package com.stacklens.domain.text.filter

data class FilterSpec(
    val name: String,
    val description: String,
    val matchCommand: Regex? = null,
    val matchOutput: List<MatchOutputRule> = emptyList(),
    val stripAnsi: Boolean = true,
    val filterStderr: Boolean = false,
    val stripLinesMatching: List<Regex> = emptyList(),
    val keepLinesMatching: List<Regex> = emptyList(),
    val maxLines: Int? = null,
    val tailLines: Int? = null,
    val truncateLinesAt: Int? = null,
    val onEmpty: String? = null,
) {
    init {
        require(maxLines == null || maxLines >= 0) {
            "FilterSpec '$name': maxLines must be >= 0 (was $maxLines)"
        }
        require(tailLines == null || tailLines >= 0) {
            "FilterSpec '$name': tailLines must be >= 0 (was $tailLines)"
        }
        require(truncateLinesAt == null || truncateLinesAt > 0) {
            "FilterSpec '$name': truncateLinesAt must be > 0 (was $truncateLinesAt)"
        }
    }

    val isWhitelist: Boolean get() = keepLinesMatching.isNotEmpty()
}

data class MatchOutputRule(
    val pattern: Regex,
    val message: String,
    val unless: Regex? = null,
)
