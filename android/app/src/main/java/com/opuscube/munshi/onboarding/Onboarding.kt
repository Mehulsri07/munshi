package com.opuscube.munshi.onboarding

import com.opuscube.munshi.api.MunshiApi
import com.opuscube.munshi.api.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUi(
    val step: Step,
    val phone: String = "",
    val otp: String = "",
    val profile: Profile? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val heard: String? = null,        // what the voice check heard
    val voiceTried: Boolean = false,
)

// The merchant's first run, one step per screen; the step is saved so a closed app resumes there.
class Onboarding(private val store: ProgressStore, private val scope: CoroutineScope, private val otpDelayMs: Long = 1_200) {
    private val _ui = MutableStateFlow<OnboardingUi?>(null)
    val ui: StateFlow<OnboardingUi?> = _ui          // null until the store is read
    private val _done = MutableStateFlow<Boolean?>(null)
    val done: StateFlow<Boolean?> = _done
    private var progress = Progress()

    init {
        scope.launch {
            progress = store.progress.first()
            _done.value = progress.done
            _ui.value = OnboardingUi(progress.step)
        }
    }

    fun next() { val s = _ui.value?.step ?: return; Step.entries.getOrNull(s.ordinal + 1)?.let { go(it) } ?: finish() }
    fun back() { val s = _ui.value?.step ?: return; Step.entries.getOrNull(s.ordinal - 1)?.let { go(it) } }
    fun paired() { if (_ui.value?.step == Step.Connect) go(Step.Number) }
    fun setPhone(s: String) = _ui.update { it?.copy(phone = s.filter { c -> c.isDigit() || c == ' ' || c == '+' }.take(16), error = null) }
    fun setProfile(p: Profile) = _ui.update { it?.copy(profile = p, error = null) }

    // Simulated Paytm for Business lookup: any valid number finds the demo shop, then the OTP "arrives".
    fun lookup(api: MunshiApi) {
        val phone = _ui.value?.phone.orEmpty()
        val digits = phone.filter(Char::isDigit).let { if (it.length == 12 && it.startsWith("91")) it.drop(2) else it }
        if (digits.length != 10) { _ui.update { it?.copy(error = "10 ank ka number daaliye.") }; return }
        busy {
            val p = api.lookup(phone)
            _ui.update { it?.copy(profile = p, otp = "") }
            go(Step.Otp)
            scope.launch { delay(otpDelayMs); _ui.update { it?.copy(otp = "482917") } }   // demo: as if read from the SMS
        }
    }

    fun save(api: MunshiApi) {
        val p = _ui.value?.profile ?: return
        if (p.shopName.isBlank() || p.addressAs.isBlank()) { _ui.update { it?.copy(error = "Dukaan ka naam aur aapko kya bulaayein, dono chahiye.") }; return }
        busy {
            val saved = api.saveMerchant(p.copy(shopName = p.shopName.trim(), addressAs = p.addressAs.trim()))
            _ui.update { it?.copy(profile = saved) }
            if (progress.editOnly) go(Step.Voice, done = true, editOnly = false) else go(Step.Permissions)
        }
    }

    fun voice(check: suspend () -> String?) {
        _ui.update { it?.copy(busy = true, heard = null) }
        scope.launch { val h = check(); _ui.update { it?.copy(busy = false, heard = h, voiceTried = true) } }
    }

    fun finish() = go(Step.Voice, done = true, editOnly = false)
    fun redo() = go(Step.Welcome, done = false, editOnly = false)
    fun editShop() = go(Step.Shop, done = false, editOnly = true)
    fun reconnect() = go(Step.Connect, done = false, editOnly = false)   // the pairing was cleared: scan again, then carry on

    private fun go(step: Step, done: Boolean = false, editOnly: Boolean = progress.editOnly) {
        progress = Progress(step, done, editOnly)
        _ui.update { (it ?: OnboardingUi(step)).copy(step = step, error = null, busy = false) }
        _done.value = done
        scope.launch { store.save(progress) }
    }

    private fun busy(block: suspend () -> Unit) {
        _ui.update { it?.copy(busy = true, error = null) }
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.update { it?.copy(busy = false, error = "Server se baat nahi hui. Dobara koshish kijiye.") } }
        }
    }
}
