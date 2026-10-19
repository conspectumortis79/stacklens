package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.domain.model.JiraConfig
import org.springframework.stereotype.Component

@Component
class DefaultJiraApiVersionResolver : JiraApiVersionResolver {
    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    override fun apiVersion(config: JiraConfig): String {
        return if (config.cloudMode) "3" else "2"
    }

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    override fun searchPath(config: JiraConfig): String {
        return if (config.cloudMode) JiraEndpoints.SEARCH_PATH_V3 else JiraEndpoints.SEARCH_PATH_V2
    }

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    override fun contentSearchPath(config: JiraConfig): String {
        return if (config.cloudMode) "/wiki/rest/api/content/search" else "/rest/api/content/search"
    }

    @Suppress("ktlint:standard:function-signature", "ktlint:standard:function-expression-body")
    override fun issueBasePath(config: JiraConfig): String {
        return if (config.cloudMode) "/rest/api/3/issue" else "/rest/api/2/issue"
    }
}
