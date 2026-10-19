package com.stacklens.adapter.out.shared

import com.stacklens.domain.model.JiraConfig

interface JiraApiVersionResolver {
    fun apiVersion(config: JiraConfig): String

    fun searchPath(config: JiraConfig): String

    fun contentSearchPath(config: JiraConfig): String

    fun issueBasePath(config: JiraConfig): String
}
