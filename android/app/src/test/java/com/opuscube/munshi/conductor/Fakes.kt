package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.*
import com.opuscube.munshi.brain.*
import com.opuscube.munshi.voice.Line
import com.opuscube.munshi.voice.Listener
import com.opuscube.munshi.voice.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSpeaker : Speaker {
    val said = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null   // set to hold the next say() until released
    var stops = 0
    override suspend fun say(line: Line) { said += line.text; gate?.let { it.await(); gate = null } }
    override fun stop() { stops++; gate?.complete(Unit) }
}

class FakeListener(var available0: Boolean = false, val replies: ArrayDeque<String?> = ArrayDeque()) : Listener {
    override val available get() = available0
    override suspend fun listen(windowMs: Long, onPartial: (String) -> Unit): String? = replies.removeFirstOrNull()
}

val SHARMA = Next(1, "udhaar_overdue", "Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?", "…", Display("Sharma ji ka", 450, "udhaar 12 din se baaki hai. Reminder bhej doon?"))
val MITHAI = Next(2, "local_demand_spike", "Mohalle mein mithai…", null, Display("Mohalle mein mithai ki maang 39% upar", 924, "ka stock…"))
val PINKY = Next(3, "udhaar_overdue", "Pinky didi ka ₹230 udhaar…", null, Display("Pinky didi ka", 230, "udhaar…"))
val SURF = Next(7, "group_buy_open", "Surf Excel 1kg ka group order…", null, Display("Surf Excel 1kg group order", 143, "bachenge…"))

// Mirrors the server: a proposed opportunity stays current until confirmed or declined.
class FakeApi : MunshiApi {
    val nexts = ArrayDeque<Next>()
    val calls = mutableListOf<String>()
    var confirmResult: (Long) -> ActionResult = { ActionResult("Ho gaya.") }
    var converseResult: (String) -> ActionResult = { ActionResult("Samjha.") }
    var current: Next? = null
    override suspend fun brief() = Brief(listOf(BriefItem(1, "udhaar_overdue", "Sharma ji ka udhaar", 450)), 450).also { calls += "brief" }
    override suspend fun next(scope: String, interrupt: Boolean): Next {
        calls += "next:$scope"
        current = current ?: nexts.removeFirstOrNull()
        return current ?: Next()
    }
    override suspend fun confirm(id: Long): ActionResult { calls += "confirm:$id"; current = null; return confirmResult(id) }
    override suspend fun decline(id: Long): ActionResult { calls += "decline:$id"; current = null; return ActionResult("Theek hai, rehne dete hain.") }
    override suspend fun converse(text: String): ActionResult { calls += "converse:$text"; return converseResult(text) }
    var facts = Facts(
        "Ramesh Kirana", listOf(FactKhata(2, "Pinky didi", 230, 9)),
        listOf(FactTask(1, "udhaar_overdue", SHARMA.line!!, null, 450)), Week(742), listOf(Sku("surf_1kg", "Surf Excel 1kg")),
    )
    override suspend fun facts() = facts.also { calls += "facts" }
    override suspend fun propose(khataId: Long): Proposed {
        calls += "propose:$khataId"; current = PINKY
        return Proposed(PINKY.id!!, PINKY.line!!, PINKY.spoken, PINKY.display)
    }
    override suspend fun shelfReadings(readings: List<Reading>): ShelfResult {
        calls += "readings:" + readings.joinToString(",") { "${it.sku}=${it.level}" }
        return ShelfResult("Shelf dekh li: Surf Excel 1kg kam hain.")
    }
    override suspend fun ledger() = Ledger(742, emptyList()).also { calls += "ledger" }
    var profile = Profile("Ramesh Kirana", "Aliganj, Lucknow", "kirana", "Ramesh Gupta", "Ramesh ji")
    var lookupFails = false
    override suspend fun lookup(phone: String): Profile { calls += "lookup:$phone"; if (lookupFails) throw ApiError(400, "bad_phone"); return profile.copy(phone = phone) }
    override suspend fun merchant(): Profile = profile.also { calls += "merchant" }
    override suspend fun saveMerchant(p: Profile): Profile { calls += "save:${p.addressAs}"; profile = p; return p }
}

class FakeBrain(ready: Boolean = true) : Brain {
    override val state = MutableStateFlow<BrainState>(if (ready) BrainState.Ready else BrainState.Missing)
    val words = mutableMapOf<String, Line>()           // template text → warmer wording
    val prewarmed = mutableListOf<String>()
    var reply: BrainReply? = null
    var readings: List<Reading>? = null
    val asked = mutableListOf<String>()
    override suspend fun prewarm(lines: List<Line>) { prewarmed += lines.map { it.text } }
    override fun cached(line: Line): Line? = words[line.text]
    override suspend fun answer(question: String, facts: Facts): BrainReply? { asked += question; return reply }
    override suspend fun readShelf(jpeg: ByteArray, items: List<Sku>): List<Reading>? { asked += "shelf:${jpeg.size}:${items.size}"; return readings }
}
