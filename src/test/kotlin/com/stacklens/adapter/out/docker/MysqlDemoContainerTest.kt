package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MysqlDemoContainerTest {
    @Test
    fun `root logins try the configured password before the passwordless login`() {
        assertEquals(
            listOf(MysqlRootLogin.Password("rootpassword"), MysqlRootLogin.NoPassword),
            MysqlDemoContainer.rootLogins(),
        )
    }

    @Test
    fun `a root password renders as a mysql cli option while the passwordless login renders as none`() {
        assertEquals("-prootpassword", MysqlRootLogin.Password("rootpassword").cliOption())
        assertNull(MysqlRootLogin.NoPassword.cliOption())
    }

    @Test
    fun `every root login carries a distinct log label`() {
        val labels = MysqlDemoContainer.rootLogins().map { it.logLabel() }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `the demo schema and init script paths point at the initdb resources`() {
        assertEquals("stacklens-demo-mysql", MysqlDemoContainer.NAME)
        assertEquals("auth_db", MysqlDemoContainer.DEMO_DATABASE)
        assertEquals("demo-mysql/initdb/01-schema.sql", MysqlDemoContainer.INIT_SQL_PATH)
    }
}
