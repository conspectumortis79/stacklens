package com.stacklens.application.port.out.docker

data class DemoEnvironmentOutcome(
    val exitCode: Int,
    val output: String,
)

interface DemoEnvironmentPort {
    fun bringUp(timeoutSeconds: Long): DemoEnvironmentOutcome?

    fun bringDown()

    fun ensureMysqlSchema()
}
