package com.opuscube.munshi.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, speaking: Boolean = false, color: Color = Tokens.Red) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (speaking) SpeakingBars()
        Text(text.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = Tokens.TypeKickerSize.sp,
            letterSpacing = Tokens.TypeKickerTracking.em, color = color)
    }
}

@Composable
private fun SpeakingBars() {
    val t = rememberInfiniteTransition(label = "bars")
    Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val h by t.animateFloat(3f, 10f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(2.dp).height(h.dp).background(Tokens.Red, RoundedCornerShape(1.dp)))
        }
    }
}
