package com.opuscube.munshi.brain

import com.opuscube.munshi.api.Facts
import com.opuscube.munshi.api.Sku

// The prompts the server used with Claude, moved to the phone. The guards check every reply.
object Prompts {
    const val REWORD = "Aap ek kirana dukaan ke Soundbox ki awaaz hain. Diye gaye vaakya ko garamjoshi bhare 1-2 chhote Hinglish (Roman script) vaakyon mein dobara likhiye. Har naam, sankhya aur ₹ raashi bilkul wahi rakhiye, aur aakhri sawaal bhi. Pehli line mein Roman script waala vaakya likhiye. Doosri line mein wahi vaakya Devanagari lipi mein likhiye: sankhyayein aur ₹ ankon mein hi rahein, aur naam Roman mein. Sirf ye do lines, aur kuch nahi."

    const val SHELF = "Aap ek kirana dukaan ki shelf ki photo dekh rahe hain. Sirf JSON lautaiye."

    // Short on purpose: every prompt token costs ~20 ms on a mid-range phone.
    fun answer(facts: Facts): String {
        val udhaar = facts.khata.joinToString("; ") { "${it.name} (khataId ${it.khataId}): ₹${it.amount}, ${it.days} din se" }
        val tasks = facts.tasks.take(4).joinToString(" ") { it.line }
        return "Aap Munshi hain, ${facts.shop} ke business partner. Sirf neeche ki jaankari se 1-2 chhote Hinglish (Roman script) vaakyon mein jawab dijiye; koi nayi sankhya mat banaiye. " +
            "Agar dukaandaar kisi grahak ko udhaar yaad dilaana chahe, propose mein uska khataId dijiye, warna null. " +
            "Sirf JSON lautaiye: {\"say\": \"jawab\", \"propose\": {\"kind\": \"udhaar_reminder\", \"khataId\": 2} ya null}\n" +
            "Udhaar: $udhaar (kul ₹${udhaarTotal(facts)}, ${facts.khata.size} grahak)\nIs hafte Munshi ne ₹${facts.week.total} dilaye.\nAaj ke kaam: $tasks"
    }

    fun shelf(items: List<Sku>): String =
        "Yeh ek kirana dukaan ki shelf hai. In cheezon ke liye batao stock kitna dikh raha hai: ${items.joinToString { "${it.sku} (${it.name})" }}. " +
            "Har cheez ke packet ek-ek karke giniye. Jo cheez photo mein kahin nahi hai, use chhod do. Sirf JSON array do, jaise [{\"sku\":\"surf_1kg\",\"count\":2}]."
}
