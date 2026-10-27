package com.opuscube.munshi.voice

import com.opuscube.munshi.conductor.Answer
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentMatcherTest {
    @Test fun yes() {
        for (t in listOf("haan", "Haan ji", "हाँ", "हां जी", "theek hai", "bhej do", "हाँ भेज दो", "हां हां", "ओके", "हाँ,", "Haan, bhej do!", "ok ji", "kar do", "ठीक है जी", "bhej do na", "भेज दो ना"))
            assertEquals(t, Answer.Yes, IntentMatcher.match(t))
    }
    @Test fun no() {
        for (t in listOf("nahi", "नहीं", "rehne do", "नहीं चाहिए", "nahi chahiye", "na na", "mat bhejo", "अभी नहीं", "नहीं।"))
            assertEquals(t, Answer.No, IntentMatcher.match(t))
    }
    @Test fun other() {
        for (t in listOf("kitna udhaar hai?", "haan par kal bhejna", "haan nahi", "pehle Pinky ko bhejo", ""))
            assertEquals(t, null, IntentMatcher.match(t))
    }
}
