package com.stacklens.testsupport

import com.stacklens.application.port.out.transaction.TransactionPort
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

class FakeTransactionPort : TransactionPort {
    override fun <T> inTransaction(block: () -> T): T = block()

    override fun afterCommit(block: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = block()
                },
            )
        } else {
            block()
        }
    }
}
