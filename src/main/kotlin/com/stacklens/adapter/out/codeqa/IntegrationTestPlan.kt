package com.stacklens.adapter.out.codeqa

data class IntegrationTestPlan(
    val affectedServices: List<String> = emptyList(),
    val testCommands: List<String> = emptyList(),
    val waitSeconds: Int = 20,
    val composeDir: String = "",
    val projectTypes: List<String> = emptyList(),
    val testStrategy: String = "",
    val testTargets: List<String> = emptyList(),
    val reasoning: String = "",
)
