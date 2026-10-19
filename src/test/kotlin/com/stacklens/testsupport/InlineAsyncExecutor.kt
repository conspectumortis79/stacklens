package com.stacklens.testsupport

import com.stacklens.application.port.out.async.AsyncExecutorPort

class InlineAsyncExecutor : AsyncExecutorPort {
    override fun submit(
        executorName: String?,
        task: () -> Unit,
    ) = task()
}
