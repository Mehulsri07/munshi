package com.opuscube.munshi.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

// The price-in-a-fine-menu figure: small raised ₹, large tight numerals.
@Composable
fun RupeeFigure(amount: Int, modifier: Modifier = Modifier, size: TextUnit = Tokens.TypeRupeeSize.sp, color: Color = Tokens.Ink) {
    Text(
        buildAnnotatedString {
            // Positive tracking on the small ₹ keeps it clear of the first numeral's stroke.
            withStyle(SpanStyle(fontSize = size * 0.48f, baselineShift = BaselineShift(0.62f), fontWeight = FontWeight.Normal, letterSpacing = 0.18.em)) { append("₹") }
            append(rupees(amount).removePrefix("₹"))
        },
        modifier = modifier,
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = size,
        letterSpacing = Tokens.TypeRupeeTracking.em, lineHeight = size * 0.95f, color = color,
    )
}
