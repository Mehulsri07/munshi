package com.opuscube.munshi.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoTest {
    private val pinky = Line("Pinky didi ka ₹230 udhaar 9 din se baaki hai. Reminder bhej doon?", "Pinky didi का ₹230 उधार 9 दिन से बाकी है। रिमाइंडर भेज दूँ?")

    @Test fun `the recogniser picking up Munshi's own question is an echo`() {
        assertTrue(isEcho("शादी का 230 रुपए उधार 9 दिन से बाकी है रिमाइंडर", pinky))     // seen on the Galaxy A34
        assertTrue(isEcho("pinky didi ka 230 udhaar 9 din se baaki hai", pinky))
    }

    @Test fun `real answers and questions are not echoes`() {
        assertFalse(isEcho("haan", pinky))
        assertFalse(isEcho("हाँ भेज दो", pinky))
        assertFalse(isEcho("nahi", pinky))
        assertFalse(isEcho("kitna udhaar baaki hai", pinky))
        assertFalse(isEcho("पहले शर्मा जी को भेजो", pinky))
        assertFalse(isEcho("Pinky didi ka udhaar kitna baaki hai", pinky))     // her name, but her own question
    }
}
