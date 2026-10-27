package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

@Composable
fun Banner(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().background(Tokens.Ink, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ivory,
    )
}
