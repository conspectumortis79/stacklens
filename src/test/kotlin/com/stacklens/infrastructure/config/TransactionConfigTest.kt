package com.stacklens.infrastructure.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.transaction.PlatformTransactionManager

class TransactionConfigTest {
    private val txManager = mock<PlatformTransactionManager>()
    private val config = TransactionConfig()
    private val port = config.transactionPort(txManager)

    @Test
    fun `afterCommit calls block immediately when no synchronization is active`() {
        var called = false
        port.afterCommit { called = true }
        assertTrue(called)
    }
}
