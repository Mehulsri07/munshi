package com.opuscube.munshi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.api.Profile
import com.opuscube.munshi.brain.BrainState
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import java.util.Locale

data class SettingsInfo(val profile: Profile?, val brain: String, val brainOk: Boolean, val server: String)
data class SettingsActions(val editShop: () -> Unit, val repair: () -> Unit, val redoOnboarding: () -> Unit)

// From the wordmark: the shop's details, the model, the server, and the ways back into setup.
@Composable
fun SettingsSheet(info: SettingsInfo, actions: SettingsActions, onDismiss: () -> Unit) = MunshiSheet(onDismiss) { SettingsBody(info, actions) }

@Composable
fun ColumnScope.SettingsBody(info: SettingsInfo, a: SettingsActions) {
    val p = info.profile
    Kicker("Dukaan")
    Text(p?.shopName ?: "Munshi", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, color = Tokens.Ink)
    Text(listOfNotNull(p?.locality?.takeIf { it.isNotBlank() }, p?.phone).joinToString(" · "), fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
    Column(Modifier.fillMaxWidth()) {
        SettingRow(MunshiIcons.Store, "Dukaan ki jaankari", "Naam, ilaaka, Munshi aapko kya bulaye", onClick = a.editShop)
        SettingRow(MunshiIcons.Spark, "Munshi ka dimaag", info.brain, ok = info.brainOk)
        SettingRow(MunshiIcons.Wifi, "Server se juda hai", info.server.removePrefix("https://").removePrefix("http://"), ok = true)
        SettingRow(MunshiIcons.Mic, "Awaaz", "Android Hindi voice")
        SettingRow(MunshiIcons.Qr, "Dobara jodiye", "Naya setup QR scan kijiye", onClick = a.repair)
        SettingRow(MunshiIcons.Speaker, "Onboarding phir se", "Shuruaat se saare kadam", onClick = a.redoOnboarding)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, ok: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = Tokens.Ink2)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
                Text(sub, fontFamily = Inter, fontSize = 13.sp, color = if (ok) Tokens.Ok else Tokens.Ink3)
            }
            if (onClick != null) Icon(MunshiIcons.ChevronRight, null, Modifier.size(18.dp), tint = Tokens.Ink3)
        }
        HorizontalDivider(color = Tokens.Rule)
    }
}

// The model's state in one line for the settings row; true when it is ready.
fun brainNote(s: BrainState): Pair<String, Boolean> = when (s) {
    BrainState.Ready -> "Gemma · taiyaar, isi phone par" to true
    is BrainState.Downloading -> String.format(Locale.US, "Aa raha hai · %.1f / %.1f GB", s.done / 1e9, s.total / 1e9) to false
    BrainState.Loading -> "Taiyaar ho raha hai…" to false
    BrainState.Missing -> "Wi-Fi par download hoga" to false
    is BrainState.Failed -> "Nahi chala (${s.why})" to false
    BrainState.Unsupported -> "Is phone par AI nahi, templates chalte hain" to false
}
