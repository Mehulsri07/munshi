package com.opuscube.munshi

import android.app.ActivityManager
import android.app.Application
import com.opuscube.munshi.api.HttpMunshiApi
import com.opuscube.munshi.api.SoundboxEvent
import com.opuscube.munshi.brain.Brain
import com.opuscube.munshi.brain.LlmBrain
import com.opuscube.munshi.brain.ModelHost
import com.opuscube.munshi.brain.Models
import com.opuscube.munshi.conductor.Conductor
import com.opuscube.munshi.conductor.Mode
import com.opuscube.munshi.events.EventStream
import com.opuscube.munshi.onboarding.DataStoreProgress
import com.opuscube.munshi.onboarding.Onboarding
import com.opuscube.munshi.pairing.Config
import com.opuscube.munshi.pairing.ConfigStore
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.voice.AndroidListener
import com.opuscube.munshi.voice.AndroidSpeaker
import com.opuscube.munshi.voice.Chime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// Everything one paired server needs, wired by hand. Re-pairing builds a new Graph.
class Graph(val config: Config, app: MunshiApp, scope: CoroutineScope) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val sse = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    val events = EventStream(config.url, config.key, sse)
    val api = HttpMunshiApi(config.url, config.key, http)
    val conductor = Conductor(api, AndroidSpeaker(app), AndroidListener(app), scope, Tokens.MotionListenWindowMs, brain = app.brain,
        trace = { android.util.Log.i("MunshiFlow", it) })
    private val job = scope.launch {
        launch { events.live.collect(conductor::onLive) }
        // Soft tick each time Munshi starts listening (spec §5).
        launch { conductor.state.map { it.mode == Mode.Listening }.distinctUntilChanged().filter { it }.collect { Chime.tick() } }
        events.events().collect { e -> if (e is SoundboxEvent.Paid) Chime.payment(); conductor.onEvent(e) }
    }
    init { conductor.start() }
    fun close() = job.cancel()
}

class MunshiApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var configs: ConfigStore
    lateinit var onboarding: Onboarding
    val ramGb: Int by lazy { Math.round(totalRam() / 1e9).toInt() }
    // What onboarding's Brain step shows: the model this phone gets, by memory.
    val modelName: String by lazy {
        val name = Models.forRam(totalRam())?.name.orEmpty()
        when { "E4B" in name -> "Gemma 4 · E4B"; "E2B" in name -> "Gemma 4 · E2B"; else -> "Is phone par AI nahi" }
    }
    // One model for the app, shared by every pairing; it starts loading (or downloading on Wi-Fi) at launch.
    private val mind by lazy { ModelHost(this, scope, OkHttpClient()).also { it.start() } }
    val brain: Brain by lazy { LlmBrain(mind.llm, mind.state) }
    // null until the saved pairing is read: a cold start is not a cleared pairing.
    val paired: StateFlow<Boolean?> by lazy { configs.config.map { it != null }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null) }
    val graph: StateFlow<Graph?> by lazy {
        configs.config.distinctUntilChanged()
            .runningFold<Config?, Graph?>(null) { old, c -> old?.close(); c?.let { Graph(it, this, scope) } }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }
    override fun onCreate() { super.onCreate(); configs = ConfigStore(this); onboarding = Onboarding(DataStoreProgress(this), scope) }

    private fun totalRam(): Long = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
}
