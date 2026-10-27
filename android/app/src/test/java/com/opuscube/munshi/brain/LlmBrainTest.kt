package com.opuscube.munshi.brain

import com.opuscube.munshi.api.*
import com.opuscube.munshi.voice.Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeLlm(var reply: (String, String, ByteArray?) -> String = { _, _, _ -> "" }, var delayMs: Long = 0) : Llm {
    val calls = mutableListOf<Pair<String, String>>()
    var images = 0
    var background = 0
    override suspend fun generate(system: String, user: String, image: ByteArray?, background: Boolean): String {
        calls += system to user; if (image != null) images++; if (background) this.background++
        delay(delayMs); return reply(system, user, image)
    }
}

class LlmBrainTest {
    private val sharma = Line("Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?", "Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?")
    private val warm = "Ramesh ji, Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?"
    private val facts = Facts("Ramesh Kirana", listOf(FactKhata(2, "Pinky didi", 230, 9)), emptyList(), Week(742), listOf(Sku("surf_1kg", "Surf Excel 1kg")))
    private val ready = MutableStateFlow<BrainState>(BrainState.Ready)

    @Test fun `prewarm rewords once and cached returns it`() = runTest {
        val llm = FakeLlm({ _, _, _ -> warm })
        val b = LlmBrain(llm, ready)
        b.prewarm(listOf(sharma)); b.prewarm(listOf(sharma))
        assertEquals(1, llm.calls.size)
        assertEquals(Prompts.REWORD, llm.calls[0].first)
        assertEquals(sharma.text, llm.calls[0].second)
        assertEquals(Line(warm, sharma.spoken), b.cached(sharma))
        assertEquals(1, llm.background)
    }

    @Test fun `nothing runs before the model is ready`() = runTest {
        val llm = FakeLlm({ _, _, _ -> warm })
        val b = LlmBrain(llm, MutableStateFlow(BrainState.Loading))
        b.prewarm(listOf(sharma))
        assertNull(b.cached(sharma))
        assertNull(b.answer("kitna udhaar?", facts))
        assertNull(b.readShelf(ByteArray(1), facts.inventory))
        assertTrue(llm.calls.isEmpty())
    }

    @Test fun `a slow, failing or off-script model gives nothing`() = runTest {
        val slow = LlmBrain(FakeLlm({ _, _, _ -> warm }, delayMs = 61_000), ready)
        slow.prewarm(listOf(sharma)); assertNull(slow.cached(sharma))
        val broken = LlmBrain(FakeLlm({ _, _, _ -> error("gpu lost") }), ready)
        assertNull(broken.answer("kitna udhaar?", facts))
        val chatty = LlmBrain(FakeLlm({ _, _, _ -> "Sharma ji ka udhaar baaki hai" }), ready)
        chatty.prewarm(listOf(sharma)); assertNull(chatty.cached(sharma))
    }

    @Test fun `answer sends the facts and returns a checked reply`() = runTest {
        val llm = FakeLlm({ _, _, _ -> """{"say":"Pinky didi ka ₹230 baaki hai.","propose":{"kind":"udhaar_reminder","khataId":2}}""" })
        val r = LlmBrain(llm, ready).answer("Pinky ko yaad dilao", facts)
        assertEquals(BrainReply(Line("Pinky didi ka ₹230 baaki hai.", null), 2), r)
        assertTrue(llm.calls[0].first.contains("Pinky didi (khataId 2): ₹230, 9 din se"))
        assertEquals("Pinky ko yaad dilao", llm.calls[0].second)
    }

    @Test fun `shelf sends the photo and the SKU list`() = runTest {
        val llm = FakeLlm({ _, _, _ -> """[{"sku":"surf_1kg","level":"low"}]""" })
        val r = LlmBrain(llm, ready).readShelf(ByteArray(10), facts.inventory)
        assertEquals(listOf(Reading("surf_1kg", "low")), r)
        assertEquals(1, llm.images)
        assertTrue(llm.calls[0].second.contains("surf_1kg (Surf Excel 1kg)"))
    }
}
