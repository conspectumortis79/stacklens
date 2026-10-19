package com.stacklens.application.service

import com.stacklens.domain.text.AgentTextUtils.ParsedFile

internal fun AgentOutputPersister.filterSafeFiles(files: List<ParsedFile>): List<ParsedFile> = files.filter { isSafeRelativePath(it.path) }

internal fun AgentOutputPersister.filterSafeDeletionPaths(paths: List<String>): List<String> = paths.filter { isSafeRelativePath(it) }
