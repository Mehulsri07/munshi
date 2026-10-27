package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.*
import com.opuscube.munshi.brain.Brain
import com.opuscube.munshi.brain.BrainState
import com.opuscube.munshi.brain.NoBrain
import com.opuscube.munshi.voice.IntentMatcher
import com.opuscube.munshi.voice.isEcho
import com.opuscube.munshi.voice.Line
import com.opuscube.munshi.voice.Listener
import com.opuscube.munshi.voice.Speaker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private val OOPS = Line("Kuch gadbad hui. Dobara koshish kijiye.", "कुछ गड़बड़ हुई। दोबारा कोशिश कीजिए।")
private val GONE = Line("Yeh kaam ab baaki nahi.", "यह काम अब बाकी नहीं।")
private val NO_EYES = Line("Munshi ka dimaag abhi taiyaar nahi. Thodi der mein photo dobara kheenchiye.", "मुंशी का दिमाग अभी तैयार नहीं। थोड़ी देर में फ़ोटो दोबारा खींचिए।")
private val THINKING = Line("Ek second, dekhta hoon.", "एक सेकंड, देखता हूँ।")
private val NO_ANSWER = Line("Maaf kijiye, abhi jawab nahi bana. Thodi der mein dobara poochiye.", "माफ़ कीजिए, अभी जवाब नहीं बना। थोड़ी देर में दोबारा पूछिए।")
private val UNREADABLE = Line("Photo saaf nahi dikhi. Shelf ki photo dobara kheenchiye.", "फ़ोटो साफ़ नहीं दिखी। शेल्फ़ की फ़ोटो दोबारा खींचिए।")
// The name from onboarding; the seeded shop's owner until the merchant confirms their own.
private fun greet(p: Profile?) = (p?.addressAs ?: "Ramesh ji").let { Line("Suprabhat $it! Aaj ke kaam sunaata hoon.", "सुप्रभात $it! आज के काम सुनाता हूँ।") }
private val BYE_DONE = Line("Bas itna hi. Aapka din shubh ho!", "बस इतना ही। आपका दिन शुभ हो!")
private val BYE_NONE = Line("Aaj koi naya kaam nahi. Aapka din shubh ho!", "आज कोई नया काम नहीं। आपका दिन शुभ हो!")

private fun mindNote(s: BrainState): String? = when (s) {
    is BrainState.Downloading -> String.format(Locale.US, "Munshi ka dimaag aa raha hai · %.1f / %.1f GB", s.done / 1e9, s.total / 1e9)
    BrainState.Loading -> "Munshi ka dimaag taiyaar ho raha hai…"
    else -> null
}

// Port of web/Soundbox.tsx: one proposal at a time, answers accepted while speaking,
// free speech goes to the agent, a shelf reading interrupts, a pay QR stays until paid.
class Conductor(
    private val api: MunshiApi,
    private val speaker: Speaker,
    private val listener: Listener,
    private val scope: CoroutineScope,
    private val listenWindowMs: Long,
    private val brain: Brain = NoBrain,
    private val trace: (String) -> Unit = {},   // step-by-step flow for logcat (MunshiFlow); silent in tests
) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var pending: CompletableDeferred<Answer>? = null
    private val running = AtomicBoolean(false)
    private val listening = AtomicBoolean(false)   // one recogniser at a time: two fight and both fail
    private val warming = AtomicBoolean(false)     // one warm-up at a time: a second only queues for the model
    private var bannerJob: Job? = null
    private var sessionJob: Job? = null

    init {
        // Status for the dock, and a warm-up of today's lines the moment the model is ready.
        scope.launch {
            brain.state.collect { s ->
                _state.update { it.copy(mind = mindNote(s)) }
                if (s == BrainState.Ready) scope.launch { warmBrain() }
            }
        }
    }

    fun start() { scope.launch { refreshProfile(); refreshBrief(); refreshLedger(); refreshKhata() } }

    fun openShop() { session(greet = true, scope = "brief", max = 3) }

    // The counter screen opens again after onboarding or a shop edit: pick up the saved names.
    fun reloadProfile() { scope.launch { refreshProfile() } }

    // Band karo: stop whatever runs and go back to the morning brief.
    fun closeShop() {
        scope.launch {
            sessionJob?.cancelAndJoin()
            pending = null
            speaker.stop()
            _state.update { it.copy(shopOpen = false, ask = null, mode = Mode.Brief) }
            refreshBrief()
        }
    }

    // Yaad dilao on a row of the udhaar sheet: the same path as asking for it by voice.
    fun remind(khataId: Long) {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                val p = api.propose(khataId)
                resolve(p.opportunityId, p.display, worded(Line(p.line, p.spoken)), speak = true, type = "udhaar_overdue")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { say(OOPS) }
            finally { running.set(false); _state.update { it.copy(mode = Mode.Idle) } }
        }
    }

    // "Naya link bhejein" on an expired card: the server has re-offered the reminder; ask it.
    fun relink(ref: String) {
        _state.update { s -> s.copy(payQrs = s.payQrs.filter { it.ref != ref }) }
        session(greet = false, scope = "all", max = 1)
    }

    // Onboarding's last step: Munshi speaks, then listens for a haan. The mic also catches the tail of
    // Munshi's own voice ("मैं"), so anything else gets one more listen; null when no haan was heard.
    suspend fun voiceCheck(name: String): String? {
        speaker.say(Line("Namaste $name, main Munshi hoon. Sun pa rahe hain? Haan boliye.", "नमस्ते $name, मैं मुंशी हूँ। सुन पा रहे हैं? हाँ बोलिए।"))
        repeat(2) {
            if (!listener.available) return null
            val text = listenOnce() ?: return null
            if (IntentMatcher.match(text) == Answer.Yes) return text
            trace("voice check: not haan, ignored: $text")
        }
        return null
    }

    fun answer(a: Answer) {
        if (pending?.isActive == true && a != Answer.Skip) speaker.stop()
        pending?.complete(a)
    }

    // Boliye. Mid-question it only cuts the voice: the question's own listen starts right after. While Munshi
    // is already listening or busy, the tap is ignored instead of opening a second recogniser.
    fun mic() {
        if (!listener.available) return
        when {
            listening.get() -> { trace("mic ignored: already listening"); return }
            pending?.isActive == true -> { trace("mic: cut the voice, the question listens next"); speaker.stop(); return }
            running.get() -> { trace("mic ignored: busy"); return }
        }
        scope.launch { listenOnce()?.let(::hear) }
    }

    /** What the merchant said: an answer to the open question, or free speech. Debug builds feed it from adb. */
    fun hear(text: String) {
        val matched = IntentMatcher.match(text)
        trace("heard \"$text\" → ${matched ?: "free speech"}, pending=${pending?.isActive == true}, busy=${running.get()}")
        if (pending?.isActive == true) { answer(matched ?: Answer.Text(text)); return }
        if (!running.compareAndSet(false, true)) return
        scope.launch { try { converseAndFollow(text) } finally { running.set(false) } }
    }

    // The phone's model reads the photo; only its readings go to the server, which checks them.
    fun shelf(jpeg: ByteArray) {
        scope.launch {
            _state.update { it.copy(mode = Mode.Working) }
            try {
                if (brain.state.value != BrainState.Ready) { say(NO_EYES); return@launch }
                val inventory = api.facts().inventory
                val readings = brain.readShelf(jpeg, inventory)
                if (readings == null) { say(UNREADABLE); return@launch }
                _state.update { s -> s.copy(shelf = readings.map { r -> ShelfLine(r.sku, inventory.firstOrNull { it.sku == r.sku }?.name ?: r.sku, r.count, r.level) }) }
                handleShelf(api.shelfReadings(readings))
            } catch (e: ApiError) { say(if (e.status == 422) UNREADABLE else OOPS) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { say(OOPS) }
        }
    }

    fun onEvent(e: SoundboxEvent) {
        when (e) {
            is SoundboxEvent.Announce -> { showBanner(e.text); scope.launch { speaker.say(Line(e.text, e.spoken)) } }
            // Paid: the card takes the stamp, then leaves the stack.
            is SoundboxEvent.Paid -> if (_state.value.payQrs.any { it.ref == e.ref }) {
                setQr(e.ref, QrState.Paid)
                scope.launch { delay(4_000); _state.update { s -> s.copy(payQrs = s.payQrs.filter { it.ref != e.ref }) } }
            }
            SoundboxEvent.LedgerChanged -> scope.launch { refreshLedger(); refreshKhata() }
            SoundboxEvent.BriefCue -> openShop()
            SoundboxEvent.Nudge -> session(greet = false, scope = "all", max = 1)
            // Like the web page reloading: stop whatever is running, nothing stale left pending.
            SoundboxEvent.Reset -> scope.launch {
                sessionJob?.cancelAndJoin()
                pending = null
                speaker.stop()
                _state.value = UiState(live = _state.value.live)
                start()
            }
            is SoundboxEvent.Shelf -> scope.launch { handleShelf(ShelfResult(e.line, e.spoken, e.next)) }
            // The link ran out: the card says so and offers a new one. The server announces it and re-offers the reminder.
            is SoundboxEvent.Expired -> setQr(e.ref, QrState.Expired)
            SoundboxEvent.Resync -> scope.launch { refreshBrief(); refreshLedger() }
        }
    }

    fun onLive(live: Boolean) {
        val was = _state.value.live
        _state.update { it.copy(live = live) }
        if (live && !was) scope.launch { refreshLedger() }
    }

    // ---------------------------------------------------------------------------------

    private fun session(greet: Boolean, scope: String, max: Int) {
        if (!running.compareAndSet(false, true)) { trace("session refused: busy"); return }
        trace("session start ($scope, max $max)")
        sessionJob = this.scope.launch { runSession(greet, scope, max) }
    }

    private suspend fun runSession(greet: Boolean, scope: String, max: Int) {
        _state.update { it.copy(shopOpen = true, mode = Mode.Idle) }
        try {
            this.scope.launch { warmBrain() }
            if (greet) say(greet(_state.value.profile))
            var handled = 0
            while (handled < max) {
                val n = api.next(scope)
                val id = n.id ?: break
                if (resolve(id, n.display, worded(Line(n.line.orEmpty(), n.spoken)), speak = true, type = n.type) == Outcome.Done) handled++
            }
            if (greet) say(if (handled > 0) BYE_DONE else BYE_NONE)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { say(OOPS) }
        finally { running.set(false); _state.update { it.copy(mode = Mode.Idle, ask = null) }; trace("session end"); this.scope.launch { refreshKhata() } }
    }

    private enum class Outcome { Done, Skipped }

    private suspend fun resolve(id: Long, display: Display?, line: Line, speak: Boolean, type: String? = null): Outcome = coroutineScope {
        val answered = CompletableDeferred<Answer>().also { pending = it }
        trace("ask #$id: ${line.text}")
        _state.update { it.copy(ask = Ask(id, display, line, type), heard = null, mode = Mode.Speaking) }
        val voice = launch {
            if (speak) speaker.say(line)
            // The listen after a question takes only haan or nahi: the mic also hears the tail of Munshi's own
            // voice and the customers across the counter. Anything else is ignored and it listens once more;
            // a question of the merchant's own goes through Boliye.
            var heard: Answer? = null
            for (attempt in 1..2) {
                if (!listener.available || !answered.isActive) break
                val text = listenOnce() ?: break
                val matched = IntentMatcher.match(text)
                when {
                    isEcho(text, line) -> trace("echo ignored: $text")
                    matched == null -> trace("not haan or nahi, ignored: $text")
                    else -> { heard = matched; break }
                }
            }
            heard?.let { answered.complete(it) }
            if (answered.isActive) _state.update { it.copy(mode = Mode.Idle) }
        }
        val a = answered.await()
        trace("answer #$id: $a")
        voice.cancel()
        pending = null
        _state.update { it.copy(ask = null, shelf = null) }
        if (a == Answer.Skip) return@coroutineScope Outcome.Skipped
        _state.update { it.copy(mode = Mode.Working) }
        val result = try {
            when (a) {
                Answer.Yes -> api.confirm(id)
                Answer.No -> api.decline(id)
                is Answer.Text -> asking(a.text) { freeSpeech(a.text) }
                Answer.Skip -> error("unreachable")
            }
        } catch (e: ApiError) {
            if (e.status == 409) { say(GONE); return@coroutineScope Outcome.Done }
            say(OOPS); return@coroutineScope Outcome.Done
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { say(OOPS); return@coroutineScope Outcome.Done }
        show(result)
        say(Line(result.line, result.spoken))
        if (a is Answer.Text && result.opportunityId != null && result.opportunityId != id && result.payUrl == null) {
            val n = api.next()
            val nid = n.id
            if (nid != null && nid == result.opportunityId) resolve(nid, n.display, Line(n.line.orEmpty(), n.spoken), speak = false, type = n.type)
        }
        // A question in the middle of a question, or a nudge toward that same task, leaves it open: the session
        // asks it again, and it does not count toward the session's tasks.
        if (a is Answer.Text && (result.opportunityId == null || result.opportunityId == id)) Outcome.Skipped else Outcome.Done
    }

    private suspend fun converseAndFollow(text: String) {
        _state.update { it.copy(mode = Mode.Working) }
        val r = try { asking(text) { freeSpeech(text) } } catch (e: CancellationException) { throw e } catch (e: Exception) { ActionResult(OOPS.text, OOPS.spoken) }
        show(r)
        say(Line(r.line, r.spoken))
        if (r.opportunityId != null && r.payUrl == null) {
            val n = api.next()
            val nid = n.id
            if (nid != null && nid == r.opportunityId) resolve(nid, n.display, Line(n.line.orEmpty(), n.spoken), speak = false, type = n.type)
        }
    }

    // A shelf reading skips the open question; a running session then asks the restock offer (the server has
    // already proposed it). Otherwise the skipped question (from Boliye) ends at once, and the offer is asked here.
    private suspend fun handleShelf(r: ShelfResult) {
        if (pending?.isActive == true) answer(Answer.Skip)
        if (sessionJob?.isActive == true) { say(Line(r.line, r.spoken)); return }
        val free = withTimeoutOrNull(30_000) { while (!running.compareAndSet(false, true)) delay(50); true } ?: false
        if (!free) { say(Line(r.line, r.spoken)); return }
        try {
            say(Line(r.line, r.spoken))
            r.next?.let { resolve(it.id, it.display, worded(Line(it.line, it.spoken)), speak = true, type = "restock") }
        } finally { running.set(false); _state.update { it.copy(mode = Mode.Idle) } }
    }

    private fun worded(line: Line): Line = brain.cached(line) ?: line

    private suspend fun warmBrain() {
        if (brain.state.value != BrainState.Ready || !warming.compareAndSet(false, true)) return
        try {
            val facts = try { api.facts() } catch (e: CancellationException) { throw e } catch (e: Exception) { return }
            brain.prewarm(facts.tasks.map { Line(it.line, it.spoken) })
        } finally { warming.set(false) }
    }

    // Anything but haan/nahi. The phone's model answers from the shop's facts and may suggest a reminder,
    // which the server turns into a proposal; Munshi says the answer, then asks the proposal (the caller
    // speaks the returned line and follows its opportunityId). Without a usable answer: the server's line.
    private suspend fun freeSpeech(text: String): ActionResult {
        if (brain.state.value == BrainState.Ready) {
            val filler = scope.launch { speaker.say(THINKING) }   // the model takes a while on a mid-range phone
            val facts = try { api.facts() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            val reply = facts?.let { brain.answer(text, it) }
            filler.join()
            if (reply == null) return ActionResult(NO_ANSWER.text, NO_ANSWER.spoken)
            val k = reply.proposeKhataId ?: return ActionResult(reply.say.text, reply.say.spoken)
            val p = try { api.propose(k) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                ?: return ActionResult(reply.say.text, reply.say.spoken)
            say(reply.say)
            return ActionResult(p.line, p.spoken, opportunityId = p.opportunityId)
        }
        return api.converse(text)
    }

    private suspend fun listenOnce(): String? {
        if (!listening.compareAndSet(false, true)) return null
        try { return listenNow() } finally { listening.set(false) }
    }

    private suspend fun listenNow(): String? {
        _state.update { it.copy(mode = Mode.Listening, heard = null) }
        var text: String? = null
        try {
            text = listener.listen(listenWindowMs) { partial -> _state.update { it.copy(heard = partial.trim().ifEmpty { null }) } }
                ?.trim()?.ifEmpty { null }
            return text
        } finally {   // also when the listen is cut short: no stale partial left on the dock
            _state.update { it.copy(mode = if (it.mode == Mode.Listening) Mode.Idle else it.mode, heard = text) }
        }
    }

    // The merchant's question stays on the page (C4) while the answer is made.
    private suspend fun <T> asking(text: String, block: suspend () -> T): T {
        _state.update { it.copy(asked = text) }
        try { return block() } finally { _state.update { it.copy(asked = null) } }
    }

    private fun show(r: ActionResult) {
        val url = r.payUrl ?: return
        val ref = url.substringAfterLast('/')
        _state.update { s -> s.copy(payQrs = listOf(PayQr(url, ref, r.payFor?.name, r.payFor?.amount)) + s.payQrs.filter { it.ref != ref }) }
    }

    private fun setQr(ref: String, to: QrState) = _state.update { s -> s.copy(payQrs = s.payQrs.map { if (it.ref == ref) it.copy(state = to) else it }) }

    private suspend fun say(line: Line) {
        _state.update { it.copy(caption = line, mode = Mode.Speaking) }
        speaker.say(line)
        _state.update { it.copy(mode = Mode.Idle) }
    }

    private fun showBanner(text: String) {
        bannerJob?.cancel()
        _state.update { it.copy(banner = text) }
        bannerJob = scope.launch { delay(6500); _state.update { it.copy(banner = null) } }
    }

    private suspend fun refreshBrief() = runCatching { api.brief() }.onSuccess { b -> _state.update { it.copy(brief = b) } }
    private suspend fun refreshLedger() = runCatching { api.ledger() }.onSuccess { l -> _state.update { it.copy(ledger = l) } }
    private suspend fun refreshKhata() = runCatching { api.facts().khata }.onSuccess { k -> _state.update { it.copy(khata = k) } }
    private suspend fun refreshProfile() = runCatching { api.merchant() }.onSuccess { p -> _state.update { it.copy(profile = p) } }
}
