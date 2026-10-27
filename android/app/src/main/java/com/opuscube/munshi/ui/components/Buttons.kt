package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

enum class ButtonStyle { Primary, Secondary, Tool }

private val redGradient = Brush.verticalGradient(listOf(Tokens.RedTop, Tokens.RedBottom))

// Primary (red) is the one thing to do now; Secondary is Nahi; Tool is a small pill (Boliye, Shelf photo).
@Composable
fun MunshiButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.Primary, icon: ImageVector? = null, enabled: Boolean = true) {
    val tool = style == ButtonStyle.Tool
    val shape = RoundedCornerShape(if (tool) Tokens.RadiusPill.dp else Tokens.RadiusButton.dp)
    val look = when (style) {
        ButtonStyle.Primary -> Modifier.background(redGradient, shape)
        ButtonStyle.Secondary -> Modifier.border(1.5.dp, Tokens.Ivory.copy(alpha = 0.4f), shape)
        ButtonStyle.Tool -> Modifier.background(Tokens.Ivory.copy(alpha = 0.1f), shape)
    }
    Row(
        modifier.height(if (tool) 44.dp else 56.dp).alpha(if (enabled) 1f else 0.4f).clip(shape).then(look)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (tool) 14.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(if (tool) 20.dp else 22.dp), tint = Tokens.Ivory) }
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = if (tool) 14.sp else Tokens.TypeButtonSize.sp, color = Tokens.Ivory, maxLines = 1)
    }
}

// Boliye as the main action when nothing is being asked; listening adds the ring.
@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(88.dp).then(if (listening) Modifier.border(3.dp, Tokens.RedOnInk, CircleShape) else Modifier)
            .clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Boliye", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(72.dp).shadow(12.dp, CircleShape).background(redGradient, CircleShape), contentAlignment = Alignment.Center) {
            Icon(MunshiIcons.Mic, "Boliye", Modifier.size(30.dp), tint = Tokens.Ivory)
        }
    }
}
