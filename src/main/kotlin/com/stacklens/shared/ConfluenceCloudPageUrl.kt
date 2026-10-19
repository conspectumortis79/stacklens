package com.stacklens.shared

internal fun confluenceCloudPageUrl(
    baseUrl: String,
    spaceKey: String,
    pageId: String,
): String {
    val base = baseUrl.trimEnd('/')
    val wikiBase = if (base.contains("/wiki")) base else "$base/wiki"
    return "$wikiBase/spaces/$spaceKey/pages/$pageId"
}
