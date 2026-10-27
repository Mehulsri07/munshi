package com.opuscube.munshi.onboarding

import com.opuscube.munshi.conductor.FakeApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryProgress(start: Progress = Progress()) : ProgressStore {
    val saved = MutableStateFlow(start)
    override val progress: Flow<Progress> = saved
    override suspend fun save(p: Progress) { saved.value = p }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {
    private val api = FakeApi()

    @Test fun `steps go in order and are saved, so a restart resumes`() = runTest {
        val store = MemoryProgress(); val o = Onboarding(store, backgroundScope); runCurrent()
        assertEquals(Step.Welcome, o.ui.value!!.step)
        assertEquals(false, o.done.value)
        o.next(); runCurrent()
        assertEquals(Step.Connect, store.saved.value.step)
        val again = Onboarding(store, backgroundScope); runCurrent()
        assertEquals(Step.Connect, again.ui.value!!.step)
    }

    @Test fun `pairing moves Connect on to the number`() = runTest {
        val o = Onboarding(MemoryProgress(Progress(Step.Connect)), backgroundScope); runCurrent()
        o.paired(); runCurrent()
        assertEquals(Step.Number, o.ui.value!!.step)
    }

    @Test fun `a bad number is said plainly, a good one finds the shop and the OTP fills itself`() = runTest {
        val o = Onboarding(MemoryProgress(Progress(Step.Number)), backgroundScope); runCurrent()
        o.setPhone("123"); o.lookup(api); runCurrent()
        assertEquals("10 ank ka number daaliye.", o.ui.value!!.error)
        o.setPhone("98765 43210"); o.lookup(api); runCurrent()
        assertEquals(Step.Otp, o.ui.value!!.step)
        assertEquals("Ramesh Kirana", o.ui.value!!.profile!!.shopName)
        assertEquals("", o.ui.value!!.otp)
        advanceTimeBy(1_300); runCurrent()
        assertEquals(6, o.ui.value!!.otp.length)
    }

    @Test fun `a server error keeps the step and says so`() = runTest {
        api.lookupFails = true
        val o = Onboarding(MemoryProgress(Progress(Step.Number)), backgroundScope); runCurrent()
        o.setPhone("9876543210"); o.lookup(api); runCurrent()
        assertEquals(Step.Number, o.ui.value!!.step)
        assertEquals("Server se baat nahi hui. Dobara koshish kijiye.", o.ui.value!!.error)
        assertFalse(o.ui.value!!.busy)
    }

    @Test fun `confirming the shop saves it and moves on, an empty name is refused`() = runTest {
        val o = Onboarding(MemoryProgress(Progress(Step.Shop)), backgroundScope); runCurrent()
        o.setProfile(api.profile.copy(addressAs = " ")); o.save(api); runCurrent()
        assertEquals(Step.Shop, o.ui.value!!.step)
        assertNotNull(o.ui.value!!.error)
        o.setProfile(api.profile.copy(addressAs = "Suresh bhai")); o.save(api); runCurrent()
        assertTrue("save:Suresh bhai" in api.calls)
        assertEquals(Step.Permissions, o.ui.value!!.step)
    }

    @Test fun `the voice check records what was heard, finishing marks onboarding done`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.voice { "haan" }; runCurrent()
        assertEquals("haan", o.ui.value!!.heard)
        assertTrue(o.ui.value!!.voiceTried)
        o.finish(); runCurrent()
        assertTrue(store.saved.value.done)
        assertEquals(true, o.done.value)
    }

    @Test fun `editing the shop from settings opens only that step`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice, done = true)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.editShop(); runCurrent()
        assertEquals(Progress(Step.Shop, done = false, editOnly = true), store.saved.value)
        o.setProfile(api.profile); o.save(api); runCurrent()
        assertTrue(store.saved.value.done)
        assertFalse(store.saved.value.editOnly)
    }

    @Test fun `a cleared pairing reopens Connect, and pairing again carries on to the number`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice, done = true)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.reconnect(); runCurrent()
        assertEquals(Progress(Step.Connect), store.saved.value)
        o.paired(); runCurrent()
        assertEquals(Step.Number, o.ui.value!!.step)
    }

    @Test fun `onboarding again starts at Welcome`() = runTest {
        val store = MemoryProgress(Progress(Step.Voice, done = true)); val o = Onboarding(store, backgroundScope); runCurrent()
        o.redo(); runCurrent()
        assertEquals(Progress(Step.Welcome), store.saved.value)
        assertEquals(false, o.done.value)
    }
}
