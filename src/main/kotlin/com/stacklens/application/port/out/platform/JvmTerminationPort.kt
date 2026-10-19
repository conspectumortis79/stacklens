package com.stacklens.application.port.out.platform

fun interface JvmTerminationPort {
    fun halt(status: Int)
}
