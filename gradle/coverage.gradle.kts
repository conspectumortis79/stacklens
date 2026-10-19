import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

val sourceSets = the<SourceSetContainer>()

configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"
}

val coverageClasses: FileTree =
    sourceSets.getByName("main").output.classesDirs.asFileTree.matching {
        exclude("**/shared/JvmTerminationDefaultsKt*.class")
        exclude("**/loader/EncryptedLauncher.class")
    }

val childExecData: FileTree =
    fileTree(layout.buildDirectory.dir("jacoco/children").get().asFile) { include("**/child-*.exec") }

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"), tasks.named("javaFxTest"))
    executionData.from(tasks.named("javaFxTest").map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! })
    executionData.from(childExecData)
    classDirectories.setFrom(coverageClasses)
    reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    executionData(
        layout.buildDirectory.file("jacoco/test.exec"),
        tasks.named("javaFxTest").map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! },
        childExecData,
    )
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(
        sourceSets.getByName("main").allSource.sourceDirectories,
    )

    val projectRootPath: String = rootDir.absolutePath
    doFirst {
        val files = classDirectories.asFileTree.files.filter { it.extension == "class" }
        println("VERIFY_CD:${files.size}")
        files.sorted().forEach { println("VCDFILE:${it.absolutePath.removePrefix("$projectRootPath/")}") }
    }
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "COMPLEXITY"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.register("listClassDirs") {
    dependsOn(tasks.named("jacocoTestReport"))
    doFirst {
        val cd = tasks.named<JacocoReport>("jacocoTestReport").get().classDirectories
        val files =
            cd.asFileTree.files
                .filter { it.extension == "class" }
                .map { it.absolutePath.replace(rootDir.absolutePath + "/build/classes/kotlin/main/", "").removeSuffix(".class") }
        println("TOTAL_CD:${files.size}")
        files.sorted().forEach { println("CD:$it") }
    }
}

tasks.register("debugVerification") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
    doFirst {
        val cd = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification").get().classDirectories
        val files = cd.asFileTree.files.filter { it.extension == "class" }
        println("TOTAL_VCD:${files.size}")
    }
}
