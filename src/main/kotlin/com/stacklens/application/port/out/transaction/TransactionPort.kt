package com.stacklens.application.port.out.transaction

interface TransactionPort {
    fun <T> inTransaction(block: () -> T): T

    fun afterCommit(block: () -> Unit)
}
