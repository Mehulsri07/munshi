package com.opuscube.munshi.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpMunshiApiTest {
    private val server = MockWebServer()
    private lateinit var api: HttpMunshiApi

    @Before fun up() { server.start(); api = HttpMunshiApi(server.url("/").toString().trimEnd('/'), "k1", OkHttpClient()) }
    @After fun down() = server.shutdown()

    @Test fun `next posts scope and sends the demo key`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":3,"line":"x","display":{"who":"a","amount":1,"rest":"b"}}"""))
        val n = api.next(scope = "all", interrupt = true)
        val req = server.takeRequest()
        assertEquals("/api/next", req.path)
        assertEquals("k1", req.getHeader("x-demo-key"))
        assertEquals("""{"scope":"all","interrupt":true}""", req.body.readUtf8())
        assertEquals(3L, n.id)
    }

    @Test fun `server errors become ApiError with the status and message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"not_proposed"}"""))
        val e = runCatching { api.confirm(5) }.exceptionOrNull() as ApiError
        assertEquals(409, e.status)
        assertEquals("not_proposed", e.message)
        assertEquals("/api/opportunities/5/confirm", server.takeRequest().path)
    }

    @Test fun `lookup and save send the onboarding details`() = runTest {
        val reply = """{"profile":{"shopName":"Ramesh Kirana","addressAs":"Ramesh ji","phone":"+919876543210"}}"""
        server.enqueue(MockResponse().setBody(reply))
        assertEquals("+919876543210", api.lookup("98765 43210").phone)
        val l = server.takeRequest()
        assertEquals("/api/onboard/lookup", l.path)
        assertEquals("""{"phone":"98765 43210"}""", l.body.readUtf8())
        server.enqueue(MockResponse().setBody(reply.replace("Ramesh ji", "Suresh bhai")))
        assertEquals("Suresh bhai", api.saveMerchant(Profile("Ramesh Kirana", addressAs = "Suresh bhai")).addressAs)
        val s = server.takeRequest()
        assertEquals("/api/merchant", s.path)
        assertTrue(s.body.readUtf8().contains("\"addressAs\":\"Suresh bhai\""))
    }

    @Test fun `facts parse the server's shape`() = runTest {
        server.enqueue(MockResponse().setBody("""{"shop":"Ramesh Kirana","khata":[{"khataId":2,"name":"Pinky didi","amount":230,"days":9}],"tasks":[{"id":1,"type":"udhaar_overdue","line":"x","spoken":"y","amount":450}],"week":{"total":742},"inventory":[{"sku":"surf_1kg","name":"Surf Excel 1kg"}]}"""))
        val f = api.facts()
        assertEquals("/api/facts", server.takeRequest().path)
        assertEquals(FactKhata(2, "Pinky didi", 230, 9), f.khata.single())
        assertEquals("y", f.tasks.single().spoken)
        assertEquals(Sku("surf_1kg", "Surf Excel 1kg"), f.inventory.single())
    }

    @Test fun `propose posts the reminder kind and khata id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"opportunityId":3,"line":"Pinky didi ka ₹230 udhaar…","spoken":"…","display":{"who":"Pinky didi ka","amount":230,"rest":"…"}}"""))
        val p = api.propose(2)
        val req = server.takeRequest()
        assertEquals("/api/propose", req.path)
        assertEquals("""{"kind":"udhaar_reminder","khataId":2}""", req.body.readUtf8())
        assertEquals(3L, p.opportunityId)
    }

    @Test fun `shelf readings post only what the model saw`() = runTest {
        server.enqueue(MockResponse().setBody("""{"line":"Shelf dekh li: Surf Excel 1kg kam hain.","next":null}"""))
        val r = api.shelfReadings(listOf(Reading("surf_1kg", "empty")))
        val req = server.takeRequest()
        assertEquals("/api/shelf/readings", req.path)
        assertEquals("""{"readings":[{"sku":"surf_1kg","level":"empty"}]}""", req.body.readUtf8())
        assertEquals("Shelf dekh li: Surf Excel 1kg kam hain.", r.line)
    }
}
