package com.opuscube.munshi.brain

import com.opuscube.munshi.api.Facts
import com.opuscube.munshi.api.Reading
import com.opuscube.munshi.api.Sku
import com.opuscube.munshi.voice.Line
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface BrainState {
    data object Missing : BrainState                              // no model on this phone yet
    data class Downloading(val done: Long, val total: Long) : BrainState
    data object Loading : BrainState
    data object Ready : BrainState
    data class Failed(val why: String) : BrainState
    data object Unsupported : BrainState                          // too little memory: templates only
}

/** What the phone's model said back, already checked against the shop's facts. */
data class BrainReply(val say: Line, val proposeKhataId: Long?)

// The on-phone model as the Conductor sees it. Every method can come back empty; the caller then
// uses today's template path, so a slow, missing or confused model never blocks the counter.
interface Brain {
    val state: StateFlow<BrainState>
    /** Rewords these lines in the background so they are ready when asked. */
    suspend fun prewarm(lines: List<Line>)
    /** A warmer wording of a line if one is ready; never waits. */
    fun cached(line: Line): Line?
    suspend fun answer(question: String, facts: Facts): BrainReply?
    suspend fun readShelf(jpeg: ByteArray, items: List<Sku>): List<Reading>?
}

object NoBrain : Brain {
    override val state: StateFlow<BrainState> = MutableStateFlow(BrainState.Unsupported)
    override suspend fun prewarm(lines: List<Line>) {}
    override fun cached(line: Line): Line? = null
    override suspend fun answer(question: String, facts: Facts): BrainReply? = null
    override suspend fun readShelf(jpeg: ByteArray, items: List<Sku>): List<Reading>? = null
}
