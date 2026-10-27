package com.opuscube.munshi

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.opuscube.munshi.api.MunshiJson
import com.opuscube.munshi.pairing.Config
import com.opuscube.munshi.pairing.parseConfig
import com.opuscube.munshi.service.MunshiService
import com.opuscube.munshi.ui.screens.MainScreen
import com.opuscube.munshi.ui.onboarding.OnboardingFlow
import com.opuscube.munshi.ui.theme.Tokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.opuscube.munshi.ui.components.SettingsInfo
import com.opuscube.munshi.ui.components.brainNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.opuscube.munshi.conductor.Answer
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark status and navigation icons on the light paper; the screens pad themselves to the safe area.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // counter mode
        val app = application as MunshiApp
        // Pairing without the camera, e.g. from adb: am start ... --es url http://10.0.2.2:3000 [--es key K]
        intent.getStringExtra("url")?.let { parseConfig(Config(it, intent.getStringExtra("key").orEmpty()).let { c -> MunshiJson.encodeToString(Config.serializer(), c) }) }
            ?.let { c -> lifecycleScope.launch { app.configs.save(c) } }
        MunshiService.start(this)
        setContent {
            val scope = rememberCoroutineScope()
            val graph by app.graph.collectAsStateWithLifecycle()
            val done by app.onboarding.done.collectAsStateWithLifecycle()
            val paired by app.paired.collectAsStateWithLifecycle()
            val g = graph
            when {
                // Reading the saved step and pairing: decide nothing yet, or a cold start looks like a cleared pairing.
                done == null || paired == null -> Box(Modifier.fillMaxSize().background(Tokens.Paper))
                done == false || paired == false -> OnboardingFlow(app, app.onboarding)
                g == null -> Box(Modifier.fillMaxSize().background(Tokens.Paper))
                else -> {
                    val brainState by app.brain.state.collectAsStateWithLifecycle()
                    val ui by g.conductor.state.collectAsStateWithLifecycle()
                    val (note, ok) = brainNote(brainState)
                    MainScreen(g.conductor, SettingsInfo(ui.profile, note, ok, g.config.url),
                        onRepair = { scope.launch { app.configs.clear(); app.onboarding.reconnect() } },
                        onEditShop = app.onboarding::editShop, onRedoOnboarding = app.onboarding::redo)
                }
            }
        }
        drive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drive(intent)
    }

    // Debug builds only: drive the Soundbox from adb for end-to-end tests without a voice or a camera.
    //   adb shell am start -n com.opuscube.munshi/.MainActivity --es say "kitna udhaar baaki hai"
    //   adb shell am start -n com.opuscube.munshi/.MainActivity --es tap open|haan|nahi
    //   adb shell am start -n com.opuscube.munshi/.MainActivity --es shelf test/shelf.jpg   (inside the app's files dir)
    private fun drive(intent: Intent) {
        android.util.Log.i("MunshiFlow", "drive: debuggable=${applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0} extras=${intent.extras?.keySet()}")
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val say = intent.getStringExtra("say"); val tap = intent.getStringExtra("tap"); val shelf = intent.getStringExtra("shelf")
        intent.removeExtra("say"); intent.removeExtra("tap"); intent.removeExtra("shelf")
        if (say == null && tap == null && shelf == null) return
        android.util.Log.i("MunshiFlow", "adb: say=$say tap=$tap shelf=$shelf")
        val app = application as MunshiApp
        lifecycleScope.launch {
            val c = app.graph.value?.conductor ?: return@launch android.util.Log.w("MunshiFlow", "adb: not paired yet").let {}
            say?.let { c.hear(it) }
            when (tap) { "open" -> c.openShop(); "haan" -> c.answer(Answer.Yes); "nahi" -> c.answer(Answer.No) }
            shelf?.let { c.shelf(withContext(Dispatchers.IO) { File(filesDir, it).readBytes() }) }
        }
    }
}
