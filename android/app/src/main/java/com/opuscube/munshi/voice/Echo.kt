package com.opuscube.munshi.voice

// The phone's speaker is next to its microphone: the listen that opens after a question can catch the tail of
// Munshi's own voice. An echo is long, made mostly of words from the line just spoken (Roman or Devanagari),
// and repeats its numbers; a merchant's own question ("Pinky didi ka udhaar kitna baaki hai") does not.
private fun words(s: String): List<String> =
    s.lowercase().replace(Regex("[₹,।?!.\"“”]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

fun isEcho(heard: String, spoken: Line): Boolean {
    val h = words(heard)
    if (h.size < 6) return false
    val said = (words(spoken.text) + words(spoken.spoken.orEmpty())).toSet()
    val numbers = said.filter { w -> w.all { it.isDigit() } }.toSet()
    return h.count { it in said } >= h.size * 0.7 && h.any { it in numbers }
}
