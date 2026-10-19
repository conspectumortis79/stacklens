package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.sql.SQLException

class DefaultH2ShutdownExecutorTest {
    @Test
    fun `shutdown works against a real in-memory H2 database`() {
        val executor = DefaultH2ShutdownExecutor()
        val conn = java.sql.DriverManager.getConnection("jdbc:h2:mem:ds-shutdown-test;DB_CLOSE_DELAY=-1", "sa", "")
        conn.createStatement().use { it.execute("SELECT 1") }
        assertDoesNotThrow {
            executor.shutdown("jdbc:h2:mem:ds-shutdown-test;DB_CLOSE_DELAY=-1", "sa", "")
        }
    }

    @Test
    fun `shutdown throws when URL is unreachable`() {
        val executor = DefaultH2ShutdownExecutor()
        assertThrows(SQLException::class.java) {
            executor.shutdown("jdbc:h2:tcp://127.0.0.1:1/nonexistent", "sa", "")
        }
    }
}
