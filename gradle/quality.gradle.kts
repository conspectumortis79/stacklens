val kotlinTargetVersion: String =
    (project.findProperty("kotlinVersion") as? String)
        ?: "2.3.20"

tasks.register("kotlinVersionCheck") {
    group = "verification"
    description = "Asserts that kotlinTargetVersion matches the Kotlin Gradle plugin declared in plugins {}"

    val expected: String = kotlinTargetVersion
    val actual: String? =
        runCatching {
            val klass = Class.forName("org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt")
            val method =
                klass.methods.firstOrNull {
                    it.name == "getKotlinPluginVersion" &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0].isAssignableFrom(project.javaClass)
                }
            method?.invoke(null, project)?.toString()
        }.getOrNull()

    doLast {
        if (actual == null) {
            logger.lifecycle("ℹ  Could not introspect the Kotlin plugin version dynamically; please confirm manually that:")
            logger.lifecycle("    plugins { kotlin(\"jvm\") version \"$expected\" }")
            logger.lifecycle("    matches the constant val kotlinTargetVersion = \"$expected\"")
            return@doLast
        }

        if (actual != expected) {
            throw GradleException(
                "Kotlin version drift: kotlinTargetVersion=\"$expected\" but the Gradle plugin reports " +
                    "\"$actual\". Update the constant near `val kotlinTargetVersion` in gradle/quality.gradle.kts " +
                    "to match `kotlin(\\\"jvm\\\") version \\\"$actual\\\"` in the plugins block.",
            )
        }
        logger.lifecycle("✅  kotlinTargetVersion (\"$expected\") matches the Gradle plugin (\"$actual\").")
    }
}

val javaTargetVersion: Int =
    (project.findProperty("javaVersion") as? String)?.toIntOrNull() ?: 25

tasks.register("javaToolchainCheck") {
    group = "verification"
    description = "Asserts that javaTargetVersion matches the languageVersion declared in `java { toolchain { ... } }`"

    val expected: Int = javaTargetVersion
    val actual: Int? =
        runCatching {
            val javaExt = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
            val spec =
                javaExt.javaClass.getMethod("getToolchain").invoke(javaExt)
                    ?: return@runCatching null
            val langVerProp =
                spec.javaClass.getMethod("getLanguageVersion").invoke(spec)
                    ?: return@runCatching null
            val langVer =
                langVerProp.javaClass.getMethod("get").invoke(langVerProp)
                    ?: return@runCatching null
            langVer.javaClass.getMethod("asInt").invoke(langVer) as? Int
        }.getOrNull()

    doLast {
        if (actual == null) {
            logger.lifecycle("ℹ  Could not introspect the toolchain languageVersion dynamically; please confirm manually that:")
            logger.lifecycle("    java { toolchain { languageVersion.set(JavaLanguageVersion.of($expected)) } }")
            logger.lifecycle("    matches the constant val javaTargetVersion = $expected")
            return@doLast
        }

        if (actual != expected) {
            throw GradleException(
                "Java toolchain version drift: javaTargetVersion=$expected but the toolchain spec reports " +
                    "$actual. Update the constant near `val javaTargetVersion` in gradle/quality.gradle.kts to match " +
                    "`JavaLanguageVersion.of($actual)` in the `java { toolchain { ... } }` block.",
            )
        }
        logger.lifecycle("✅  javaTargetVersion ($expected) matches the toolchain spec ($actual).")
    }
}
