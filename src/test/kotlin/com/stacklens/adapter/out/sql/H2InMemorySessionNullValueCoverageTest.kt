package com.stacklens.adapter.out.sql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement

class H2InMemorySessionNullValueCoverageTest {
    private fun session(connection: Connection): Any {
        val type = Class.forName("com.stacklens.adapter.out.sql.H2InMemoryTestSessionAdapter\$H2InMemoryTestSession")
        val constructor = type.declaredConstructors.first()
        constructor.isAccessible = true
        return constructor.newInstance(connection)
    }

    private fun runQuery(
        target: Any,
        sql: String,
        maxRows: Int,
    ): Any? {
        val method =
            target.javaClass
                .getDeclaredMethod(
                    "runQuery",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).apply { isAccessible = true }
        return method.invoke(target, sql, maxRows, 5)
    }

    private fun field(
        target: Any,
        name: String,
    ): Any? {
        val getter = target.javaClass.getDeclaredMethod("get${name.replaceFirstChar { it.uppercase() }}")
        getter.isAccessible = true
        return getter.invoke(target)
    }

    @Test
    fun `a statement without columns produces an empty grid`() {
        val statement = mock<Statement>()
        val resultSet = mock<ResultSet>()
        val meta = mock<ResultSetMetaData>()
        whenever(meta.columnCount).thenReturn(0)
        whenever(resultSet.metaData).thenReturn(meta)
        whenever(resultSet.next()).thenReturn(false)
        whenever(statement.executeQuery(any())).thenReturn(resultSet)
        val connection = mock<Connection>()
        whenever(connection.createStatement()).thenReturn(statement)

        val result = runQuery(session(connection), "anything", 10)!!

        assertEquals(emptyList<Any>(), field(result, "columns"))
        assertEquals(emptyList<Any>(), field(result, "rows"))
        assertEquals(false, field(result, "truncated"))
    }

    @Test
    fun `a cell without a value renders as no text`() {
        val statement = mock<Statement>()
        val resultSet = mock<ResultSet>()
        val meta = mock<ResultSetMetaData>()
        whenever(meta.columnCount).thenReturn(2)
        whenever(meta.getColumnLabel(org.mockito.Mockito.anyInt())).thenReturn("A", "B")
        whenever(resultSet.metaData).thenReturn(meta)
        whenever(resultSet.next()).thenReturn(true, false)
        whenever(resultSet.getObject(org.mockito.Mockito.anyInt())).thenReturn(null, "text")
        val connection = mock<Connection>()
        whenever(connection.createStatement()).thenReturn(statement)
        whenever(statement.executeQuery(anyOrNull())).thenReturn(resultSet)

        val result = runQuery(session(connection), "select a, b from t", 10)!!

        val rows = field(result, "rows") as List<*>

        @Suppress("UNCHECKED_CAST")
        val first = rows.first() as List<String?>
        assertEquals(listOf(null, "text"), first)
        assertEquals(2, (field(result, "columns") as List<*>).size)
        assertTrue(rows.size == 1)
    }
}
