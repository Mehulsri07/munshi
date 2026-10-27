package com.opuscube.munshi.brain

import com.opuscube.munshi.api.Facts
import com.opuscube.munshi.api.MunshiJson
import com.opuscube.munshi.api.Reading
import com.opuscube.munshi.api.Sku
import com.opuscube.munshi.voice.Line
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Small models drift: these checks decide whether a reply may be used at all. Anything doubtful is null.

private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
private val LEVELS = setOf("empty", "low", "ok")

fun levelFor(count: Int): String = when { count <= 0 -> "empty"; count <= 3 -> "low"; else -> "ok" }

/** Every number in a line, commas dropped: "₹1,240" and "1240" are the same number. */
fun numbers(s: String): Set<String> =
    Regex("\\d[\\d,]*").findAll(s).map { it.value.replace(",", "") }.filter { it.isNotEmpty() }.toSet()

private fun firstJson(raw: String, open: Char, close: Char): String? {
    val a = raw.indexOf(open); val b = raw.lastIndexOf(close)
    return if (a >= 0 && b > a) raw.substring(a, b + 1) else null
}

/** Two lines back: Roman Hinglish, then Devanagari. Same numbers, same question; else null. */
fun parseReword(raw: String, template: Line): Line? {
    val lines = raw.lines().map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
    val roman = lines.getOrNull(0) ?: return null
    val need = numbers(template.text)
    if (DEVANAGARI.containsMatchIn(roman) || !numbers(roman).containsAll(need)) return null
    if (template.text.trimEnd().endsWith("?") && !roman.trimEnd().endsWith("?")) return null
    val dev = lines.getOrNull(1)?.takeIf { DEVANAGARI.containsMatchIn(it) && numbers(it).containsAll(need) }
    return Line(roman, dev ?: template.spoken)
}

/** {"say", "spoken", "propose": {"kind":"udhaar_reminder","khataId"} | null}, checked against the facts. */
private val INTERNAL = Regex("khataId|propose|json|null|[{}]", RegexOption.IGNORE_CASE)   // the prompt's own words, never said aloud

fun udhaarTotal(facts: Facts): Int = facts.khata.sumOf { it.amount }

fun parseAnswer(raw: String, facts: Facts): BrainReply? {
    val obj = firstJson(raw, '{', '}')?.let { runCatching { MunshiJson.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
    val say = obj["say"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (say.isEmpty() || DEVANAGARI.containsMatchIn(say) || INTERNAL.containsMatchIn(say)) return null
    val known = numbers(MunshiJson.encodeToString(Facts.serializer(), facts)) + "${udhaarTotal(facts)}" + "${facts.khata.size}"
    if (!known.containsAll(numbers(say))) return null                  // never a number the shop's data does not have
    val spoken = obj["spoken"]?.jsonPrimitive?.contentOrNull?.trim()
        ?.takeIf { DEVANAGARI.containsMatchIn(it) && known.containsAll(numbers(it)) }
    val propose = obj["propose"] as? JsonObject
    val khataId = propose?.get("khataId")?.jsonPrimitive?.longOrNull
    if (propose != null && (khataId == null || facts.khata.none { it.khataId == khataId })) return null
    return BrainReply(Line(say, spoken), khataId)
}

/** A JSON array of {"sku","level"}; unknown SKUs and levels dropped; nothing usable is null. */
fun parseShelf(raw: String, items: List<Sku>): List<Reading>? {
    val arr = firstJson(raw, '[', ']')?.let { runCatching { MunshiJson.parseToJsonElement(it).jsonArray }.getOrNull() } ?: return null
    val skus = items.map { it.sku }.toSet()
    return arr.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val sku = o["sku"]?.jsonPrimitive?.contentOrNull
        // The model counts packets; the level is ours (it counts more reliably than it applies a rule).
        val count = o["count"]?.jsonPrimitive?.intOrNull
        val level = count?.let { levelFor(it) } ?: o["level"]?.jsonPrimitive?.contentOrNull
        if (sku != null && level != null && sku in skus && level in LEVELS) Reading(sku, level, count) else null
    }.takeIf { it.isNotEmpty() }
}
