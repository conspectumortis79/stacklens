package com.stacklens.adapter.out.demo

object DemoRuntimeMode {
    const val CONTAINER_ENV_VAR: String = "STACKLENS_RUNNING_IN_CONTAINER"

    const val DOCKER_FLAG_PROPERTY: String = "stacklens.running-in-container"

    fun envValue(): String? = System.getenv(CONTAINER_ENV_VAR)

    fun dockerFlag(): Boolean = java.lang.Boolean.getBoolean(DOCKER_FLAG_PROPERTY)

    fun insideContainer(
        envValue: String? = envValue(),
        dockerFlag: Boolean = dockerFlag(),
    ): Boolean = envValue == "true" || dockerFlag
}
