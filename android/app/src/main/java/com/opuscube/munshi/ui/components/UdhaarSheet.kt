package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.api.FactKhata
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

// Who owes what, with a reminder one tap away (the same path as asking Munshi by voice).
@Composable
fun UdhaarSheet(khata: List<FactKhata>, onRemind: (Long) -> Unit, onDismiss: () -> Unit) = MunshiSheet(onDismiss) { UdhaarBody(khata, onRemind) }

@Composable
fun ColumnScope.UdhaarBody(khata: List<FactKhata>, onRemind: (Long) -> Unit) {
    Kicker("Udhaar")
    Text(if (khata.isEmpty()) "Koi udhaar baaki nahi" else "${rupees(khata.sumOf { it.amount })} baaki, ${khata.size} log",
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, color = Tokens.Ink)
    khata.forEach { k ->
        TaskRow("${k.days} din se baaki", k.name, k.amount) {
            Row(Modifier.clip(RoundedCornerShape(Tokens.RadiusPill.dp)).background(Tokens.Ink).clickable { onRemind(k.khataId) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(MunshiIcons.Bell, null, Modifier.size(14.dp), tint = Tokens.Ivory)
                Text("Yaad dilao", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ivory)
            }
        }
    }
    Text("Munshi khud bhi poochega jab udhaar 7 din se zyada ho.", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
}
