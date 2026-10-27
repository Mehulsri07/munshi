package com.opuscube.munshi.brain

import com.opuscube.munshi.api.Facts
import com.opuscube.munshi.api.Reading
import com.opuscube.munshi.api.Sku
import com.opuscube.munshi.voice.Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** One-shot generation on the phone. No history: every call is a fresh conversation. A background call
 *  (warming up wording) is stopped as soon as the merchant asks something. */
interface Llm {
    suspend fun generate(system: String, user: String, image: ByteArray? = null, background: Boolean = false): String
}

class LlmBrain(
    private val llm: Llm,
    override val state: StateFlow<BrainState>,
    // Measured on a Galaxy A34 (E4B, GPU): ~45 tok/s in, ~9 tok/s out. Rewording runs in the background and
    // its limit includes waiting behind the merchant's question (up to answerMs); the merchant hears
    // "Ek second" while an answer is made.
    private val rewordMs: Long = 60_000,
    private val answerMs: Long = 25_000,
    private val shelfMs: Long = 40_000,
) : Brain {
    private val worded = ConcurrentHashMap<String, Line>()   // template text → warmer wording
    private val ready get() = state.value == BrainState.Ready

    override fun cached(line: Line): Line? = worded[line.text]

    override suspend fun prewarm(lines: List<Line>) {
        for (l in lines) {
            if (!ready || worded.containsKey(l.text)) continue
            val raw = ask(rewordMs) { llm.generate(Prompts.REWORD, l.text, background = true) } ?: continue
            parseReword(raw, l)?.let { worded[l.text] = it }
        }
    }

    override suspend fun answer(question: String, facts: Facts): BrainReply? {
        if (!ready) return null
        val raw = ask(answerMs) { llm.generate(Prompts.answer(facts), question) } ?: return null
        return parseAnswer(raw, facts)
    }

    override suspend fun readShelf(jpeg: ByteArray, items: List<Sku>): List<Reading>? {
        if (!ready) return null
        val raw = ask(shelfMs) { llm.generate(Prompts.SHELF, Prompts.shelf(items), jpeg) } ?: return null
        return parseShelf(raw, items)
    }

    // A slow or failing model is a missing answer, never a crash: the templates take over.
    private suspend fun ask(ms: Long, block: suspend () -> String): String? = try {
        withTimeoutOrNull(ms) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
