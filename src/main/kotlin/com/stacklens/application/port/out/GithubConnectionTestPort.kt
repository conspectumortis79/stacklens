
package com.stacklens.application.port.out

import com.stacklens.domain.model.GithubRepoConfig

interface GithubConnectionTestPort {
    sealed class TestResult {
        data class Success(
            val message: String,
        ) : TestResult()

        data class Failure(
            val message: String,
        ) : TestResult()
    }

    fun testConnection(
        config: GithubRepoConfig,
        token: String,
        baseUrl: String,
    ): TestResult
}
