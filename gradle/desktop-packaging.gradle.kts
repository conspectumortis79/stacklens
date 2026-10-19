import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService

val javaToolchains = the<JavaToolchainService>()

val sourceSets = the<SourceSetContainer>()

tasks.register<Exec>("buildMacInstaller") {
    group = "distribution"
    description = "Builds macOS .pkg and .dmg installer artifacts"
    dependsOn(tasks.named("prepareDesktopPackageInput"))
    environment("SKIP_DESKTOP_PREPARE", "1")
    commandLine("bash", "scripts/build-macos-installer.sh")
}

tasks.register<Exec>("buildLinuxDeb") {
    group = "distribution"
    description = "Builds Linux .deb installer artifact (Ubuntu/Pop!_OS)"
    dependsOn(tasks.named("prepareDesktopPackageInput"))
    environment("SKIP_DESKTOP_PREPARE", "1")

    val toolchainLauncher =
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }

    val rootDirPath = project.rootDir.absolutePath

    doFirst {
        val flatpakId = System.getenv("FLATPAK_ID")
        if (!flatpakId.isNullOrBlank()) {
            throw GradleException(
                """
                |
                |⚠️  Flatpak sandbox detected ($flatpakId).
                |   dpkg-deb and fakeroot are not accessible from IntelliJ's embedded terminal.
                |
                |   Run the build from a system terminal instead:
                |
                |     1. Open a system terminal (Pop!_OS Terminal, not IntelliJ's terminal)
                |     2. Install fakeroot if missing:
                |          sudo apt-get install -y fakeroot
                |     3. Run the Gradle task:
                |          cd "$rootDirPath"
                |          ./gradlew buildLinuxDeb
                |
                """.trimMargin(),
            )
        }

        val javaHome =
            toolchainLauncher
                .get()
                .metadata.installationPath.asFile
        val jpackageBin = File(javaHome, "bin/jpackage")
        if (jpackageBin.isFile) {
            environment("JPACKAGE_BIN", jpackageBin.absolutePath)
            logger.lifecycle("Using jpackage from toolchain: ${jpackageBin.absolutePath}")
        }
    }

    commandLine("bash", "scripts/build-linux-deb.sh")
}

tasks.register<Jar>("desktopJar") {
    group = "build"
    description = "Builds a plain desktop launcher jar for jpackage"
    dependsOn(tasks.named("classes"))
    archiveBaseName.set("stacklens-desktop")
    archiveVersion.set("")
    from(sourceSets.getByName("main").output)
}

tasks.register<Sync>("prepareDesktopPackageInput") {
    group = "distribution"
    description = "Prepares jars for desktop packaging with jpackage"
    dependsOn(tasks.named("desktopJar"))
    from(tasks.named<Jar>("desktopJar").flatMap { it.archiveFile })
    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("desktop-input"))
}
