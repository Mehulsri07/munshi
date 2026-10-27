package com.opuscube.munshi.pairing

import org.junit.Assert.*
import org.junit.Test

class ConfigTest {
    @Test fun `parses the control page QR and trims the trailing slash`() {
        assertEquals(Config("https://abc.trycloudflare.com", "k9"), parseConfig("""{"url":"https://abc.trycloudflare.com/","key":"k9"}"""))
    }
    @Test fun `accepts an empty key`() {
        assertEquals(Config("http://10.0.2.2:3000", ""), parseConfig("""{"url":"http://10.0.2.2:3000","key":""}"""))
    }
    @Test fun `rejects anything that is not a Munshi pairing code`() {
        assertNull(parseConfig("hello"))
        assertNull(parseConfig("""{"url":"ftp://x"}"""))
        assertNull(parseConfig("""{"key":"k"}"""))
    }
}
