package com.opuscube.munshi.events

import com.opuscube.munshi.api.PollResult
import com.opuscube.munshi.api.SoundboxEvent
import com.opuscube.munshi.api.parsePoll
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class EventStreamTest {
    @Test fun `backoff grows to a 15 second ceiling`() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 15000L), (0..5).map(::backoffMs))
    }

    private fun json(body: String) = MockResponse().setHeader("content-type", "application/json").setBody(body)

    @Test fun `long-polls from the current position, skips unknown types, follows the cursor`() = runBlocking {
        val server = MockWebServer().apply { start() }
        server.enqueue(json("""{"events":[],"seq":4}"""))
        server.enqueue(json("""{"events":[{"seq":5,"type":"brief"},{"seq":6,"type":"mystery"}],"seq":6}"""))
        server.enqueue(json("""{"events":[{"seq":7,"type":"paid","ref":"r1","name":"Sharma ji","amount":450}],"seq":7}"""))
        val stream = EventStream(server.url("/").toString().trimEnd('/'), "k", OkHttpClient(), backoff = { 10L })
        val got = withTimeout(5000) { stream.events().take(2).toList() }
        assertEquals(listOf(SoundboxEvent.BriefCue, SoundboxEvent.Paid("r1", "Sharma ji", 450)), got)
        assertEquals("/api/events/poll?key=k", server.takeRequest().path)
        assertEquals("/api/events/poll?key=k&after=4", server.takeRequest().path)
        assertEquals("/api/events/poll?key=k&after=6", server.takeRequest().path)
        server.shutdown()
    }

    @Test fun `a failed poll backs off, and a resync is passed on`() = runBlocking {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(json("""{"events":[],"seq":2}"""))
        server.enqueue(json("""{"resync":true,"seq":9}"""))
        val stream = EventStream(server.url("/").toString().trimEnd('/'), "k", OkHttpClient(), backoff = { 10L })
        val got = withTimeout(5000) { stream.events().take(1).toList() }
        assertEquals(listOf(SoundboxEvent.Resync), got)
        assertEquals(true, stream.live.value)
        server.shutdown()
    }

    @Test fun `parsePoll reads events, cursor and resync`() {
        assertEquals(PollResult(listOf(SoundboxEvent.Expired("r1", "Sharma ji", 450)), 3, false),
            parsePoll("""{"events":[{"seq":3,"type":"expired","ref":"r1","name":"Sharma ji","amount":450}],"seq":3}"""))
        assertEquals(PollResult(emptyList(), 9, true), parsePoll("""{"resync":true,"seq":9}"""))
        assertEquals(null, parsePoll("not json"))
    }
}
