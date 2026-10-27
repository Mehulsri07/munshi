package com.opuscube.munshi.brain

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    @Test fun `8 GB phones get E4B, 4 to 6 GB get E2B, smaller phones keep templates`() {
        assertEquals(Models.E4B, Models.forRam(7_400_000_000))      // what a Galaxy A34 8 GB reports
        assertEquals(Models.E2B, Models.forRam(5_600_000_000))      // a 6 GB phone
        assertEquals(Models.E2B, Models.forRam(3_700_000_000))      // a 4 GB phone
        assertNull(Models.forRam(2_800_000_000))
    }

    @Test fun `files are pinned to a revision and a checksum`() {
        for (m in listOf(Models.E4B, Models.E2B)) {
            assertTrue(m.url, Regex("/resolve/[0-9a-f]{40}/").containsMatchIn(m.url))
            assertEquals(64, m.sha256.length)
            assertTrue(m.url.endsWith(m.name))
        }
    }
}
