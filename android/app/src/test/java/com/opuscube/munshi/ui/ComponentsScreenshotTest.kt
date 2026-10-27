package com.opuscube.munshi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.opuscube.munshi.conductor.PayQr
import com.opuscube.munshi.conductor.QrState
import com.opuscube.munshi.ui.components.*
import com.opuscube.munshi.ui.theme.Tokens
import org.junit.Rule
import org.junit.Test

// The Figma Components page, in Compose.
class ComponentsScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)
    private val qr = PayQr("https://munshi.test/pay/ab12", "ab12", "Sharma ji", 450)

    @Test fun dockControls() = paparazzi.snapshot {
        Column(Modifier.fillMaxSize().background(Tokens.Ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MunshiButton("Haan", {}, Modifier.weight(1f), icon = MunshiIcons.Check)
                MunshiButton("Nahi", {}, Modifier.width(130.dp), style = ButtonStyle.Secondary)
            }
            MunshiButton("Dukaan kholo", {}, Modifier.fillMaxWidth(), icon = MunshiIcons.Store)
            MunshiButton("Dukaan kholo", {}, Modifier.fillMaxWidth(), icon = MunshiIcons.Store, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MunshiButton("Boliye", {}, style = ButtonStyle.Tool, icon = MunshiIcons.Mic)
                MunshiButton("Shelf photo", {}, style = ButtonStyle.Tool, icon = MunshiIcons.Camera)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { MicButton(false, {}); MicButton(true, {}) }
        }
    }

    @Test fun paperParts() = paparazzi.snapshot {
        LedgerPage {
            Column(Modifier.padding(start = 40.dp, top = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TopBar(Status.Ready, 742, false, {}, {})
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { StatusDot(Status.Ready); StatusDot(Status.Loading); StatusDot(Status.Offline) }
                QrCard(qr, "26 Okt", {}, animate = false)
                QrCard(qr.copy(state = QrState.Paid), "26 Okt", {}, animate = false)
                QrCard(qr.copy(state = QrState.Expired), "26 Okt", {}, animate = false)
                TaskRow("Udhaar · 12 din", "Sharma ji", 450)
                FieldRow("Dukaan ka naam", "Ramesh Kirana Store", {})
            }
        }
    }
}
