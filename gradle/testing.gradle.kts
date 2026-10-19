import java.time.Duration

val sourceSets = the<SourceSetContainer>()

val javaFxIsolatedTests: List<String> =
    listOf(
        "DesktopExternalLinkInterceptorInstallCoverageTest",
        "DesktopLauncherDefaultHooksCoverageTest",
        "DefaultScreenBoundsProviderCoverageTest",
        "DesktopPopupHandlerInstallConstructionTest",
        "DesktopStartupErrorReporterEngineCoverageTest",
        "RealJsExecutorStrategyCoverageTest",
        "RealPopupHandlerStrategyEngineCoverageTest",
        "WebViewConfiguratorRealAdapterCoverageTest",
    )

tasks.named<Test>("test") {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    val childExecDir = layout.buildDirectory.dir("jacoco/children").get().asFile
    doFirst {
        childExecDir.listFiles { file -> file.name.startsWith("child-") }?.forEach { it.delete() }
    }
}

val javaFxTest =
    tasks.register<Test>("javaFxTest") {
        group = "verification"
        description = "Runs the JavaFX engine tests in a dedicated worker so the FX toolkit stays usable"
        mustRunAfter(tasks.named("test"))
        testClassesDirs = sourceSets.getByName("test").output.classesDirs
        classpath = sourceSets.getByName("test").runtimeClasspath
        javaFxIsolatedTests.forEach { include("**/$it.class") }
        systemProperty("java.awt.headless", "false")
        outputs.upToDateWhen { false }
    }

val testParallelForks: Int =
    (providers.gradleProperty("testForks").orNull?.toIntOrNull() ?: 4)
        .coerceIn(1, Runtime.getRuntime().availableProcessors())

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = testParallelForks
    filter {
        javaFxIsolatedTests.forEach { excludeTestsMatching("com.stacklens.desktop.$it") }
        excludeTestsMatching("*SystemTest")
        excludeTestsMatching("*SystemTests")
        excludeTestsMatching("*E2ETest")
        excludeTestsMatching("*E2ETests")
        excludeTestsMatching("*AcceptanceTest")
        excludeTestsMatching("*FunctionalTest")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    timeout.set(Duration.ofMinutes(10))

    systemProperty("junit.jupiter.execution.timeout.default", "90s")
    systemProperty("junit.jupiter.execution.timeout.thread.mode", "SEPARATE_THREAD")
    jvmArgs(
        "-Xmx2g",
        "-Xshare:off",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-XX:+EnableDynamicAgentLoading",
        "-Dnet.bytebuddy.experimental=true",
    )
}

tasks.register<Test>("systemTest") {
    group = "verification"
    description = "Runs all backend system tests (classes matching *SystemTest) without the frontend."
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath

    filter {
        includeTestsMatching("*SystemTest")
        includeTestsMatching("*SystemTests")
        includeTestsMatching("*E2ETest")
        includeTestsMatching("*E2ETests")
        includeTestsMatching("*AcceptanceTest")
        includeTestsMatching("*FunctionalTest")
    }
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
    jvmArgs(
        "-Xshare:off",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-XX:+EnableDynamicAgentLoading",
        "-Dnet.bytebuddy.experimental=true",
    )
}
