package com.stacklens.application.port.out.textfilter

interface TextFilterConfigPort {
    val enabled: Boolean

    fun setEnabled(enabled: Boolean)
}
