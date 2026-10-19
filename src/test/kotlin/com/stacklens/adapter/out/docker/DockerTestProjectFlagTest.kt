package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockerTestProjectFlagTest {
    @Test
    fun `TESTDOCKER_PROJECT_NAME is a stable lowercased identifier that does not collide with default docker project name`() {
        assertEquals("stacklens-testdocker", TESTDOCKER_PROJECT_NAME)
        assertNotEquals("docker", TESTDOCKER_PROJECT_NAME)
        assertTrue(TESTDOCKER_PROJECT_NAME.matches(Regex("^[a-zA-Z0-9_-]+$")))
    }

    @Test
    fun `testdockerProjectFlag returns the canonical -p name flag pair`() {
        val flag = testdockerProjectFlag()
        assertEquals(listOf("-p", TESTDOCKER_PROJECT_NAME), flag)
    }
}
