package com.stacklens.adapter.out.jira

data class JiraEndpoints(
    val cloud: List<JiraEndpointSpec> = DEFAULT_CLOUD_ENDPOINTS,
    val server: List<JiraEndpointSpec> = DEFAULT_SERVER_ENDPOINTS,
) {
    companion object {
        const val SEARCH_PATH_V3: String = "/rest/api/3/search/jql"

        const val SEARCH_PATH_V2: String = "/rest/api/2/search"

        const val SEARCH_PATH_V3_LEGACY: String = "/rest/api/3/search"

        const val PROJECT_PATH_V3: String = "/rest/api/3/project"

        val DEFAULT_CLOUD_ENDPOINTS: List<JiraEndpointSpec> =
            listOf(
                JiraEndpointSpec("GET", SEARCH_PATH_V3),
                JiraEndpointSpec("POST", SEARCH_PATH_V3),
                JiraEndpointSpec("GET", SEARCH_PATH_V2),
                JiraEndpointSpec("GET", SEARCH_PATH_V3_LEGACY),
                JiraEndpointSpec("POST", SEARCH_PATH_V3_LEGACY),
                JiraEndpointSpec("POST", SEARCH_PATH_V2),
            )

        val DEFAULT_SERVER_ENDPOINTS: List<JiraEndpointSpec> =
            listOf(
                JiraEndpointSpec("GET", SEARCH_PATH_V2),
                JiraEndpointSpec("POST", SEARCH_PATH_V2),
                JiraEndpointSpec("GET", SEARCH_PATH_V3_LEGACY),
            )
    }
}

data class JiraEndpointSpec(
    val method: String,
    val path: String,
)
