package com.opuscube.munshi.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.opuscube.munshi.conductor.PayQr
import com.opuscube.munshi.conductor.QrState
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens

// Drawn on the device from the pay link, so it works even when WhatsApp or the network does not.
fun qrBitmap(text: String, px: Int = 360): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
    val ink = Tokens.Ink.toArgb()
    val paper = Tokens.Card.toArgb()
    val pixels = IntArray(px * px) { i -> if (m.get(i % px, i / px)) ink else paper }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}

// One pay link: open (scan to pay), paid (stamped, about to leave), or expired (offer a new link).
@Composable
fun QrCard(qr: PayQr, today: String, onRelink: () -> Unit, modifier: Modifier = Modifier, animate: Boolean = true) {
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    val bmp = remember(qr.url) { qrBitmap(qr.url).asImageBitmap() }
    val who = qr.name ?: "Grahak"
    Row(
        modifier.shadow(6.dp, shape, ambientColor = Tokens.Ink.copy(alpha = 0.1f), spotColor = Tokens.Ink.copy(alpha = 0.1f))
            .background(Tokens.Card, shape).border(1.dp, Tokens.Rule, shape).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            val fade = when (qr.state) { QrState.Open -> 1f; QrState.Paid -> 0.35f; QrState.Expired -> 0.18f }
            Image(bmp, "$who ke liye bhugtaan QR", Modifier.size(100.dp).alpha(fade))
            if (qr.state == QrState.Paid) ChukaDiyaStamp(today, animate = animate)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when (qr.state) { QrState.Open -> "$who, scan karke chukayein"; QrState.Paid -> "$who ne chuka diya"; QrState.Expired -> "$who ka link" },
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Ink)
            qr.amount?.let { RupeeFigure(it, size = 40.sp, color = if (qr.state == QrState.Expired) Tokens.Ink3 else Tokens.Ink) }
            when (qr.state) {
                QrState.Open -> Text("Link 1 ghante tak chalega", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
                QrState.Paid -> Text("Paise aa gaye", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ok)
                QrState.Expired -> {
                    Text("Link khatam ho gaya", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Red)
                    Text("Naya link bhejein →", Modifier.clickable(onClick = onRelink).padding(vertical = 4.dp),
                        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Red)
                }
            }
        }
    }
}

// Every link shown, newest first; the next card peeks in from the right.
@Composable
fun QrStack(qrs: List<PayQr>, today: String, onRelink: (String) -> Unit, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (qrs.isEmpty()) return
    val pager = rememberPagerState { qrs.size }
    // A new link goes in front: show it, not the card the pager was keeping its place on.
    LaunchedEffect(qrs.first().ref) { pager.scrollToPage(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Khule QR · ${qrs.count { it.state == QrState.Open }}")
            Spacer(Modifier.weight(1f))
            if (qrs.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(qrs.size) { i ->
                    val on = i == pager.currentPage
                    Box(Modifier.size(if (on) 8.dp else 6.dp).background(if (on) Tokens.Ink else Tokens.Ink.copy(alpha = 0.2f), CircleShape))
                }
            }
        }
        HorizontalPager(pager, contentPadding = PaddingValues(end = if (qrs.size > 1) 28.dp else 0.dp), pageSpacing = 12.dp, key = { qrs[it].ref }) { i ->
            QrCard(qrs[i], today, { onRelink(qrs[i].ref) }, Modifier.fillMaxWidth(), animate)
        }
    }
}
