package com.stacklens.application.service.pipeline

class StageOutputs {
    private val values = LinkedHashMap<String, String>()

    fun record(
        stage: String,
        output: String?,
    ) {
        if (output == null) values.remove(stage) else values[stage] = output
    }

    fun of(stage: String): String = values[stage].orEmpty()

    fun optional(stage: String): String? = values[stage]

    fun valuesFor(stages: Iterable<String>): List<String> = stages.map { of(it) }

    fun asMap(): Map<String, String> = values.toMap()
}
