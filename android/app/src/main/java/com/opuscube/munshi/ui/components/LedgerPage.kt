package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.opuscube.munshi.ui.theme.Tokens

// Ivory paper with the red margin line. The ruled lines of the first design are gone: calmer behind the cards.
@Composable
fun LedgerPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(Tokens.Paper).drawBehind {
            val hair = 1.dp.toPx()
            val x = Tokens.SpaceMarginLineX.dp.toPx()
            drawLine(Tokens.MarginLine, Offset(x, 0f), Offset(x, size.height), hair)
        },
        content = content,
    )
}
