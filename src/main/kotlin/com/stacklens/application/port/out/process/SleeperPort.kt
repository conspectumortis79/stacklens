package com.stacklens.application.port.out.process

fun interface SleeperPort {
    fun sleep(millis: Long): Boolean
}
