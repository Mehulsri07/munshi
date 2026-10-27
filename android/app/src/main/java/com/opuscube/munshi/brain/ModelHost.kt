package com.opuscube.munshi.brain

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File

// The model on this phone: picks E4B or E2B by memory, takes a file pushed by adb or downloads it on
// Wi-Fi, loads it once (GPU, else CPU) and serves one generation at a time.
class ModelHost(private val context: Context, private val scope: CoroutineScope, private val http: OkHttpClient) {
    private val _state = MutableStateFlow<BrainState>(BrainState.Missing)
    val state: StateFlow<BrainState> = _state
    private val dir = File(context.filesDir, "models")
    private val lock = Mutex()
    @Volatile private var engine: Engine? = null
    private var backendName = "?"

    @Volatile private var current: Conversation? = null
    @Volatile private var currentIsBackground = false

    val llm: Llm = object : Llm {
        override suspend fun generate(system: String, user: String, image: ByteArray?, background: Boolean): String {
            if (!background && currentIsBackground) {   // the merchant is waiting: stop the warm-up
                Log.i(TAG, "pre-empting a warm-up for: ${user.take(40)}")
                current?.cancelProcess()
            }
            return lock.withLock {
                val t0 = SystemClock.elapsedRealtime()
                val what = "${if (background) "bg · " else ""}${if (image != null) "image + " else ""}${user.take(40)}"
                try {
                    run(system, user, image, background).also { Log.i(TAG, "${SystemClock.elapsedRealtime() - t0} ms · $backendName · $what") }
                } catch (e: Throwable) {
                    Log.w(TAG, "${SystemClock.elapsedRealtime() - t0} ms · failed (${e.javaClass.simpleName}: ${e.message}) · $what")
                    throw e
                }
            }
        }
    }

    fun start() { scope.launch(Dispatchers.IO) { prepare() } }

    private suspend fun prepare() {
        val m = Models.forRam(totalRam()) ?: run { _state.value = BrainState.Unsupported; return }
        dir.mkdirs()
        val file = File(dir, m.name)
        if (!file.isFile) {
            if (!onUnmeteredNetwork()) { _state.value = BrainState.Missing; return }   // next start on Wi-Fi, or adb push
            if (dir.usableSpace < m.bytes + 500_000_000) { _state.value = BrainState.Failed("not enough space"); return }
            var shown = -1L
            val ok = ModelDownloader(http).fetch(m, file) { done, total ->
                if (done - shown >= 20_000_000 || done == total) { shown = done; _state.value = BrainState.Downloading(done, total) }
            }
            if (!ok) { _state.value = BrainState.Failed("download"); return }
        }
        _state.value = BrainState.Loading
        engine = load(file)
        _state.value = if (engine != null) BrainState.Ready else BrainState.Failed("load")
        Log.i(TAG, "model ${m.name} → ${_state.value} on $backendName")
    }

    @OptIn(ExperimentalApi::class)
    private fun load(file: File): Engine? {
        ExperimentalFlags.enableSpeculativeDecoding = true
        ExperimentalFlags.enableBenchmark = true   // per-call token counts and speeds in logcat (MunshiBrain)
        for ((name, backend) in listOf("gpu" to Backend.GPU(), "cpu" to Backend.CPU())) {
            val e = runCatching {
                Engine(EngineConfig(modelPath = file.path, backend = backend, visionBackend = backend, maxNumImages = 1, cacheDir = context.cacheDir.path))
                    .also { it.initialize() }
            }.onFailure { Log.w(TAG, "$name backend failed", it) }.getOrNull()
            if (e != null) { backendName = name; return e }
        }
        return null
    }

    // A fresh conversation per call: no history leaks between questions or customers. If the caller gives up
    // (time limit) or a warm-up is pre-empted, the generation is stopped instead of holding the model.
    @OptIn(ExperimentalApi::class)
    private suspend fun run(system: String, user: String, image: ByteArray?, background: Boolean): String {
        val e = engine ?: error("model not ready")
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
            maxOutputToken = 256,
        )
        val c = e.createConversation(config)
        current = c; currentIsBackground = background
        try {
            return coroutineScope {
                val work = async(Dispatchers.IO) {
                    val input = if (image != null) Contents.of(Content.ImageBytes(image), Content.Text(user)) else Contents.of(user)
                    val out = c.sendMessage(input).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                    runCatching { c.getBenchmarkInfo() }.getOrNull()?.let { b ->
                        Log.i(TAG, "bench prefill ${b.lastPrefillTokenCount} tok @ ${"%.0f".format(b.lastPrefillTokensPerSecond)}/s, decode ${b.lastDecodeTokenCount} tok @ ${"%.1f".format(b.lastDecodeTokensPerSecond)}/s, ttft ${"%.1f".format(b.timeToFirstTokenInSecond)} s")
                    }
                    Log.i(TAG, "out ${out.length} chars: ${out.take(160).replace("\n", " | ")}")
                    out
                }
                try { work.await() } catch (x: CancellationException) { c.cancelProcess(); throw x }
            }
        } finally {
            current = null
            c.close()
        }
    }

    private fun totalRam(): Long =
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem

    private fun onUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    private companion object { const val TAG = "MunshiBrain" }
}
