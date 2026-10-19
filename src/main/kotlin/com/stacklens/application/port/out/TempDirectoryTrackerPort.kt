package com.stacklens.application.port.out

import java.io.File

interface TempDirectoryTrackerPort {
    fun register(dir: File)

    fun unregister(dir: File)
}
