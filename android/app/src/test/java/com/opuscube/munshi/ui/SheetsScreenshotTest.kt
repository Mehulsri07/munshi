package com.opuscube.munshi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.opuscube.munshi.api.*
import com.opuscube.munshi.ui.components.*
import com.opuscube.munshi.ui.theme.Tokens
import org.junit.Rule
import org.junit.Test

// The Figma Sheets page. Paparazzi cannot draw a ModalBottomSheet window, so each body is shot on paper.
class SheetsScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun shot(body: @Composable ColumnScope.() -> Unit) = paparazzi.snapshot {
        Column(Modifier.fillMaxSize().background(Tokens.Paper).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
    }

    @Test fun udhaar() = shot { UdhaarBody(listOf(FactKhata(1, "Sharma ji", 450, 12), FactKhata(2, "Pinky didi", 230, 9))) {} }

    @Test fun hisaab() = shot {
        HisaabBody(Ledger(742, listOf(
            LedgerItem("udhaar_recovered", 450, "Sharma ji · reminder se", "", "send_udhaar_reminder"),
            LedgerItem("winback", 180, "12 grahak laute", "", "send_winback_offer"),
            LedgerItem("group_buy_saving", 112, "Surf Excel · 4 dukaanein", "", "join_group_buy"),
        ), Loan(50000, 52400, 520, 100)))
    }

    @Test fun settings() = shot {
        SettingsBody(SettingsInfo(Profile("Ramesh Kirana Store", "Aliganj, Lucknow", addressAs = "Ramesh ji", phone = "+919876543210"),
            "Gemma 4 E4B · taiyaar · GPU par", true, "https://abc-def.trycloudflare.com"), SettingsActions({}, {}, {}))
    }
}
