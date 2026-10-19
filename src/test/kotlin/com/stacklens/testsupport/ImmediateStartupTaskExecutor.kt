package com.stacklens.testsupport

import com.stacklens.application.port.out.async.StartupTaskExecutor

object ImmediateStartupTaskExecutor : StartupTaskExecutor {
    override fun submit(task: () -> Unit) {
        task()
    }
}
