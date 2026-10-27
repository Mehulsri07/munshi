package com.opuscube.munshi.events

import com.opuscube.munshi.api.SoundboxEvent
import com.opuscube.munshi.api.parsePoll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume

fun backoffMs(attempt: Int): Long = minOf(15_000L, 1000L shl minOf(attempt, 4))

// The server's live feed by long-poll (Cloudflare Quick Tunnels do not carry SSE): announcements,
// payments, cues from /control. Retries forever with backoff; `live` drives the "Jud raha hai…" dot.
class EventStream(
    private val baseUrl: String,
    private val key: String,
    private val client: OkHttpClient,
    private val backoff: (Int) -> Long = ::backoffMs,
) {
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live

    fun events(): Flow<SoundboxEvent> = channelFlow {
        var after: Long? = null
        var attempt = 0
        val base = "$baseUrl/api/events/poll?key=${URLEncoder.encode(key, "UTF-8")}"
        while (isActive) {
            val r = get(base + (after?.let { "&after=$it" } ?: ""))?.let(::parsePoll)
            if (r == null) { _live.value = false; delay(backoff(attempt++)); continue }
            attempt = 0
            _live.value = true
            if (r.resync) send(SoundboxEvent.Resync)
            r.events.forEach { send(it) }
            after = r.seq
        }
    }

    private suspend fun get(url: String): String? = suspendCancellableCoroutine { cont ->
        val call = client.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cont.resume(null) }
            override fun onResponse(call: Call, response: Response) {
                response.use { cont.resume(if (it.isSuccessful) it.body?.string() else null) }
            }
        })
    }
}
