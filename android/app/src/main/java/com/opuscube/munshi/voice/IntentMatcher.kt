package com.opuscube.munshi.voice

import com.opuscube.munshi.conductor.Answer
import java.text.Normalizer

// Port of classifyReply in server/agent.ts: yes (or no) when every word is a yes-word or a polite
// filler and at least one is a real yes. Anything else goes to the agent.
object IntentMatcher {
    private fun words(s: String) = s.split(' ').toSet()
    private val YES_CORE = words("haan haa han ha hanji haanji yes ok okay theek thik zaroor bhej bhejo kar karo हाँ हां हा ओके ठीक ज़रूर जरूर भेज भेजो कर करो")
    private val YES_FILLER = words("ji जी do दो de दे dijiye दीजिए hai है please")
    private val NO_CORE = words("nahi nahin na no mat nako rehne नहीं नही ना मत रहने")
    private val NO_FILLER = words("ji जी chahiye चाहिए abhi अभी do दो bhej bhejo kar karo भेज भेजो कर करो hai है")
    private val PUNCT = Regex("[\\p{P}\\p{S}]")

    private fun allOf(tokens: List<String>, core: Set<String>, filler: Set<String>) =
        tokens.any { it in core } && tokens.all { it in core || it in filler }

    fun match(text: String): Answer? {
        val tokens = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
            .replace(PUNCT, " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.size > 1 && tokens.last() in setOf("na", "ना")) tokens.removeAt(tokens.lastIndex)
        if (tokens.isEmpty()) return null
        if (allOf(tokens, NO_CORE, NO_FILLER)) return Answer.No
        return if (allOf(tokens, YES_CORE, YES_FILLER)) Answer.Yes else null
    }
}
