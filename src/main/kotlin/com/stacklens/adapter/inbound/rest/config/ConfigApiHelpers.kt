
package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.ConfluenceConfigDto
import com.stacklens.adapter.inbound.rest.DatabaseConfigDto
import com.stacklens.adapter.inbound.rest.GitlabConfigDto
import com.stacklens.adapter.inbound.rest.GrafanaConfigDto
import com.stacklens.adapter.inbound.rest.JiraConfigDto
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import kotlin.reflect.KMutableProperty1

internal fun <CONFIG : Any> loadMutateSave(
    id: Long?,
    find: (Long) -> CONFIG?,
    create: () -> CONFIG,
    secrets: List<Pair<String, KMutableProperty1<CONFIG, String>>>,
    save: (CONFIG) -> CONFIG,
    mutate: CONFIG.() -> Unit,
): CONFIG {
    val entity = (if (id != null && id > 0) find(id) else null) ?: create()
    entity.mutate()
    for ((submitted, secret) in secrets) {
        if (submitted.isNotBlank()) secret.set(entity, submitted)
    }
    return save(entity)
}

internal fun GitlabConfig.toDto() =
    GitlabConfigDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        accessToken = "",
        accessTokenSet = accessToken.isNotBlank(),
        groupId = groupId,
        projectIds = projectIds,
        defaultBranch = defaultBranch,
        active = active,
    )

internal fun GitlabConfig.toExportDto() = GitlabConfigDto(id, name, baseUrl, accessToken, accessToken.isNotBlank(), groupId, projectIds, defaultBranch, active)

internal fun DatabaseConfig.toDto() =
    DatabaseConfigDto(
        id = id,
        name = name,
        dbType = dbType.name,
        host = host,
        port = port,
        databaseName = databaseName,
        username = username,
        password = "",
        passwordSet = password.isNotBlank(),
        schemas = schemas,
        active = active,
    )

internal fun DatabaseConfig.toExportDto() = DatabaseConfigDto(id, name, dbType.name, host, port, databaseName, username, password, password.isNotBlank(), schemas, active)

internal fun ConfluenceConfig.toDto() =
    ConfluenceConfigDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        apiToken = "",
        apiTokenSet = apiToken.isNotBlank(),
        spaceKeys = spaceKeys,
        cloudMode = cloudMode,
        searchRootUrl = searchRootUrl,
        active = active,
    )

internal fun ConfluenceConfig.toExportDto() =
    ConfluenceConfigDto(
        id,
        name,
        baseUrl,
        username,
        apiToken,
        apiToken.isNotBlank(),
        spaceKeys,
        cloudMode,
        searchRootUrl,
        active,
    )

internal fun GrafanaConfig.toDto() =
    GrafanaConfigDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        password = "",
        passwordSet = password.isNotBlank(),
        apiToken = "",
        apiTokenSet = apiToken.isNotBlank(),
        orgId = orgId,
        scanPanelQueries = scanPanelQueries,
        active = active,
    )

internal fun GrafanaConfig.toExportDto() =
    GrafanaConfigDto(
        id,
        name,
        baseUrl,
        username,
        password,
        password.isNotBlank(),
        apiToken,
        apiToken.isNotBlank(),
        orgId,
        scanPanelQueries,
        active,
    )

internal fun JiraConfig.toDto() =
    JiraConfigDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        projectKey = projectKey,
        username = username,
        apiToken = "",
        apiTokenSet = apiToken.isNotBlank(),
        cloudMode = cloudMode,
        commentVisibilityRole = commentVisibilityRole,
        active = active,
    )

internal fun JiraConfig.toExportDto() =
    JiraConfigDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        projectKey = projectKey,
        username = username,
        apiToken = apiToken,
        apiTokenSet = apiToken.isNotBlank(),
        cloudMode = cloudMode,
        commentVisibilityRole = commentVisibilityRole,
        active = active,
    )
