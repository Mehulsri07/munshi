package com.opuscube.munshi.brain

import com.opuscube.munshi.api.*
import com.opuscube.munshi.voice.Line
import org.junit.Assert.*
import org.junit.Test

class GuardsTest {
    private val sharma = Line("Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?", "Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?")
    private val facts = Facts(
        "Ramesh Kirana",
        listOf(FactKhata(1, "Sharma ji", 450, 12), FactKhata(2, "Pinky didi", 230, 9)),
        listOf(FactTask(5, "local_demand_spike", "…", null, 1_240)), Week(742), listOf(Sku("surf_1kg", "Surf Excel 1kg"), Sku("parle_g", "Parle-G")),
    )

    @Test fun `numbers ignore Indian grouping commas`() {
        assertEquals(setOf("50000", "400"), numbers("₹50,000 aaye, roz ₹400."))
    }

    @Test fun `a good reword keeps numbers and the question, Roman first then Devanagari`() {
        val raw = "Ramesh ji, Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?\nरमेश जी, Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?"
        assertEquals(Line("Ramesh ji, Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?", "रमेश जी, Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?"), parseReword(raw, sharma))
    }

    @Test fun `a reword that drops a number, the question or puts Devanagari first is refused`() {
        assertNull(parseReword("Sharma ji ka udhaar baaki hai. Bhej doon?", sharma))
        assertNull(parseReword("Sharma ji ka ₹450 udhaar 12 din se baaki hai.", sharma))
        assertNull(parseReword("शर्मा जी का ₹450 उधार 12 दिन? ", sharma))
        assertNull(parseReword("", sharma))
    }

    @Test fun `a reword with a bad Devanagari line keeps the template's Devanagari`() {
        val r = parseReword("Ramesh ji, Sharma ji ka ₹450 udhaar 12 din se baaki hai. Bhej doon?\nशर्मा जी का उधार बाकी है।", sharma)
        assertEquals(sharma.spoken, r!!.spoken)
    }

    @Test fun `an answer uses only numbers and khata ids from the facts`() {
        val ok = parseAnswer("""Sure: {"say":"Pinky didi ka ₹230 9 din se baaki hai.","spoken":"Pinky didi का ₹230 9 दिन से बाकी है।","propose":{"kind":"udhaar_reminder","khataId":2}}""", facts)
        assertEquals(BrainReply(Line("Pinky didi ka ₹230 9 din se baaki hai.", "Pinky didi का ₹230 9 दिन से बाकी है।"), 2), ok)
        assertEquals(BrainReply(Line("Is hafte ₹742 aaye.", null), null), parseAnswer("""{"say":"Is hafte ₹742 aaye.","propose":null}""", facts))
        val total = facts.khata.sumOf { it.amount }
        assertEquals(BrainReply(Line("Kul ₹$total udhaar baaki hai.", null), null), parseAnswer("""{"say":"Kul ₹$total udhaar baaki hai.","propose":null}""", facts))
        assertNull(parseAnswer("""{"say":"Pinky didi ka ₹999 baaki hai."}""", facts))                  // invented amount
        assertNull(parseAnswer("""{"say":"Pinky didi ka khataId 2 use karo.","propose":null}""", facts))  // prompt words leaked
        assertNull(parseAnswer("""{"say":"Bhej doon?","propose":{"kind":"udhaar_reminder","khataId":77}}""", facts))  // unknown khata
        assertNull(parseAnswer("""{"say":"पिंकी दीदी"}""", facts))                                          // say must be Roman
        assertNull(parseAnswer("no json here", facts))
    }

    @Test fun `shelf readings keep known SKUs and levels only`() {
        assertEquals(listOf(Reading("surf_1kg", "empty")), parseShelf("""Dekha: [{"sku":"surf_1kg","level":"empty"},{"sku":"bogus","level":"low"},{"sku":"parle_g","level":"meh"}]""", facts.inventory))
        assertNull(parseShelf("""[{"sku":"bogus","level":"low"}]""", facts.inventory))
        assertEquals(listOf(Reading("surf_1kg", "low", 2)), parseShelf("""```json
[{"sku":"surf_1kg","count":2}]
```""", facts.inventory))
        assertEquals(listOf("empty", "low", "low", "ok"), listOf(0, 1, 3, 4).map(::levelFor))
        assertNull(parseShelf("""[{"sku": "surf_1kg", "level": """, facts.inventory))
    }
}
