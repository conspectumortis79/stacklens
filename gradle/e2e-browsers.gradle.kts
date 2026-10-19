import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class ExpectedBinary(
    val binaryPaths: List<String>,
    val executableNames: List<String> = emptyList(),
)

tasks.register("installE2eBrowsers") {
    group = "verification"
    description = "Download the Playwright browser binaries the pinned Playwright version expects (chromium, headless shell) into the Playwright cache"
    dependsOn(tasks.named("npmInstall"))

    val osName = System.getProperty("os.name", "").lowercase()
    val isMac = osName.contains("mac") || osName.contains("darwin") || osName.contains("osx")
    val isLinux = osName.contains("linux")
    if (!isMac && !isLinux) {
        throw GradleException(
            "installE2eBrowsers: unsupported OS '$osName'. " +
                "StackLens Playwright E2E tests are only supported on macOS and Linux.",
        )
    }

    val defaultCacheDir =
        if (isMac) {
            "${System.getProperty("user.home")}/Library/Caches/ms-playwright"
        } else {
            "${System.getProperty("user.home")}/.cache/ms-playwright"
        }
    val cacheDir = System.getenv("PLAYWRIGHT_BROWSERS_PATH")?.takeIf { it.isNotBlank() } ?: defaultCacheDir

    val runDir = project.projectDir.absolutePath

    val nodeWorkDir =
        layout
            .buildDirectory
            .dir("nodejs")
            .get()
            .asFile
            .absolutePath

    val browserExecutableNames: Map<String, List<String>> =
        mapOf(
            "chromium" to listOf("Google Chrome for Testing", "Chromium", "chrome"),
            "chromium-headless-shell" to listOf("chrome-headless-shell", "headless_shell"),
        )

    val expected: Map<String, ExpectedBinary> =
        if (isMac) {
            mapOf(
                "chromium" to
                    ExpectedBinary(
                        binaryPaths =
                            listOf(
                                "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing",
                                "chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing",
                                "chrome-mac/Chromium.app/Contents/MacOS/Chromium",
                            ),
                        executableNames = browserExecutableNames.getValue("chromium"),
                    ),
                "chromium-headless-shell" to
                    ExpectedBinary(
                        binaryPaths = emptyList(),
                        executableNames = browserExecutableNames.getValue("chromium-headless-shell"),
                    ),
            )
        } else {
            mapOf(
                "chromium" to
                    ExpectedBinary(
                        binaryPaths =
                            listOf(
                                "chrome-linux64/chrome",
                                "chrome-linux/chrome",
                            ),
                        executableNames = browserExecutableNames.getValue("chromium"),
                    ),
                "chromium-headless-shell" to
                    ExpectedBinary(
                        binaryPaths = emptyList(),
                        executableNames = browserExecutableNames.getValue("chromium-headless-shell"),
                    ),
            )
        }

    fun clearStaleLock() {
        val lock = File(cacheDir, "__dirlock")
        if (!lock.exists()) return
        val inUse =
            try {
                ProcessBuilder("pgrep", "-f", "playwright (install|core)")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0
            } catch (e: Exception) {
                logger.lifecycle("installE2eBrowsers: pgrep unavailable (${e.message}); treating lock as stale.")
                false
            }
        if (inUse) {
            throw GradleException(
                "installE2eBrowsers: $cacheDir/__dirlock exists and a playwright process is " +
                    "currently running. Wait for it to finish or stop the running process, then retry.",
            )
        }
        logger.lifecycle("installE2eBrowsers: removing stale Playwright cache lock at $lock")
        lock.deleteRecursively()
    }

    var lastProbeIssue: String? = null

    fun cacheDirPrefix(browser: String): String =
        when (browser) {
            "chromium-headless-shell" -> "chromium_headless_shell"
            else -> browser
        }

    fun pinnedRevision(browser: String): String? {
        val browsersJson = File(runDir, "node_modules/playwright-core/browsers.json")
        if (!browsersJson.isFile) {
            lastProbeIssue =
                "${browsersJson.absolutePath} is missing - the Playwright packages are not installed " +
                    "(run './gradlew npmInstall' or 'npm ci')."
            return null
        }
        val json = browsersJson.readText()
        val revision =
            Regex("\"name\"\\s*:\\s*\"$browser\"\\s*,\\s*\"revision\"\\s*:\\s*\"(\\d+)\"")
                .find(json)
                ?.groupValues
                ?.get(1)
        if (revision == null) {
            lastProbeIssue = "${browsersJson.absolutePath} has no entry for '$browser'."
        }
        return revision
    }

    fun pinnedCacheDirName(browser: String): String? {
        val revision = pinnedRevision(browser) ?: return null
        return "${cacheDirPrefix(browser)}-$revision"
    }

    fun findBrowserBinary(browser: String): File? {
        val e = expected[browser] ?: return null
        val cacheRoot = File(cacheDir)
        val requiredDir = pinnedCacheDirName(browser) ?: return null
        val pinnedRoot = File(cacheRoot, requiredDir)
        if (!pinnedRoot.isDirectory) {
            val prefix = cacheDirPrefix(browser)
            val present =
                cacheRoot
                    .listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("$prefix-") }
                    ?.joinToString(", ") { it.name }
                    .orEmpty()
            val message =
                "$cacheDir holds ${if (present.isEmpty()) "no $prefix-* directory" else present} but the pinned " +
                    "Playwright expects $requiredDir - a cache from another Playwright version cannot be launched"
            logger.lifecycle("installE2eBrowsers: $message.")
            lastProbeIssue = message
            return null
        }
        val byPath =
            e.binaryPaths
                .map { File(pinnedRoot, it) }
                .filter { it.isFile }
        val found =
            byPath.firstOrNull()
                ?: pinnedRoot
                    .walkTopDown()
                    .firstOrNull { it.isFile && it.name in e.executableNames }
        if (found == null) {
            val message =
                "$pinnedRoot exists but contains none of: ${e.binaryPaths.joinToString(", ")}" +
                    " nor any executable named ${e.executableNames.joinToString(", ")}"
            logger.lifecycle("installE2eBrowsers: $message.")
            lastProbeIssue = message
            return null
        }
        lastProbeIssue = null
        return found
    }

    fun probeLaunch(probe: File): Pair<Boolean, String> =
        try {
            val collected = StringBuilder()
            val process =
                ProcessBuilder(probe.absolutePath, "--version")
                    .redirectErrorStream(true)
                    .start()
            val drain =
                Thread(
                    {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            synchronized(collected) { collected.appendLine(line.trim()) }
                        }
                    },
                    "playwright-probe-reader",
                ).apply {
                    isDaemon = true
                    start()
                }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                drain.join(1_000)
                false to "timed out after 10s"
            } else {
                drain.join(1_000)
                val tail =
                    synchronized(collected) {
                        collected.toString().trim().lines().filter { it.isNotBlank() }.take(3).joinToString(" | ")
                    }
                (process.exitValue() == 0) to
                    "exited with code ${process.exitValue()}${if (tail.isEmpty()) "" else ": $tail"}"
            }
        } catch (e: Exception) {
            false to "could not be started (${e.message})"
        }

    fun isPlaywrightInstalledOk(browser: String): Boolean {
        val probe = findBrowserBinary(browser) ?: return false
        val (ok, detail) = probeLaunch(probe)
        if (!ok) {
            lastProbeIssue = "${probe.absolutePath} exists but $detail"
            logger.lifecycle("installE2eBrowsers: $browser is not launchable - ${lastProbeIssue}.")
        }
        return ok
    }

    fun resolvePinnedNode(): Triple<File, File, String>? {
        val roots =
            listOf(File(nodeWorkDir), File(runDir, "build/nodejs"))
                .filter { it.isDirectory }
        val nodeDirs =
            roots
                .flatMap { root -> root.listFiles()?.toList().orEmpty() }
                .filter { it.isDirectory && it.name.startsWith("node-") }
                .sortedByDescending { it.lastModified() }
        for (dir in nodeDirs) {
            val bin = File(dir, "bin")
            val node = File(bin, "node")
            if (node.isFile && node.canExecute()) return Triple(node, bin, "build-pinned Node")
        }
        return null
    }

    fun versionOf(nodeBinary: File): String =
        try {
            val out =
                ProcessBuilder(nodeBinary.absolutePath, "--version")
                    .redirectErrorStream(true)
                    .start()
            val text = out.inputStream.bufferedReader().readText().trim()
            out.waitFor(10, TimeUnit.SECONDS).let { if (it) text else "<unknown>" }
        } catch (e: Exception) {
            "<unknown: ${e.message}>"
        }

    fun installOneBrowser(browser: String) {
        val cli =
            listOf("node_modules/playwright/cli.js", "node_modules/playwright-core/cli.js")
                .map { File(runDir, it) }
                .firstOrNull { it.isFile }
        val pinned = resolvePinnedNode()
        val command: List<String>
        val pathBinDir: File?
        var runnerLabel: String
        when {
            pinned != null && cli != null -> {
                command = listOf(pinned.first.absolutePath, cli.absolutePath, "install", browser)
                pathBinDir = pinned.second
                runnerLabel = "${pinned.third} ${versionOf(pinned.first)}"
            }
            pinned != null -> {
                val npx = File(pinned.second, "npx")
                command =
                    if (npx.isFile && npx.canExecute()) {
                        listOf(npx.absolutePath, "playwright", "install", browser)
                    } else {
                        listOf("npx", "playwright", "install", browser)
                    }
                pathBinDir = pinned.second
                runnerLabel = "${pinned.third} ${versionOf(pinned.first)} via npx"
            }
            else -> {
                command = listOf("npx", "playwright", "install", browser)
                pathBinDir = null
                runnerLabel = "npx from PATH"
                logger.lifecycle(
                    "installE2eBrowsers: WARNING - $nodeWorkDir is empty (nodeSetup has not run?), falling back to " +
                        "'npx' from PATH. Playwright's unzip step deadlocks on Node >= 25; if this hangs at 100%, " +
                        "run './gradlew installE2eBrowsers' again so the pinned Node gets unpacked first.",
                )
            }
        }
        logger.lifecycle(
            "installE2eBrowsers: installing $browser with $runnerLabel -> ${command.joinToString(" ")}",
        )
        val pb =
            ProcessBuilder(command)
                .directory(File(runDir))
                .redirectErrorStream(true)
        if (pathBinDir != null) {
            pb.environment()["PATH"] =
                pathBinDir.absolutePath + File.pathSeparator + (System.getenv("PATH") ?: "")
        }
        val proc = pb.start()
        val lastOutputAt = AtomicLong(System.currentTimeMillis())
        val reader =
            Thread({
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        lastOutputAt.set(System.currentTimeMillis())
                        logger.lifecycle("playwright: $line")
                    }
                }
            }, "playwright-install-reader")
        reader.isDaemon = true
        reader.start()

        val stallMillis = 4 * 60 * 1000L
        val hardDeadline = System.currentTimeMillis() + 30 * 60 * 1000L
        var finished = false
        var stalled = false
        while (true) {
            if (proc.waitFor(5, TimeUnit.SECONDS)) {
                finished = true
                break
            }
            val now = System.currentTimeMillis()
            if (now - lastOutputAt.get() > stallMillis) {
                stalled = true
                break
            }
            if (now > hardDeadline) {
                stalled = false
                finished = false
                break
            }
        }
        if (!finished) {
            proc.destroyForcibly()
            proc.waitFor(10, TimeUnit.SECONDS)
            reader.join(2_000)
            throw GradleException(
                if (stalled) {
                    "installE2eBrowsers: playwright install $browser (runner: $runnerLabel) produced no output for " +
                        "$stallMillis ms and was killed. A silent install means the download or the unzip step is " +
                        "wedged - the known cause is running Playwright on an unsupported Node (>= 25 makes " +
                        "playwright's bundled unzipper hang right after \"100% of …\"), the other one is a stuck " +
                        "lock. Clean up with: rm -rf $cacheDir/__dirlock $cacheDir/$browser-* and re-run."
                } else {
                    "installE2eBrowsers: 'playwright install $browser' did not finish within 30 minutes and was " +
                        "killed. This usually means a stuck __dirlock from a previous run. " +
                        "Try: rm -rf $cacheDir/__dirlock and re-run."
                },
            )
        }
        reader.join(2_000)
        if (proc.exitValue() != 0) {
            val diagnosis =
                findBrowserBinary(browser)?.let { probe ->
                    probeLaunch(probe).let { (ok, detail) ->
                        if (ok) {
                            "$browser is present and launchable at ${probe.absolutePath}, so the install itself " +
                                "failed for another reason (see the playwright output above)."
                        } else {
                            "$browser is present at ${probe.absolutePath} but $detail — missing shared libraries " +
                                "on Linux; run 'sudo node node_modules/playwright/cli.js install-deps $browser'."
                        }
                    }
                } ?: "$browser is not present in $cacheDir after the failed install."
            logger.lifecycle("installE2eBrowsers: $diagnosis")
            throw GradleException(
                "installE2eBrowsers: 'playwright install $browser' (runner: $runnerLabel) exited with code " +
                    "${proc.exitValue()}. See output above. $diagnosis On Linux, missing system libraries for " +
                    "chromium (e.g. libnss3, libatk-bridge2.0-0) are a common cause — install via " +
                    "`sudo npx playwright install-deps` after adding your distro's repos.",
            )
        }
    }

    doLast {
        logger.lifecycle("installE2eBrowsers: target OS = $osName")
        logger.lifecycle("installE2eBrowsers: Playwright cache = $cacheDir")
        clearStaleLock()

        for (browser in listOf("chromium", "chromium-headless-shell")) {
            if (isPlaywrightInstalledOk(browser)) {
                logger.lifecycle("installE2eBrowsers: $browser already present and launchable, skipping install.")
                continue
            }
            installOneBrowser(browser)
            val installedBinary = findBrowserBinary(browser)
            if (installedBinary == null) {
                val tried = expected[browser]?.binaryPaths?.joinToString(", ").orEmpty()
                throw GradleException(
                    "installE2eBrowsers: 'playwright install $browser' finished with exit 0, " +
                        "but no expected binary was found in $cacheDir. Tried: $tried ($lastProbeIssue). " +
                        "This is the symptom of a killed-by-Gradle Playwright subprocess leaving a " +
                        "half-extracted cache. Remove $cacheDir/$browser-* and re-run, or run " +
                        "'npx playwright install $browser' directly in the repo root.",
                )
            }
            val (launchOk, launchDetail) = probeLaunch(installedBinary)
            if (!launchOk) {
                throw GradleException(
                    "installE2eBrowsers: $browser was installed to ${installedBinary.absolutePath} but the binary " +
                        "does not start: $launchDetail. On Linux the usual cause is missing shared libraries " +
                        "(libnspr4, libnss3, libatk-bridge2.0-0, libgbm1, …); fix them with " +
                        "'sudo node node_modules/playwright/cli.js install-deps $browser'. " +
                        "On macOS, a quarantined bundle is the equivalent cause: " +
                        "'xattr -dr com.apple.quarantine ${installedBinary.parentFile.absolutePath}'.",
                )
            }
        }
        logger.lifecycle("installE2eBrowsers: done. Playwright cache = $cacheDir")
    }
}

