package com.stacklens.application.port.out.sql

fun interface H2TestSessionPort {
    fun open(testId: String): H2TestSession
}
