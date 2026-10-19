package com.stacklens.infrastructure.db

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource

class H2AndSchemaUseFailureCoverageTest {
    private var statementsCreated = 0

    private fun proxy(
        vararg interfaces: Class<*>,
        handler: InvocationHandler,
    ): Any = Proxy.newProxyInstance(javaClass.classLoader, interfaces, handler)

    private fun statement(): Any =
        proxy(Statement::class.java) { _, method: Method, _: Array<Any?>? ->
            when (method.name) {
                "execute" -> throw SQLException("the database is already closed")
                "close" -> null
                "toString" -> "scripted-statement"
                "hashCode" -> 11
                "equals" -> false
                else -> false
            }
        }

    private fun connection(): Any =
        proxy(Connection::class.java) { _, method: Method, _: Array<Any?>? ->
            when (method.name) {
                "createStatement" -> {
                    statementsCreated++
                    statement()
                }

                "close" -> null
                "isClosed" -> false
                "toString" -> "scripted-connection"
                "hashCode" -> 22
                "equals" -> false
                else -> false
            }
        }

    private fun driver(): Any =
        proxy(Driver::class.java) { _, method: Method, args: Array<Any?>? ->
            when (method.name) {
                "connect" -> if ((args?.get(0) as? String)?.startsWith(URL_PREFIX) == true) connection() else null
                "acceptsURL" -> (args?.get(0) as? String)?.startsWith(URL_PREFIX) == true
                "toString" -> "scripted-driver"
                "hashCode" -> 33
                "equals" -> false
                else -> false
            }
        }

    @Test
    fun `a failing statement unwinds both use blocks of the shutdown`() {
        val scripted = driver() as Driver
        DriverManager.registerDriver(scripted)
        try {
            val failure =
                assertThrows(SQLException::class.java) {
                    DefaultH2ShutdownExecutor().shutdown("$URL_PREFIX mem", "sa", "")
                }
            assertTrue(failure.message!!.contains("already closed"))
            assertTrue(statementsCreated > 0)
        } finally {
            DriverManager.deregisterDriver(scripted)
        }
    }

    @Test
    fun `a broken statement is logged while the schema upgrade keeps going`() {
        val connection = mock<Connection>()
        val statement = mock<Statement>()
        whenever(connection.createStatement()).thenReturn(statement)
        whenever(statement.execute(any())).thenThrow(SQLException("syntax error"))
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenReturn(connection)

        SchemaUpgradeRunner(dataSource).upgrade()
    }

    @Test
    fun `an error aborts the schema upgrade and unwinds both use blocks`() {
        val connection = mock<Connection>()
        val statement = mock<Statement>()
        whenever(connection.createStatement()).thenReturn(statement)
        whenever(statement.execute(any())).thenAnswer { throw AssertionError("driver is gone") }
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenReturn(connection)

        assertThrows(AssertionError::class.java) {
            SchemaUpgradeRunner(dataSource).upgrade()
        }
    }

    private companion object {
        const val URL_PREFIX = "jdbc:stacklens-scripted:"
    }
}
