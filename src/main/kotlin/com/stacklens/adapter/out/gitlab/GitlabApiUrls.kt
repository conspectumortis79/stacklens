package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.search.KeywordContext

internal object GitlabApiUrls {
    const val PAGE_SIZE: Int = 100

    const val UNLIMITED_PAGES: Int = Int.MAX_VALUE

    const val TICKET_PROJECT_LIST_MAX_PAGES: Int = 5

    const val CONTEXT_PROJECT_LIST_MAX_PAGES: Int = 50

    const val BLOB_SEARCH_MAX_PAGES: Int = 5

    const val CONTEXT_BLOB_SEARCH_MAX_PAGES: Int = 3

    const val TREE_MAX_PAGES: Int = 20

    const val CONTEXT_TREE_MAX_PAGES: Int = 10

    fun groupProjects(
        groupId: String?,
        page: Int,
    ): String =
        if (groupId != null) {
            "/api/v4/groups/${KeywordContext.encode(groupId)}/projects?include_subgroups=true&per_page=$PAGE_SIZE&page=$page"
        } else {
            "/api/v4/projects?membership=true&per_page=$PAGE_SIZE&page=$page"
        }

    fun repositoryTree(
        projectId: Long,
        branch: String,
        page: Int,
    ): String = "/api/v4/projects/$projectId/repository/tree?recursive=true&per_page=$PAGE_SIZE&page=$page&ref=$branch"

    fun <T> fetchPages(
        maxPages: Int,
        fetchPage: (Int) -> List<T>,
    ): List<T> {
        val all = mutableListOf<T>()
        var page = 1
        while (page <= maxPages) {
            val pageResult = fetchPage(page)
            all += pageResult
            if (pageResult.size < PAGE_SIZE) break
            page++
        }
        return all
    }
}
