package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.TempDirectoryTrackerPort
import com.stacklens.domain.util.quietly
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Component
open class TempDirectoryTrackerAdapter : TempDirectoryTrackerPort {
    private val trackedTempDirs: MutableSet<File> =
        ConcurrentHashMap.newKeySet()

    init {
        Runtime
            .getRuntime()
            .addShutdownHook(
                Thread {
                    cleanupTrackedTempDirs()
                },
            )
    }

    protected open fun cleanupTrackedTempDirs() {
        trackedTempDirs.forEach { dir ->
            quietly { dir.deleteRecursively() }
        }
    }

    override fun register(dir: File) {
        trackedTempDirs.add(dir)
    }

    override fun unregister(dir: File) {
        trackedTempDirs.remove(dir)
    }
}
