package com.stacklens.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.jira.JiraClient
import com.stacklens.adapter.out.jira.JiraConnectionTestAdapter
import com.stacklens.adapter.out.jira.JiraIssueMapper
import com.stacklens.adapter.out.jira.JiraJqlBuilder
import com.stacklens.adapter.out.jira.JiraSprintHttpClient
import com.stacklens.adapter.out.jira.JiraTicketHttpClient
import com.stacklens.application.port.out.JiraConnectionTestPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.port.out.search.JiraSearchPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class JiraAdapterConfig(
    private val objectMapper: ObjectMapper,
    private val stackLensProperties: StackLensProperties,
) {
    @Value("\${webclient.connect-timeout:30000}")
    private val connectTimeoutMs: Int = 30_000

    @Value("\${webclient.read-timeout:180000}")
    private val readTimeoutMs: Int = 180_000

    @Bean
    fun jiraSearchPort(): JiraSearchPort =
        JiraClient(
            objectMapper = objectMapper,
            jqlBuilder = JiraJqlBuilder(),
            issueMapper = JiraIssueMapper(),
            endpoints = stackLensProperties.jira.endpoints,
        )

    @Bean(name = ["jiraTicketFetchPort", "jiraTicketHttpClient"])
    fun jiraTicketHttpClient(): JiraTicketHttpClient = JiraTicketHttpClient(connectTimeoutMs, readTimeoutMs)

    @Bean
    fun jiraConnectionTestPort(
        i18n: I18nPort,
        webClientBuilder: WebClient.Builder,
    ): JiraConnectionTestPort = JiraConnectionTestAdapter(objectMapper, i18n, webClientBuilder)

    @Bean(name = ["jiraSprintFetchPort"])
    fun jiraSprintFetchPort(): JiraSprintFetchPort =
        JiraSprintHttpClient(
            com.stacklens.adapter.out.shared
                .JiraSprintIssueFieldResolver(),
            connectTimeoutMs,
            readTimeoutMs,
        )
}
