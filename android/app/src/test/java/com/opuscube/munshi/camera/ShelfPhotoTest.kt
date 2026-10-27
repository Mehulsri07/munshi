package com.opuscube.munshi.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfPhotoTest {
    @Test fun `longest side is capped at 1280 and aspect is kept`() {
        assertEquals(1280 to 960, scaledSize(4000, 3000))
        assertEquals(720 to 1280, scaledSize(2250, 4000))
        assertEquals(800 to 600, scaledSize(800, 600))
    }
}
