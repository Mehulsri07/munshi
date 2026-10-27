package com.opuscube.munshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.api.Ledger
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

// The week chip's sheet: every rupee Munshi brought in, tied to what brought it; the loan apart.
@Composable
fun HisaabSheet(ledger: Ledger, onDismiss: () -> Unit) = MunshiSheet(onDismiss) { HisaabBody(ledger) }

private fun kindLabel(kind: String) = when (kind) {
    "udhaar_recovered" -> "Udhaar wapas"
    "winback" -> "Win-back"
    "group_buy_saving" -> "Group order bachat"
    "restock_margin" -> "Restock"
    else -> "Kamaai"
}

@Composable
fun ColumnScope.HisaabBody(ledger: Ledger) {
    Kicker("Is hafte")
    Text("Munshi ne ${rupees(ledger.total)} dilaye", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, color = Tokens.Ink)
    Text("Har rupaya us kaam se juda hai jisse woh aaya.", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
    Column(Modifier.fillMaxWidth()) {
        ledger.items.forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Kicker(kindLabel(i.kind))
                    Text(i.label, fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink)
                }
                Text("+" + rupees(i.amount), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Tokens.Ink)
            }
            HorizontalDivider(color = Tokens.Rule)
        }
    }
    ledger.loan?.let { l ->
        Column(Modifier.fillMaxWidth().background(Tokens.Ink.copy(alpha = 0.04f), RoundedCornerShape(Tokens.RadiusCard.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Diwali stock loan", Modifier.weight(1f), fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink2)
                Text(rupees(l.principal), fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink2)
            }
            Text("Roz ${rupees(l.dailyDeduction)} settlement se · kul kharcha ${rupees(l.totalCost)}", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
            Text("Kamaai mein nahi gina", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
        }
    }
}
