
package com.stacklens.infrastructure.config

import com.stacklens.application.port.out.transaction.TransactionPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class TransactionConfig {
    @Bean
    fun transactionPort(transactionManager: PlatformTransactionManager): TransactionPort {
        val template = TransactionTemplate(transactionManager)
        return object : TransactionPort {
            override fun <T> inTransaction(block: () -> T): T = template.execute { block() }!!

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
    }
}
