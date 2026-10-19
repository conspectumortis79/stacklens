package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.ConfluenceDemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeederStrategy
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.adapter.out.demo.GithubDemoDataSeeder
import com.stacklens.adapter.out.demo.GitlabDemoDataSeeder
import com.stacklens.adapter.out.demo.GrafanaDemoDataSeeder
import com.stacklens.adapter.out.demo.JiraDemoDataSeeder
import com.stacklens.adapter.out.demo.McpDemoDataSeeder
import com.stacklens.adapter.out.demo.MysqlDemoDataSeeder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DemoDataSeederHostTest {
    private val hostResolver = DemoHostResolver()
    private val seeder = DemoDataSeeder(emptyList())

    @Test fun `reseed runs every registered strategy`() {
        var seeds = 0
        val strategy =
            object : DemoDataSeederStrategy {
                override fun seed() {
                    seeds++
                }
            }

        DemoDataSeeder(listOf(strategy, strategy)).reseed()

        assertEquals(2, seeds)
    }

    @Test fun `reseeding the outbound adapter runs the registered strategies`() {
        var seeds = 0
        val runner =
            DemoDataSeeder(
                listOf(
                    object : DemoDataSeederStrategy {
                        override fun seed() {
                            seeds++
                        }
                    },
                ),
            )

        runner.reseed()

        assertEquals(1, seeds)
    }

    @Test fun `the resolver rewrites hosts for explicitly supplied runtime flags`() {
        assertEquals(DemoHostResolver.LOCAL_HOST, hostResolver.hostFor(envValue = null, dockerFlag = false))
        assertEquals(DemoHostResolver.DOCKER_HOST, hostResolver.hostFor(envValue = "true", dockerFlag = false))
        assertEquals(DemoHostResolver.DOCKER_HOST, hostResolver.hostFor(envValue = null, dockerFlag = true))
        assertEquals(
            "http://localhost:8080/rest",
            hostResolver.refreshHost("http://host.docker.internal:8080/rest", envValue = null, dockerFlag = false),
        )
        assertEquals(
            "http://example.test",
            hostResolver.refreshHost("http://example.test", envValue = "true", dockerFlag = false),
        )
    }

    @Test fun `the port exposes the ambient host and the mcp base url`() {
        val expected =
            if (System.getenv("STACKLENS_RUNNING_IN_CONTAINER") == "true" ||
                java.lang.Boolean.getBoolean(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
            ) {
                DemoHostResolver.DOCKER_HOST
            } else {
                DemoHostResolver.LOCAL_HOST
            }

        assertEquals(expected, hostResolver.serviceHost())
        assertEquals(expected, hostResolver.alignedHost(DemoHostResolver.DOCKER_HOST))
        assertEquals("http://$expected:${DemoHostResolver.MCP_DEFAULT_PORT}", hostResolver.mcpServiceBaseUrl())
    }

    @Test fun `the legacy seeder list covers every demo technology`() {
        val strategies =
            DemoDataSeederTestHelper.legacySeeders(
                gitlabConfigRepo = mock(),
                jiraConfigRepo = mock(),
                confluenceConfigRepo = mock(),
                grafanaConfigRepo = mock(),
                databaseConfigRepo = mock(),
                mcpConfigRepo = mock(),
                githubRepoConfigRepo = mock(),
                hostResolver = hostResolver,
            )

        assertEquals(7, strategies.size)
        assertEquals(
            listOf(
                GitlabDemoDataSeeder::class,
                GithubDemoDataSeeder::class,
                JiraDemoDataSeeder::class,
                ConfluenceDemoDataSeeder::class,
                GrafanaDemoDataSeeder::class,
                MysqlDemoDataSeeder::class,
                McpDemoDataSeeder::class,
            ),
            strategies.map { it::class },
        )
    }
}
