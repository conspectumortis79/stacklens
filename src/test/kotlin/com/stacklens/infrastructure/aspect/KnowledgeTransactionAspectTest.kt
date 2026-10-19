package com.stacklens.infrastructure.aspect

import com.stacklens.application.stereotype.ApplicationTransactional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

open class TransactionalProbe {
    @ApplicationTransactional
    open fun annotated(): String = "published"

    @ApplicationTransactional
    open fun failing(): String = throw IllegalStateException("boom")

    open fun unannotated(): String = "plain"
}

class KnowledgeTransactionAspectTest {
    private var started = 0
    private var committed = 0
    private var rolledBack = 0

    private val transactionManager =
        object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
                started++
                return SimpleTransactionStatus()
            }

            override fun commit(status: TransactionStatus) {
                committed++
            }

            override fun rollback(status: TransactionStatus) {
                rolledBack++
            }
        }

    private fun proxy(): TransactionalProbe {
        val factory = AspectJProxyFactory(TransactionalProbe())
        factory.addAspect(KnowledgeTransactionAspect(transactionManager))
        return factory.getProxy()
    }

    @Test
    fun `annotated methods execute the advice inside a committed transaction`() {
        assertEquals("published", proxy().annotated())
        assertEquals(1, started)
        assertEquals(1, committed)
        assertEquals(0, rolledBack)
    }

    @Test
    fun `unannotated methods bypass the advice`() {
        assertEquals("plain", proxy().unannotated())
        assertEquals(0, started)
        assertEquals(0, committed)
        assertEquals(0, rolledBack)
    }

    @Test
    fun `failures propagate and roll back the transaction`() {
        val proxy = proxy()
        assertThrows(IllegalStateException::class.java) { proxy.failing() }
        assertEquals(1, started)
        assertEquals(0, committed)
        assertEquals(1, rolledBack)
    }
}
