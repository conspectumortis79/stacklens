package com.stacklens.adapter.out.textfilter

import com.stacklens.domain.text.filter.FilterSpec
import com.stacklens.domain.text.filter.FilterTestCase

internal fun TomlFilterParser.parseOrFail(
    content: String,
    fileName: String,
): Pair<FilterSpec, List<FilterTestCase>> {
    val parsed = parse(content, fileName)
    val spec = parsed.spec ?: throw TomlParseException("$fileName: no [filters.*] section found")
    return spec to parsed.tests
}
