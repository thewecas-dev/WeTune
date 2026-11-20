package com.wetune

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyTotpTest {
    // RFC 6238's SHA-1 test key ("12345678901234567890") in base32; its 8-digit codes end in these 6 digits
    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test fun matchesTheStandardsExamples() {
        assertEquals("287082", SpotifyCanvas.totp(secret, 59))
        assertEquals("081804", SpotifyCanvas.totp(secret, 1111111109))
        assertEquals("050471", SpotifyCanvas.totp(secret, 1111111111))
    }

    @Test fun base32IgnoresPaddingAndCase() {
        assertEquals("12345678901234567890", String(SpotifyCanvas.base32(secret.lowercase() + "==")))
    }
}
