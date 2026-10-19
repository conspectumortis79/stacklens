package com.stacklens.application.service

import com.stacklens.application.port.out.transaction.TransactionPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FactoryResetServiceTest {
    @Test
    fun `factoryReset runs every handler inside the transaction`() {
        val calls = mutableListOf<String>()
        val handler1 =
            object : FactoryResetHandler {
                override fun reset() {
                    calls.add("h1")
                }
            }
        val handler2 =
            object : FactoryResetHandler {
                override fun reset() {
                    calls.add("h2")
                }
            }
        var txInvoked = false
        val tx =
            object : TransactionPort {
                override fun <T> inTransaction(block: () -> T): T {
                    txInvoked = true
                    return block()
                }

                override fun afterCommit(block: () -> Unit) {
                    block()
                }
            }
        FactoryResetService(listOf(handler1, handler2), tx).factoryReset()
        assertEquals(listOf("h1", "h2"), calls)
        assertEquals(true, txInvoked)
    }

    @Test
    fun `factoryReset is a no-op when there are no handlers`() {
        var txInvoked = false
        val tx =
            object : TransactionPort {
                override fun <T> inTransaction(block: () -> T): T {
                    txInvoked = true
                    return block()
                }

                override fun afterCommit(block: () -> Unit) {
                    block()
                }
            }
        FactoryResetService(emptyList(), tx).factoryReset()
        assertEquals(true, txInvoked)
    }
}
