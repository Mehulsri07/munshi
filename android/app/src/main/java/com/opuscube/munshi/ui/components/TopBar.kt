package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Tokens

enum class Status { Ready, Loading, Offline }

fun statusOf(live: Boolean, mind: String?): Status = when { !live -> Status.Offline; mind != null -> Status.Loading; else -> Status.Ready }

// Beside the wordmark: green connected and AI ready, amber model on its way, grey reconnecting.
@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier) {
    val c = when (status) { Status.Ready -> Tokens.Ok; Status.Loading -> Tokens.Warn; Status.Offline -> Tokens.Ink3 }
    Box(modifier.size(10.dp).background(c, CircleShape))
}

// Wordmark and status (tap: settings) and the week chip (tap: hisaab).
@Composable
fun TopBar(status: Status, weekTotal: Int?, flash: Boolean, onBrand: () -> Unit, onWeek: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Settings", onClick = onBrand).padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Munshi", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Tokens.Ink)
            StatusDot(status)
        }
        Spacer(Modifier.weight(1f))
        weekTotal?.let { WeekChip(it, flash, onWeek) }
    }
}
