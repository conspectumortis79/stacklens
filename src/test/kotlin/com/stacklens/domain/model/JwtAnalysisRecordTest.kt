package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JwtAnalysisRecordTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val rec = JwtAnalysisRecord(token = "abc.def.ghi", status = "VALID")
        assertEquals(0L, rec.id)
        assertEquals("abc.def.ghi", rec.token)
        assertEquals(null, rec.jwtId)
        assertEquals(null, rec.audience)
        assertEquals(null, rec.issuer)
        assertEquals(null, rec.subject)
        assertEquals("VALID", rec.status)
    }

    @Test
    fun `named constructor populates all fields`() {
        val rec =
            JwtAnalysisRecord(
                id = 1L,
                token = "x.y.z",
                jwtId = "jti-1",
                audience = "aud-1",
                issuer = "iss-1",
                subject = "sub-1",
                status = "EXPIRED",
            )
        assertEquals(1L, rec.id)
        assertEquals("jti-1", rec.jwtId)
        assertEquals("aud-1", rec.audience)
        assertEquals("iss-1", rec.issuer)
        assertEquals("sub-1", rec.subject)
        assertEquals("EXPIRED", rec.status)
    }
}
