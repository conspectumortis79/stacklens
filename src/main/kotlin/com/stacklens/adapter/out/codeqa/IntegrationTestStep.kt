package com.stacklens.adapter.out.codeqa

enum class IntegrationTestStep(
    val label: String,
) {
    CLONE_URL("Fetching clone URL"),
    CLONE_BRANCH("Cloning MR branch"),
    COMPOSE_DISCOVERY("Searching for docker-compose.yml"),
    TEST_PLAN("AI analysing affected services"),
    DOCKER_UP("Starting Docker services"),
    RUN_TESTS("Running tests"),
    SUMMARISE("AI summarising results"),
    ;

    val number: Int
        get() = ordinal + 1

    fun progressPrefix(): String = "[$number/${entries.size}]"

    companion object {
        fun startingFrom(step: IntegrationTestStep): List<IntegrationTestStep> = entries.filter { it.number >= step.number }
    }
}
