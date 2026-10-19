package com.stacklens.desktop

import com.stacklens.shared.exitJvm
import com.stacklens.shared.haltImmediately
import com.stacklens.shared.haltJvm
import java.awt.Desktop

object ExitSeamProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        dumpCoverageBeforeHaltingJvm()
        when (args.first()) {
            "app-lifecycle-halt" -> haltJvm(4)

            "reporter-exit" -> exitJvm(5)

            "shutdown-sleep" -> {
                invokeSeam("com.stacklens.desktop.DesktopShutdownHandler", "_init_\$lambda\$1", 3L)
                println("slept")
            }

            "shutdown-halt" -> haltImmediately()

            "quit-sleep" -> {
                invokeSeam("com.stacklens.desktop.DesktopQuitCoordinator", "_init_\$lambda\$2", 3L)
                println("slept")
            }

            "quit-halt" -> haltImmediately()

            "quit-exitfx" -> {
                javafx.application.Platform.startup { }
                invokeSeam("com.stacklens.desktop.DesktopQuitCoordinator", "_init_\$lambda\$0")
                println("fx-exited")
                finishProbeCase()
            }

            "mac-dock-icon" -> {
                setMacDockIcon(
                    osName = "Mac OS X",
                    resourceStream = { java.io.ByteArrayInputStream(ByteArray(8)) },
                    imageReader = {
                        java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    },
                )
                println("dock-icon-set")
                finishProbeCase()
            }

            "browser-desktop" -> {
                val desktop = invokeSeam("com.stacklens.desktop.BrowserLauncher", "_init_\$lambda\$0")
                println("desktop=${desktop is Desktop}")
                finishProbeCase()
            }

            else -> error("unknown case ${args.first()}")
        }
    }

    private fun finishProbeCase() {
        dumpCoverageBeforeHaltingJvm()
        haltImmediately()
    }

    fun dumpCoverageBeforeHaltingJvm() {
        val report =
            runCatching {
                val runtime = Class.forName("org.jacoco.agent.rt.RT")
                val agent = checkNotNull(runtime.getMethod("getAgent").invoke(null)) { "no agent instance" }
                Class
                    .forName("org.jacoco.agent.rt.IAgent")
                    .getMethod("dump", Boolean::class.javaPrimitiveType)
                    .invoke(agent, false)
                "dumped-with-${agent.javaClass.name}"
            }.fold({ it }, { "dump-failed: $it" })
        println(report)
    }

    private fun invokeSeam(
        owner: String,
        method: String,
        argument: Any? = null,
    ): Any? {
        val candidate =
            Class
                .forName(owner)
                .declaredMethods
                .firstOrNull { it.name == method }
                ?: error("no seam $owner.$method")
        candidate.isAccessible = true
        return if (candidate.parameterCount == 0) {
            candidate.invoke(null)
        } else {
            candidate.invoke(null, argument)
        }
    }
}
