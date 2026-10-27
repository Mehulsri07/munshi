package com.opuscube.munshi.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opuscube.munshi.camera.jpegBytes
import com.opuscube.munshi.camera.newPhotoUri
import com.opuscube.munshi.conductor.Answer
import com.opuscube.munshi.conductor.Conductor
import com.opuscube.munshi.ui.components.HisaabSheet
import com.opuscube.munshi.ui.components.SettingsActions
import com.opuscube.munshi.ui.components.SettingsInfo
import com.opuscube.munshi.ui.components.SettingsSheet
import com.opuscube.munshi.ui.components.UdhaarSheet
import com.opuscube.munshi.ui.theme.romanDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MainScreen(conductor: Conductor, settings: SettingsInfo, onRepair: () -> Unit, onEditShop: () -> Unit, onRedoOnboarding: () -> Unit) {
    val state by conductor.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { conductor.reloadProfile() }
    var sheet by remember { mutableStateOf<String?>(null) }   // "hisaab", "udhaar" or "settings"
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // Onboarding asks for the mic; if it was refused, Boliye asks again.
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) conductor.mic() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { conductor.shelf(jpegBytes(context, it)) }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) conductor.shelf(jpegBytes(context, u))
        else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) }
    val dateLine = remember { romanDate(LocalDate.now()) }
    MainContent(state, MainActions(
        openShop = { conductor.openShop() },
        yes = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); conductor.answer(Answer.Yes) },
        no = { conductor.answer(Answer.No) },
        mic = { if (micGranted(context)) conductor.mic() else askMic.launch(Manifest.permission.RECORD_AUDIO) },
        camera = {
            // Some phones refuse the camera intent (policy, no camera app): pick from the gallery instead.
            val u = newPhotoUri(context); photoUri = u
            runCatching { take.launch(u) }.onFailure { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        },
        week = { sheet = "hisaab" },
        settings = { sheet = "settings" },
        udhaar = { sheet = "udhaar" },
        closeShop = conductor::closeShop,
        relink = conductor::relink,
    ), today, dateLine = dateLine)

    val ledger = state.ledger
    when (sheet) {
        "hisaab" -> if (ledger != null) HisaabSheet(ledger) { sheet = null }
        "udhaar" -> UdhaarSheet(state.khata, { id -> sheet = null; conductor.remind(id) }) { sheet = null }
        "settings" -> SettingsSheet(settings, SettingsActions(
            editShop = { sheet = null; onEditShop() }, repair = { sheet = null; onRepair() }, redoOnboarding = { sheet = null; onRedoOnboarding() },
        )) { sheet = null }
    }
}

private fun micGranted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
