package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig

fun contentSearchPath(config: ConfluenceConfig): String = if (config.cloudMode) "/wiki/rest/api/content/search" else "/rest/api/content/search"
