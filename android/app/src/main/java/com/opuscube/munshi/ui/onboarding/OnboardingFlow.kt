package com.opuscube.munshi.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.opuscube.munshi.MunshiApp
import com.opuscube.munshi.onboarding.Onboarding
import com.opuscube.munshi.onboarding.Step
import com.opuscube.munshi.pairing.Config
import com.opuscube.munshi.pairing.parseConfig
import kotlinx.coroutines.launch

private fun granted(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

// The /control page's QR: {"url", "key"}. Google's scanner, so the app needs no camera permission.
private fun scanSetupQr(context: Context, onConfig: (Config) -> Unit, onError: (String) -> Unit) {
    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, opts).startScan()
        .addOnSuccessListener { b -> parseConfig(b.rawValue.orEmpty())?.let(onConfig) ?: onError("Yeh Munshi ka QR nahi hai.") }
        .addOnFailureListener { onError("Scan nahi hua. Dobara koshish kijiye.") }
}

@Composable
fun OnboardingFlow(app: MunshiApp, onboarding: Onboarding) {
    val ui by onboarding.ui.collectAsStateWithLifecycle()
    val graph by app.graph.collectAsStateWithLifecycle()
    val paired by app.paired.collectAsStateWithLifecycle()
    val brain by app.brain.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mic by remember { mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO)) }
    var notify by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it }
    val step = ui?.step

    // Paired (scanned, typed, or from adb): carry on to the number. Pairing cleared later: back to Connect.
    LaunchedEffect(graph, paired, step) {
        if (graph != null && step == Step.Connect) onboarding.paired()
        if (paired == false && step != null && step.ordinal > Step.Connect.ordinal) onboarding.reconnect()
    }
    // After a restart on the Shop step, the looked-up details come from the server again.
    LaunchedEffect(graph, step) {
        val g = graph
        if (g != null && step == Step.Shop && ui?.profile == null) runCatching { g.api.merchant() }.getOrNull()?.let(onboarding::setProfile)
    }

    val u = ui ?: return
    val save = { c: Config -> scanError = null; scope.launch { app.configs.save(c) }; Unit }
    OnboardingContent(u.copy(error = u.error ?: scanError.takeIf { u.step == Step.Connect }), OnboardingEnv(app.modelName, brain, mic, notify, app.ramGb), OnboardingActions(
        next = onboarding::next, back = onboarding::back,
        scan = { scanSetupQr(context, save) { scanError = it } },
        typeUrl = { url -> parseConfig("""{"url":"${url.trim()}","key":""}""")?.let(save) ?: run { scanError = "Yeh pata sahi nahi lag raha." } },
        phone = onboarding::setPhone,
        lookup = { graph?.let { onboarding.lookup(it.api) } },
        profile = onboarding::setProfile,
        save = { graph?.let { onboarding.save(it.api) } },
        askMic = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
        askNotify = { if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) },
        voice = { graph?.let { g -> onboarding.voice { g.conductor.voiceCheck(u.profile?.addressAs ?: "Ramesh ji") } } },
        finish = onboarding::finish,
    ))
}
