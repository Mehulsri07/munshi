package com.opuscube.munshi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

// A line in the brief or the udhaar sheet: why (kicker), who, how much.
@Composable
fun TaskRow(kicker: String, who: String, amount: Int, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Kicker(kicker)
                Text(who, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Tokens.Ink)
            }
            Text(rupees(amount), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Tokens.Ink)
            trailing?.invoke()
        }
        HorizontalDivider(color = Tokens.Rule)
    }
}

// Onboarding field: small label, large value, one underline.
@Composable
fun FieldRow(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, big: Boolean = false, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.16.em, color = Tokens.Ink3)
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = keyboard,
            textStyle = TextStyle(fontFamily = if (accent) Fraunces else Inter, fontStyle = if (accent) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (big) FontWeight.Medium else FontWeight.Normal, fontSize = if (big) 26.sp else 18.sp, color = if (accent) Tokens.Red else Tokens.Ink),
            cursorBrush = SolidColor(Tokens.Red))
        HorizontalDivider(thickness = 1.5.dp, color = Tokens.Ink.copy(alpha = 0.25f))
    }
}
