package com.stacklens.staticresources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.yaml.snakeyaml.Yaml
import java.io.File

class CacheBustingConfigTest {
    @Test
    fun `application yml enables content-hash versioning for all static resources`() {
        val root = readApplicationYml()

        val content = navigate(root, "spring", "web", "resources", "chain", "strategy", "content")

        assertEquals(
            true,
            content["enabled"],
            "spring.web.resources.chain.strategy.content.enabled must be true " +
                "so static asset URLs carry a content hash (cache busting)",
        )
        val paths = content["paths"]
        assertTrue(
            paths is List<*> && paths.any { it.toString().trim() == "/**" } || paths.toString().trim() == "/**",
            "spring.web.resources.chain.strategy.content.paths must cover /**, was: $paths",
        )
    }

    @Test
    fun `templates reference local css and js assets only via thymeleaf url expressions`() {
        val hardcodedLocalAsset =
            Regex("""(?:href|src)="/(?:css|js)/[^"]+"|(?:href|src)='/(?:css|js)/[^']+'""")

        val offenders =
            templateDir()
                .walkTopDown()
                .filter { it.isFile && it.extension == "html" }
                .flatMap { file ->
                    file
                        .readLines()
                        .mapIndexed { idx, line -> Triple(file, idx + 1, line) }
                        .filter { (_, _, line) -> hardcodedLocalAsset.containsMatchIn(line) }
                }.toList()

        assertTrue(
            offenders.isEmpty(),
            "hardcoded local asset URLs bypass content-hash versioning; use th:href=\"@{…}\" / th:src=\"@{…}\":\n" +
                offenders.joinToString("\n") { (f, n, l) -> "${f.name}:$n: ${l.trim()}" },
        )
    }

    @Test
    fun `knowledge template has no inline style safety net`() {
        val html = File(templateDir(), "knowledge.html").readText()
        val styleBlocks = Regex("<style>(.*?)</style>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        assertTrue(
            styleBlocks.isEmpty(),
            "knowledge.html must not carry an inline <style> block — " +
                "the kb-page rules live in knowledge.css and that file's URL " +
                "is content-hash versioned (no stale cache possible)",
        )
    }

    @Test
    fun `layout does not poll or auto-reload on app startup`() {
        val html = File(templateDir(), "layout.html").readText()
        for (needle in listOf("/api/system/startup-token", "sl.startupToken", "_slr")) {
            assertTrue(
                !html.contains(needle),
                "layout.html must not contain '$needle' — content-hash versioning already handles cache busting, " +
                    "no polling or auto-reload should be added",
            )
        }
    }

    @Test
    fun `knowledge template links its stylesheet through a thymeleaf url expression`() {
        val html = File(templateDir(), "knowledge.html").readText()
        assertTrue(
            html.contains("""th:href="@{/css/knowledge.css}""""),
            "knowledge.html must link knowledge.css via th:href=\"@{/css/knowledge.css}\" so the URL gets versioned",
        )
    }

    private fun readApplicationYml(): Map<*, *> {
        val file = File("src/main/resources/application.yml")
        assertTrue(file.isFile, "main application.yml not found: ${file.absolutePath}")
        return Yaml().load(file.readText()) as? Map<*, *> ?: fail("application.yml did not parse to a map")
    }

    private fun navigate(
        root: Map<*, *>,
        vararg keys: String,
    ): Map<*, *> {
        var current: Any? = root
        for (key in keys) {
            current =
                (current as? Map<*, *>)?.get(key)
                    ?: fail("application.yml is missing ${keys.joinToString(".")} (failed at '$key')")
        }
        val result = current as? Map<*, *>
        assertNotNull(result, "${keys.joinToString(".")} must be a map")
        return result!!
    }

    private fun templateDir(): File {
        val url =
            javaClass.classLoader.getResource("templates")
                ?: fail("classpath directory not found: templates")
        val dir = File(url.toURI())
        assertTrue(dir.isDirectory, "template directory not found: ${dir.absolutePath}")
        return dir
    }
}
