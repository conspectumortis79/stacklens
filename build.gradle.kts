import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openjfx.javafxplugin") version "0.1.0"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    kotlin("plugin.allopen") version "2.3.20"
    kotlin("plugin.jpa") version "2.3.20"
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "com.stacklens"
version = "0.0.1-SNAPSHOT"

allOpen {
    annotation("com.stacklens.application.stereotype.ApplicationService")
}

springBoot {
    mainClass.set("com.stacklens.StackLensApplicationKt")
}

val bootJarArchiveName = "${rootProject.name}-${project.version}.jar"
val bootJarStagingDir = layout.buildDirectory.dir("boot-jar-staging")

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    mainClass.set("com.stacklens.StackLensApplicationKt")
    destinationDirectory.set(bootJarStagingDir)
    archiveFileName.set(bootJarArchiveName)

    outputs.cacheIf { false }
}

val publishBootJar =
    tasks.register<PublishBootJar>("publishBootJar") {
        group = "distribution"
        description = "Publishes the staged boot JAR into build/libs by rename so a running JVM never reads a half-replaced archive"
        stagedArchive.set(bootJarStagingDir.get().file(bootJarArchiveName))
        publishedArchive.set(layout.buildDirectory.file("libs/$bootJarArchiveName"))
    }

tasks.named("bootJar") {
    finalizedBy(publishBootJar)
}

tasks.named<org.springframework.boot.gradle.plugin.ResolveMainClassName>("resolveTestMainClassName") {
    configuredMainClassName.set("com.stacklens.StackLensApplicationKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

javafx {
    version = "21.0.12"
    modules("javafx.controls", "javafx.web", "javafx.media")

    val osName = System.getProperty("os.name", "").lowercase()
    val osArch = System.getProperty("os.arch", "").lowercase()
    if (osName.contains("linux") && (osArch == "aarch64" || osArch == "arm64")) {
        setPlatform("linux")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.aspectj:aspectjweaver:1.9.25.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.postgresql:postgresql")
    implementation("com.mysql:mysql-connector-j")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre11")
    implementation("nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:4.0.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-scratchpad:5.3.0")
    implementation("net.sourceforge.tess4j:tess4j:5.11.0")
    implementation("org.apache.lucene:lucene-core:10.4.0")
    implementation("org.apache.lucene:lucene-analysis-common:10.4.0")
    implementation("org.apache.lucene:lucene-queryparser:10.4.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
    testImplementation("org.mockito:mockito-core:5.16.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.16.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("net.bytebuddy:byte-buddy:1.17.0")
    testRuntimeOnly("net.bytebuddy:byte-buddy-agent:1.17.0")
    testRuntimeOnly("com.h2database:h2")

    runtimeOnly("io.netty:netty-resolver-dns-native-macos") { artifact { classifier = "osx-aarch_64" } }
    runtimeOnly("io.netty:netty-resolver-dns-native-macos") { artifact { classifier = "osx-x86_64" } }
    runtimeOnly("com.h2database:h2")

    compileOnly("org.jspecify:jspecify:1.0.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        freeCompilerArgs.add("-jvm-default=no-compatibility")
        jvmTarget.set(JvmTarget.fromTarget("25"))
    }
}

tasks.withType<Test> {
    systemProperty("java.awt.headless", "false")
}

ktlint {
    version.set("1.6.0")
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
    }
}

node {
    download.set(true)
    version.set("22.12.0")
    workDir.set(
        layout.buildDirectory
            .dir("nodejs")
            .get()
            .asFile,
    )
    npmWorkDir.set(
        layout.buildDirectory
            .dir("npm")
            .get()
            .asFile,
    )
}

tasks.register("patchNpmBin") {
    dependsOn(tasks.named("nodeSetup"))
    val nodeBuildDir = layout.buildDirectory.dir("nodejs")
    doLast {
        val nodeDir = nodeBuildDir.get().asFile
        val npmBinDir =
            nodeDir
                .listFiles()
                ?.firstOrNull { it.isDirectory }
                ?.resolve("lib/node_modules/npm/bin") ?: return@doLast
        listOf("npm-cli.js", "npx-cli.js").forEach { name ->
            val shim = npmBinDir.resolve(name)
            if (!shim.exists()) {
                val entry = if (name == "npx-cli.js") "npx" else "npm"
                shim.writeText("#!/usr/bin/env node\nrequire('../lib/$entry/cli.js')(process)\n")
            }
        }
    }
}

tasks.named("npmInstall") { dependsOn("patchNpmBin") }

tasks.register<com.github.gradle.node.npm.task.NpmTask>("testFrontend") {
    group = "verification"
    description = "Run Vitest frontend JavaScript tests via npm"
    dependsOn(tasks.named("npmInstall"))
    args.set(listOf("run", "test:frontend"))
    execOverrides {
        standardOutput = System.out
        errorOutput = System.err
    }
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("testE2e") {
    group = "verification"
    description = "Run Playwright end-to-end tests (requires StackLens running on BASE_URL, default http://127.0.0.1:8091)"
    dependsOn(tasks.named("npmInstall"))
    dependsOn(tasks.named("bootJar"))
    dependsOn("installE2eBrowsers")
    mustRunAfter(tasks.named("test"), tasks.named("testFrontend"))
    val javaLauncher =
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    environment.put(
        "E2E_JAVA_BIN",
        javaLauncher.map { it.executablePath.asFile.absolutePath },
    )
    val baseUrl = resolvedBaseUrl("e2e.baseUrl", "http://127.0.0.1:8091")
    environment.put("BASE_URL", baseUrl)
    args.set(listOf("run", "test:e2e"))
    execOverrides {
        standardOutput = System.out
        errorOutput = System.err
    }

    doFirst {
        val port =
            try {
                URI(baseUrl).port
            } catch (e: Exception) {
                8091
            }
        if (port <= 0) return@doFirst
        val lsof =
            try {
                ProcessBuilder("lsof", "-nP", "-ti", "tcp:$port", "-sTCP:LISTEN")
                    .redirectErrorStream(true)
                    .start()
            } catch (e: Exception) {
                logger.lifecycle("testE2e: lsof not available, skipping stale-server cleanup (${e.message})")
                null
            }
        if (lsof != null) {
            val output =
                lsof.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            val exit = lsof.waitFor()
            if (exit == 0 && output.isNotEmpty()) {
                val pids =
                    output
                        .split("\\s+".toRegex())
                        .filter { it.isNotBlank() }
                logger.lifecycle("testE2e: killing stale server PID(s) on port $port: $pids")
                pids.forEach { pid ->
                    try {
                        ProcessBuilder("kill", "-TERM", pid)
                            .start()
                            .waitFor()
                    } catch (e: Exception) {
                        logger.lifecycle("testE2e: failed to kill PID $pid (${e.message})")
                    }
                }
                Thread.sleep(1500)
                val lsof2 =
                    ProcessBuilder("lsof", "-nP", "-ti", "tcp:$port", "-sTCP:LISTEN")
                        .redirectErrorStream(true)
                        .start()
                val remaining =
                    lsof2.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                lsof2.waitFor()
                if (remaining.isNotEmpty()) {
                    logger.lifecycle("testE2e: SIGKILL stubborn PIDs on port $port: $remaining")
                    remaining
                        .split("\\s+".toRegex())
                        .filter { it.isNotBlank() }
                        .forEach { pid ->
                            try {
                                ProcessBuilder("kill", "-KILL", pid)
                                    .start()
                                    .waitFor()
                            } catch (_: Exception) {
                            }
                        }
                    Thread.sleep(500)
                }
            } else {
                logger.lifecycle("testE2e: port $port is free, no stale server to kill")
            }
        }
    }
}

fun resolvedBaseUrl(
    propertyName: String,
    defaultUrl: String,
): String =
    providers
        .gradleProperty(propertyName)
        .orElse(providers.environmentVariable("BASE_URL"))
        .orElse(defaultUrl)
        .get()

tasks.register<com.github.gradle.node.npm.task.NpmTask>("testFrontendLive") {
    group = "verification"
    description =
        "Run the browser-driven frontend layout tests against a running StackLens (BASE_URL, default http://127.0.0.1:8090)"
    dependsOn(tasks.named("npmInstall"))
    mustRunAfter(tasks.named("test"), tasks.named("testFrontend"), tasks.named("testE2e"))
    val baseUrl = resolvedBaseUrl("frontendLive.baseUrl", "http://127.0.0.1:8090")
    environment.put("STACKLENS_UI_URL", baseUrl)
    environment.put("BASE_URL", baseUrl)
    args.set(listOf("run", "test:frontend:live"))
    execOverrides {
        standardOutput = System.out
        errorOutput = System.err
    }
    doFirst {
        val connection = URI(baseUrl).toURL().openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        connection.requestMethod = "GET"
        val reachable =
            try {
                connection.responseCode > 0
            } catch (e: Exception) {
                false
            } finally {
                connection.disconnect()
            }
        if (!reachable) {
            throw GradleException(
                "testFrontendLive needs a running StackLens on $baseUrl - start it with ./start.sh or " +
                    "./gradlew bootRun, or point -PfrontendLive.baseUrl at a running instance.",
            )
        }
    }
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("testE2eReport") {
    group = "verification"
    description = "Open the Playwright HTML report from the last test run"
    dependsOn(tasks.named("npmInstall"))
    args.set(listOf("run", "test:e2e:report"))
    execOverrides {
        standardOutput = System.out
        errorOutput = System.err
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        },
    )
    mainClass.set("com.stacklens.desktop.StackLens")
    systemProperty("java.awt.headless", "false")
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
    )

    if (System.getProperty("os.name", "").lowercase().contains("linux")) {
        val upstream = errorOutput ?: System.err
        errorOutput =
            object : OutputStream() {
                private val lineBuf = ByteArrayOutputStream(256)

                override fun write(b: Int) {
                    if (b == '\n'.code) {
                        val text = lineBuf.toString("UTF-8")
                        lineBuf.reset()
                        if (
                            "XSetErrorHandler" !in text &&
                            "GDK error trap" !in text &&
                            "Failed to open Wayland display" !in text
                        ) {
                            upstream.write((text + "\n").toByteArray(Charsets.UTF_8))
                            upstream.flush()
                        }
                    } else {
                        lineBuf.write(b)
                    }
                }

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = b.copyOfRange(off, off + len).forEach { write(it.toInt() and 0xFF) }

                override fun flush() = upstream.flush()

                override fun close() = upstream.close()
            }
    }

    doFirst {
        @Suppress("UNCHECKED_CAST")
        val task = this as org.springframework.boot.gradle.tasks.run.BootRun
        val jfxJars =
            task.classpath.filter { f ->
                f.name.let { n -> (n.startsWith("javafx-") || n.startsWith("javafx.")) && n.endsWith(".jar") }
            }
        if (!jfxJars.isEmpty) {
            task.classpath = task.classpath.minus(jfxJars)
            task.jvmArgs(
                "--module-path=${jfxJars.asPath}",
                "--add-modules=javafx.controls,javafx.web,javafx.media",
                "--enable-native-access=javafx.graphics",
                "--enable-native-access=javafx.web",
                "--enable-native-access=javafx.media",
            )
        }
    }
}

apply(from = "gradle/testing.gradle.kts")
apply(from = "gradle/coverage.gradle.kts")
apply(from = "gradle/quality.gradle.kts")
apply(from = "gradle/desktop-packaging.gradle.kts")
apply(from = "gradle/e2e-browsers.gradle.kts")
apply(from = "gradle/source-encryption.gradle.kts")

abstract class PublishBootJar : DefaultTask() {
    @get:InputFile
    abstract val stagedArchive: RegularFileProperty

    @get:OutputFile
    abstract val publishedArchive: RegularFileProperty

    @TaskAction
    fun publish() {
        val staged = stagedArchive.get().asFile.toPath()
        val target = publishedArchive.get().asFile.toPath()
        Files.createDirectories(target.parent)
        val incoming = target.resolveSibling("${target.fileName}.publishing-${ProcessHandle.current().pid()}")
        try {
            Files.copy(staged, incoming, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            try {
                Files.move(incoming, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(incoming, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(incoming)
        }
    }
}
