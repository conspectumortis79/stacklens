package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.confluence.ConfluenceClient
import com.stacklens.adapter.out.confluence.ConfluenceConnectionTestAdapter
import com.stacklens.adapter.out.github.GithubConnectionTestAdapter
import com.stacklens.adapter.out.github.GithubSearchAdapter
import com.stacklens.adapter.out.github.GithubTicketBridgeAdapter
import com.stacklens.adapter.out.gitlab.ContextMapAdapter
import com.stacklens.adapter.out.gitlab.GitlabClient
import com.stacklens.adapter.out.gitlab.GitlabConnectionTestAdapter
import com.stacklens.adapter.out.gitlab.GitlabMrAdapter
import com.stacklens.adapter.out.gitlab.GitlabTicketBridgeAdapter
import com.stacklens.adapter.out.gitlab.PipelineSearchAdapter
import com.stacklens.adapter.out.grafana.GrafanaClient
import com.stacklens.adapter.out.grafana.GrafanaConnectionTestAdapter
import com.stacklens.adapter.out.shared.MrFetchAdapter
import com.stacklens.adapter.out.shared.MrSuggestionAdapter
import com.stacklens.application.port.out.ConfluenceConnectionTestPort
import com.stacklens.application.port.out.DiffHunkParserPort
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConnectionTestPort
import com.stacklens.application.port.out.GitlabTicketBridgePort
import com.stacklens.application.port.out.GrafanaConnectionTestPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.mr.GitlabMrPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.port.out.search.ContextMapSearchPort
import com.stacklens.application.port.out.search.GithubSearchPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.application.port.out.search.PipelineSearchPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientAdapterConfig(
    private val webClientBuilder: WebClient.Builder,
) {
    @Bean
    fun gitlabPort(): GitlabPort = GitlabClient()

    @Bean
    fun gitlabTicketBridgePort(): GitlabTicketBridgePort = GitlabTicketBridgeAdapter()

    @Bean
    fun gitlabMrPort(): GitlabMrPort = GitlabMrAdapter()

    @Bean
    fun pipelineSearchPort(): PipelineSearchPort = PipelineSearchAdapter()

    @Bean
    fun contextMapSearchPort(): ContextMapSearchPort = ContextMapAdapter()

    @Bean
    fun confluenceSearchPort(): ConfluenceSearchPort = ConfluenceClient()

    @Bean
    fun grafanaSearchPort(): GrafanaSearchPort = GrafanaClient()

    @Bean
    fun githubSearchPort(): GithubSearchPort = GithubSearchAdapter(webClientBuilder)

    @Bean
    fun githubTicketBridgePort(githubRepoConfigPort: GithubRepoConfigPort): GithubTicketBridgePort = GithubTicketBridgeAdapter(githubRepoConfigPort)

    @Bean
    fun mrFetchPort(diffHunkParser: DiffHunkParserPort): MrFetchPort = MrFetchAdapter(diffHunkParser = diffHunkParser)

    @Bean
    fun mrSuggestionPort(): MrSuggestionPort = MrSuggestionAdapter()

    @Bean
    fun githubConnectionTestPort(i18n: I18nPort): GithubConnectionTestPort = GithubConnectionTestAdapter(webClientBuilder, i18n)

    @Bean
    fun confluenceConnectionTestPort(
        i18n: I18nPort,
        webClientBuilder: WebClient.Builder,
    ): ConfluenceConnectionTestPort = ConfluenceConnectionTestAdapter(i18n, webClientBuilder)

    @Bean
    fun gitlabConnectionTestPort(
        i18n: I18nPort,
        webClientBuilder: WebClient.Builder,
        httpErrorMessageResolver: com.stacklens.application.port.out.HttpErrorMessageResolverPort,
    ): GitlabConnectionTestPort = GitlabConnectionTestAdapter(i18n, webClientBuilder, httpErrorMessageResolver)

    @Bean
    fun grafanaConnectionTestPort(
        i18n: I18nPort,
        webClientBuilder: WebClient.Builder,
        httpErrorMessageResolver: com.stacklens.application.port.out.HttpErrorMessageResolverPort,
    ): GrafanaConnectionTestPort = GrafanaConnectionTestAdapter(i18n, webClientBuilder, httpErrorMessageResolver)

    @Bean("gitlabPortFactory")
    fun gitlabPortFactory(): java.util.function.Function<WebClient.Builder, GitlabPort> = java.util.function.Function { GitlabClient() }

    @Bean("gitlabTicketBridgePortFactory")
    fun gitlabTicketBridgePortFactory(): java.util.function.Function<WebClient.Builder, GitlabTicketBridgePort> = java.util.function.Function { GitlabTicketBridgeAdapter() }
}
