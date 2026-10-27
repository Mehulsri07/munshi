package com.opuscube.munshi.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

val MunshiJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type"; explicitNulls = false }

@Serializable data class Display(val who: String, val amount: Int, val rest: String)
@Serializable data class Next(val id: Long? = null, val type: String? = null, val line: String? = null, val spoken: String? = null, val display: Display? = null)
@Serializable data class BriefItem(val id: Long, val type: String, val title: String, val amount: Int)
@Serializable data class Brief(val items: List<BriefItem>, val total: Int)
@Serializable data class PayFor(val name: String, val amount: Int)
@Serializable data class ActionResult(
    val line: String, val spoken: String? = null, val payUrl: String? = null,
    val payFor: PayFor? = null, val opportunityId: Long? = null,
)
@Serializable data class Offer(val id: Long, val line: String, val spoken: String? = null, val display: Display? = null)
@Serializable data class ShelfResult(val line: String, val spoken: String? = null, val next: Offer? = null)
@Serializable data class LedgerItem(val kind: String, val amount: Int, val label: String, val ts: String, val tool: String)
@Serializable data class Loan(val principal: Int, val totalCost: Int, val dailyDeduction: Int, val tenureDays: Int)
@Serializable data class Ledger(val total: Int, val items: List<LedgerItem>, val loan: Loan? = null)

// The shop's details from onboarding (/api/onboard/lookup, /api/merchant).
@Serializable data class Profile(val shopName: String, val locality: String = "", val category: String = "", val ownerName: String = "", val addressAs: String, val phone: String? = null)
@Serializable data class ProfileReply(val profile: Profile)

// GET /api/facts: everything the phone's model may talk about.
@Serializable data class FactKhata(val khataId: Long, val name: String, val amount: Int, val days: Int)
@Serializable data class FactTask(val id: Long, val type: String, val line: String, val spoken: String? = null, val amount: Int)
@Serializable data class Week(val total: Int)
@Serializable data class Sku(val sku: String, val name: String)
@Serializable data class Facts(val shop: String, val khata: List<FactKhata>, val tasks: List<FactTask>, val week: Week, val inventory: List<Sku>, val addressAs: String? = null)
@Serializable data class Proposed(val opportunityId: Long, val line: String, val spoken: String? = null, val display: Display? = null)
@Serializable data class Reading(val sku: String, val level: String, val count: Int? = null)

@Serializable
sealed interface SoundboxEvent {
    @Serializable @SerialName("announce") data class Announce(val text: String, val spoken: String? = null) : SoundboxEvent
    @Serializable @SerialName("brief") data object BriefCue : SoundboxEvent
    @Serializable @SerialName("nudge") data object Nudge : SoundboxEvent
    @Serializable @SerialName("ledger") data object LedgerChanged : SoundboxEvent
    @Serializable @SerialName("paid") data class Paid(val ref: String, val name: String? = null, val amount: Int? = null) : SoundboxEvent
    @Serializable @SerialName("reset") data object Reset : SoundboxEvent
    @Serializable @SerialName("shelf") data class Shelf(val line: String, val spoken: String? = null, val next: Offer? = null) : SoundboxEvent
    @Serializable @SerialName("expired") data class Expired(val ref: String, val name: String? = null, val amount: Int? = null) : SoundboxEvent
    // Not sent by the server: EventStream emits it when the poll cursor fell off the server's buffer.
    @Serializable @SerialName("resync") data object Resync : SoundboxEvent
}

// Newer servers may send event types this build does not know; those are skipped, not fatal.
fun parseEvent(data: String): SoundboxEvent? =
    runCatching { MunshiJson.decodeFromString(SoundboxEvent.serializer(), data) }.getOrNull()

data class PollResult(val events: List<SoundboxEvent>, val seq: Long, val resync: Boolean)

// GET /api/events/poll: {"events":[{"seq":N,"type":…}],"seq":N} or {"resync":true,"seq":N}.
fun parsePoll(data: String): PollResult? = runCatching {
    val o = MunshiJson.parseToJsonElement(data).jsonObject
    PollResult(
        events = o["events"]?.jsonArray.orEmpty().mapNotNull { parseEvent(it.toString()) },
        seq = o.getValue("seq").jsonPrimitive.long,
        resync = o["resync"]?.jsonPrimitive?.booleanOrNull == true,
    )
}.getOrNull()
