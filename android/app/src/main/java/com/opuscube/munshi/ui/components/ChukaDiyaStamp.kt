package com.opuscube.munshi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

// A round red ledger stamp that drops onto the page when a payment lands.
@Composable
fun ChukaDiyaStamp(date: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    // Previews and screenshot tests show the settled stamp, not the first frame of the drop.
    val drop = animate && !LocalInspectionMode.current
    val s = remember { Animatable(if (drop) 1.6f else 1f) }
    LaunchedEffect(Unit) { if (drop) s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Box(
        modifier.size(88.dp).scale(s.value).rotate(-12f).alpha(0.92f)
            .border(2.5.dp, Tokens.Red, CircleShape).padding(5.dp).border(1.5.dp, Tokens.Red, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CHUKA", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.14.em, color = Tokens.Red)
            Text("diya", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 17.sp, color = Tokens.Red)
            Text(date.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.14.em, color = Tokens.Red)
        }
    }
}
