package com.stacklens.domain.util

fun exceptionDiagnostic(e: Throwable): String = e.message ?: e.javaClass.simpleName
