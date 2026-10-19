package com.stacklens.application.port.out

interface AiJsonRepairerPort {
    fun sanitizeAiJson(s: String): String

    fun repairUnbalancedQuotes(s: String): String

    fun tryRepairTruncatedJson(s: String): String?
}
