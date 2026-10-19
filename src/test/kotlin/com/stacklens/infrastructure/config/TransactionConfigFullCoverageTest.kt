package com.stacklens.infrastructure.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

class TransactionConfigFullCoverageTest {
    private var dataSource: DataSource? = null
    private var txManager: DataSourceTransactionManager? = null

    @BeforeEach
    fun setUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        (dataSource as? AutoCloseable)?.close()
    }

    @Test
    fun `inTransaction executes the block through the TransactionTemplate`() {
        val ds =
            EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build()
        dataSource = ds
        val txm = DataSourceTransactionManager(ds)
        txManager = txm
        val cfg = TransactionConfig()
        val port = cfg.transactionPort(txm)
        val result = port.inTransaction { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `afterCommit defers the block when synchronization is active`() {
        val ds =
            EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build()
        dataSource = ds
        val txm = DataSourceTransactionManager(ds)
        txManager = txm
        val cfg = TransactionConfig()
        val port = cfg.transactionPort(txm)
        val template = TransactionTemplate(txm)
        var blockRan = false
        template.execute {
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive())
            port.afterCommit { blockRan = true }
            assertFalse(blockRan)
        }
        assertTrue(blockRan)
    }

    @Test
    fun `afterCommit calls block immediately when no synchronization is active`() {
        val txm = mockTxManager()
        val cfg = TransactionConfig()
        val port = cfg.transactionPort(txm)
        var called = false
        port.afterCommit { called = true }
        assertTrue(called)
    }

    private fun mockTxManager(): DataSourceTransactionManager {
        val ds =
            EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build()
        dataSource = ds
        val txm =
            object : DataSourceTransactionManager(ds) {
                override fun doCommit(status: DefaultTransactionStatus) {
                }
            }
        txManager = txm
        return txm
    }

    @Test
    fun `transactionPort returns a non-null TransactionPort`() {
        val cfg = TransactionConfig()
        val ds =
            EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build()
        dataSource = ds
        val port = cfg.transactionPort(DataSourceTransactionManager(ds))
        assertNotNull(port)
    }
}
