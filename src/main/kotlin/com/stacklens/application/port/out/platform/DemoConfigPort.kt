package com.stacklens.application.port.out.platform

interface DemoConfigPort {
    fun <T> apply(
        configs: List<T>,
        nameOf: (T) -> String,
    ): List<T>
}
