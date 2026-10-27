package com.opuscube.munshi.conductor

import com.opuscube.munshi.api.Brief
import com.opuscube.munshi.api.Display
import com.opuscube.munshi.api.FactKhata
import com.opuscube.munshi.api.Ledger
import com.opuscube.munshi.api.Profile
import com.opuscube.munshi.voice.Line

enum class Mode { Brief, Speaking, Listening, Working, Idle }

data class Ask(val id: Long, val display: Display?, val line: Line, val type: String? = null)
enum class QrState { Open, Paid, Expired }
data class PayQr(val url: String, val ref: String, val name: String?, val amount: Int?, val state: QrState = QrState.Open)
data class ShelfLine(val sku: String, val name: String, val count: Int?, val level: String)

data class UiState(
    val mode: Mode = Mode.Brief,
    val brief: Brief? = null,
    val caption: Line? = null,       // what Munshi last said, when it is not a question
    val ask: Ask? = null,            // the open question; Haan / Nahi are enabled only when set
    val heard: String? = null,       // "Aapne kaha: …"
    val payQrs: List<PayQr> = emptyList(),   // every link shown, newest first; a paid one leaves after 4 s
    val banner: String? = null,      // announcement band, 6.5 s
    val ledger: Ledger? = null,
    val live: Boolean = false,
    val shopOpen: Boolean = false,
    val asked: String? = null,       // the merchant's own question while Munshi makes the answer
    val shelf: List<ShelfLine>? = null,   // the last shelf reading, until its offer is answered
    val khata: List<FactKhata> = emptyList(),   // open udhaar, for the tally and the udhaar sheet
    val profile: Profile? = null,    // the names from onboarding
    val mind: String? = null,        // the on-phone model's status while it is not ready ("aa raha hai · 1.2 / 3.7 GB")
)
