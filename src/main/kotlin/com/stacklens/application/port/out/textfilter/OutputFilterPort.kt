package com.stacklens.application.port.out.textfilter

import com.stacklens.domain.text.filter.FilterResult

interface OutputFilterPort {
    fun filter(
        command: List<String>,
        stdout: String,
        stderr: String = "",
    ): FilterResult

    fun filterByName(
        specName: String,
        output: String,
    ): FilterResult

    fun knownFilters(): List<String>

    val enabled: Boolean
}
