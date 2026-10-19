package com.stacklens.adapter.out.jira

class JiraJqlBuilder {
    fun buildJql(
        projectKeys: List<String>,
        keyword: String,
        cloudMode: Boolean = false,
        componentFilters: List<String> = emptyList(),
        labelFilters: List<String> = emptyList(),
    ): String {
        val safe = keyword.replace("\"", "\\\"")

        if (ISSUE_KEY.matches(keyword.trim())) {
            return "key = \"${safe}\" ORDER BY updated DESC"
        }

        val textClause = "(text ~ \"$safe\" OR component = \"$safe\" OR labels = \"$safe\")"
        val conditions = mutableListOf<String>()
        val comps = componentFilters.map { it.trim() }.filter { it.isNotBlank() }
        when {
            comps.size == 1 -> conditions.add("component = \"${comps[0]}\"")
            comps.size > 1 -> conditions.add("component in (${comps.joinToString(",") { "\"$it\"" }})")
        }

        val labs = labelFilters.map { it.trim() }.filter { it.isNotBlank() }
        when {
            labs.size == 1 -> conditions.add("labels = \"${labs[0]}\"")
            labs.size > 1 -> conditions.add("labels in (${labs.joinToString(",") { "\"$it\"" }})")
        }

        conditions.add(textClause)
        return "${conditions.joinToString(" AND ")} ORDER BY updated DESC"
    }

    fun projectKeyList(projectKeys: String): List<String> = projectKeys.split(",").map { it.trim() }.filter { it.isNotBlank() }

    private companion object {
        private val ISSUE_KEY = Regex("^[A-Z][A-Z0-9]*-[0-9]+$")
    }
}
