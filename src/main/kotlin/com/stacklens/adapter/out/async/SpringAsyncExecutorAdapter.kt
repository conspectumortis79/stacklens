package com.stacklens.adapter.out.async

import com.stacklens.application.port.out.async.AsyncExecutorPort
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.Executor

@Component
class SpringAsyncExecutorAdapter(
    private val executors: Map<String, Executor>,
) : AsyncExecutorPort {
    private val defaultExecutor: TaskExecutor = SimpleAsyncTaskExecutor("async-default-")

    override fun submit(
        executorName: String?,
        task: () -> Unit,
    ) {
        val executor = executorName?.let { executors[it] } ?: defaultExecutor
        executor.execute(task)
    }
}
