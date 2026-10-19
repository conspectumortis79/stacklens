package com.stacklens.adapter.out.dockertest

import com.stacklens.domain.docker.DockerCommandOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DockerOsHelperCommandResultTest {
    @Test
    fun `CommandResult stores exitCode and output`() {
        val r = DockerCommandOutcome(exitCode = 0, output = "success")
        assertEquals(0, r.exitCode)
        assertEquals("success", r.output)
    }

    @Test
    fun `CommandResult equals and hashCode`() {
        val a = DockerCommandOutcome(0, "ok")
        val b = DockerCommandOutcome(0, "ok")
        val c = DockerCommandOutcome(1, "err")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotNull(a.toString())
    }
}
