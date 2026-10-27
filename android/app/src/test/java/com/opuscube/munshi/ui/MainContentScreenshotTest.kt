package com.opuscube.munshi.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.opuscube.munshi.api.*
import com.opuscube.munshi.conductor.*
import com.opuscube.munshi.ui.screens.MainActions
import com.opuscube.munshi.ui.screens.MainContent
import com.opuscube.munshi.voice.Line
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// The Figma Counter screen frames (C1–C6), on a phone and on a landscape tablet.
@RunWith(Parameterized::class)
class MainContentScreenshotTest(private val device: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun devices() = listOf("phone", "tablet")
    }

    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = if (device == "phone") DeviceConfig.PIXEL_5 else DeviceConfig.NEXUS_10.copy(orientation = ScreenOrientation.LANDSCAPE),
    )

    private val noop = MainActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val ledger = Ledger(742, listOf(LedgerItem("udhaar_recovered", 230, "Pinky didi ka udhaar wapas", "", "send_udhaar_reminder")))
    private val brief = Brief(listOf(BriefItem(1, "udhaar_overdue", "Sharma ji ka udhaar", 450), BriefItem(6, "local_demand_spike", "Soan Papdi stock", 924), BriefItem(2, "udhaar_overdue", "Pinky didi ka udhaar", 230)), 1604)
    private val khata = listOf(FactKhata(1, "Sharma ji", 450, 12), FactKhata(2, "Pinky didi", 230, 9))
    private val sharma = Ask(1, Display("Sharma ji", 450, "Sharma ji ka ₹450 udhaar 12 din se baaki hai. WhatsApp par reminder aur pay link bhej doon?"), Line("…"), type = "udhaar_overdue")
    private val open = PayQr("https://m.test/pay/ab12", "ab12", "Sharma ji", 450)
    private val profile = Profile("Ramesh Kirana Store", addressAs = "Ramesh ji")

    // Snapshots show the settled stamp, not the first frame of its drop-in.
    private fun shot(s: UiState) = paparazzi.snapshot { MainContent(s, noop, today = "26 Okt", dateLine = "Somvaar, 26 Oktober", animate = false) }

    @Test fun c1MorningBrief() = shot(UiState(mode = Mode.Brief, brief = brief, ledger = ledger, live = true, profile = profile))
    @Test fun c2Asking() = shot(UiState(mode = Mode.Speaking, ask = sharma, ledger = ledger, live = true, shopOpen = true))
    @Test fun c3Listening() = shot(UiState(mode = Mode.Listening, ask = sharma, heard = "pehle Pinky didi ko bhejo", ledger = ledger, live = true, shopOpen = true))
    @Test fun c4Thinking() = shot(UiState(mode = Mode.Working, asked = "kitna udhaar baaki hai?", ledger = ledger, live = true, shopOpen = true))
    @Test fun c5Open() = shot(UiState(mode = Mode.Idle, payQrs = listOf(open, open.copy(ref = "cd34", name = "Pinky didi", amount = 230)), khata = khata, ledger = ledger, live = true, shopOpen = true))
    @Test fun c6Shelf() = shot(UiState(mode = Mode.Speaking, ask = Ask(7, Display("Surf Excel group order", 143, "Judein toh 4 dukaanein, 8% bachat. ₹143 bachenge. Jud jaaun?"), Line("…"), "restock"),
        shelf = listOf(ShelfLine("tata_salt", "Tata Salt 1kg", 6, "ok"), ShelfLine("surf_1kg", "Surf Excel 1kg", 2, "low"), ShelfLine("parle_g", "Parle-G", 1, "low")), ledger = ledger, live = true, shopOpen = true))
    @Test fun paidCard() = shot(UiState(mode = Mode.Idle, payQrs = listOf(open.copy(state = QrState.Paid)), khata = khata, ledger = ledger.copy(total = 1192), live = true, shopOpen = true))
    @Test fun answered() = shot(UiState(mode = Mode.Idle, caption = Line("Total udhaar ₹680 baaki hai."), khata = khata, ledger = ledger, live = true, shopOpen = true))
    @Test fun reconnecting() = shot(UiState(mode = Mode.Idle, ask = sharma, ledger = ledger, live = false, shopOpen = true))
    @Test fun downloading() = shot(UiState(mode = Mode.Brief, brief = brief, ledger = ledger, live = true, mind = "Munshi ka dimaag aa raha hai · 1.2 / 3.7 GB"))
    @Test fun banner() = shot(UiState(mode = Mode.Idle, banner = "₹120 prapt hue", khata = khata, ledger = ledger, live = true, shopOpen = true))
}
