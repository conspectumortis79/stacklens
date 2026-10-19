package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class JvmManagedProcessTest {
    private val process: Process = mock()
    private val otherProcess: Process = mock()

    @Test
    fun `equals compares the wrapped process by identity`() {
        val wrapper = JvmManagedProcess(process)

        assertTrue(wrapper.equals(wrapper))
        assertTrue(wrapper == JvmManagedProcess(process))
        assertFalse(wrapper == JvmManagedProcess(otherProcess))
        assertFalse(wrapper.equals(null))
        assertFalse(wrapper.equals("other"))
    }
}
