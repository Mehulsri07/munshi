package com.opuscube.munshi.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.*
import org.junit.Test

class ContractTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun `next has display and spoken`() {
        val n = MunshiJson.decodeFromString<Next>(fixture("next.json"))
        assertNotNull(n.id)
        assertEquals("Sharma ji ka", n.display!!.who)
        assertEquals(450, n.display!!.amount)
        assertTrue(n.spoken!!.contains("उधार"))
    }

    @Test fun `profile replies carry the names Munshi uses`() {
        val p = MunshiJson.decodeFromString<ProfileReply>(fixture("profile.json")).profile
        assertEquals("Ramesh ji", p.addressAs)
        assertEquals("+919876543210", p.phone)
    }

    @Test fun `brief lists three with a total`() {
        val b = MunshiJson.decodeFromString<Brief>(fixture("brief.json"))
        assertEquals(3, b.items.size)
        assertEquals(b.items.sumOf { it.amount }, b.total)
    }

    @Test fun `confirm returns a pay link for udhaar`() {
        val r = MunshiJson.decodeFromString<ActionResult>(fixture("confirm.json"))
        assertTrue(r.payUrl!!.contains("/pay/"))
        assertEquals(450, r.payFor!!.amount)
    }

    @Test fun `shelf next carries a display`() {
        val s = MunshiJson.decodeFromString<ShelfResult>(fixture("shelf.json"))
        assertEquals("Surf Excel 1kg group order", s.next!!.display!!.who)
    }

    @Test fun `ledger parses with or without a loan`() {
        val l = MunshiJson.decodeFromString<Ledger>(fixture("ledger.json"))
        assertTrue(l.total >= 0)
    }

    @Test fun `every known event parses and unknown ones are ignored`() {
        val arr = MunshiJson.parseToJsonElement(fixture("events.json")) as JsonArray
        val parsed = arr.map { parseEvent(it.toString()) }
        assertEquals(SoundboxEvent.Announce("Sharma ji ne ₹450 chuka diye", "Sharma ji ने ₹450 चुका दिए"), parsed[0])
        assertEquals(SoundboxEvent.Paid("ab12cd", "Sharma ji", 450), parsed[1])
        assertEquals(listOf(SoundboxEvent.BriefCue, SoundboxEvent.Nudge, SoundboxEvent.LedgerChanged, SoundboxEvent.Reset), parsed.subList(2, 6))
        assertEquals(143, (parsed[6] as SoundboxEvent.Shelf).next!!.display!!.amount)
        assertNull(parsed[7])
    }
}
