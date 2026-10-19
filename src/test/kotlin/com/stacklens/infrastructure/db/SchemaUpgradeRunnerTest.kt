package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.annotation.Lazy
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

class SchemaUpgradeRunnerTest {
    @Test
    fun `upgrade runs all statements successfully`() {
        val dataSource = mock<DataSource>()
        val conn = mock<Connection>()
        val stmt = mock<Statement>()
        whenever(dataSource.connection).thenReturn(conn)
        whenever(conn.createStatement()).thenReturn(stmt)
        whenever(stmt.execute(any())).thenReturn(true)
        val runner = SchemaUpgradeRunner(dataSource)
        runner.upgrade()
    }

    @Test
    fun `upgrade swallows exceptions from a failing statement`() {
        val dataSource = mock<DataSource>()
        val conn = mock<Connection>()
        val stmt = mock<Statement>()
        whenever(dataSource.connection).thenReturn(conn)
        whenever(conn.createStatement()).thenReturn(stmt)
        whenever(stmt.execute(any())).thenThrow(RuntimeException("boom"))
        val runner = SchemaUpgradeRunner(dataSource)
        runner.upgrade()
    }

    @Test
    fun `upgrade swallows exceptions from getConnection`() {
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenThrow(RuntimeException("no connection"))
        val runner = SchemaUpgradeRunner(dataSource)
        runner.upgrade()
        assertNotNull(runner)
    }

    @Test
    fun `runner is eagerly initialized so the migration actually runs on startup`() {
        val lazy = SchemaUpgradeRunner::class.java.getAnnotation(Lazy::class.java)
        assertNotNull(lazy, "@Lazy annotation is required to override the project's lazy-initialization")
        assertNotEquals(
            true,
            lazy.value,
            "@Lazy must be set to `false` (or no value), otherwise the migration " +
                "won't run because nothing else references this bean",
        )
    }
}
