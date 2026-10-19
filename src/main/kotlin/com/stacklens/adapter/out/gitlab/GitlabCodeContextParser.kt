package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.search.KeywordContext

class GitlabCodeContextParser {
    fun parseCodeContext(data: String): ParsedContext {
        val lines = data.lines()
        var className: String? = null
        var functionName: String? = null
        for (line in lines) {
            CLASS_DECL.find(line)?.let { className = it.groupValues[1] }
            FUNC_DECL.find(line)?.let { functionName = it.groupValues[1] }
        }
        val resultType =
            when {
                functionName != null -> ResultType.FUNCTION
                className != null -> ResultType.CLASS
                else -> ResultType.FILE
            }
        return ParsedContext(className, functionName, resultType)
    }

    fun findKeywordInContent(
        content: String,
        keyword: String,
    ): List<Pair<Int, String>> = KeywordContext.findKeywordInContent(content, keyword)

    private companion object {
        private val CLASS_DECL = Regex("""\b(?:class|interface|object|enum class|abstract class|data class)\s+(\w+)""")
        private val FUNC_DECL = Regex("""\b(?:fun|def|function|void|public|private|protected|static)\s+(\w+)\s*\(""")
    }
}
