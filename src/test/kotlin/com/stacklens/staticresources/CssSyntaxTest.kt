package com.stacklens.staticresources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class CssSyntaxTest {
    private val cssFiles: List<String> =
        listOf(
            "static/css/custom.css",
            "static/css/themes.css",
            "static/css/history.css",
            "static/css/knowledge.css",
            "static/css/grafana-board.css",
        )

    @Test
    fun `every shipped CSS file is non-empty`() {
        for (path in cssFiles) {
            val text = read(path)
            assertTrue(text.isNotBlank(), "$path must not be empty")
        }
    }

    @Test
    fun `every shipped CSS file has balanced comment delimiters`() {
        for (path in cssFiles) {
            val text = read(path)
            val openers = Regex("""/\*""").findAll(text).count()
            val closers = Regex("""\*/""").findAll(text).count()
            assertEquals(
                openers,
                closers,
                "$path has $openers `/*` openers but $closers `*/` closers — broken comment?",
            )
        }
    }

    @Test
    fun `no line starts with a bare slash followed by a non-asterisk character`() {
        for (path in cssFiles) {
            val text = read(path)
            val offenders =
                text
                    .lineSequence()
                    .mapIndexed { idx, line -> idx + 1 to line }
                    .filter { (_, line) ->
                        val trimmed = line.trimStart()
                        trimmed.startsWith("/") && !trimmed.startsWith("/*")
                    }.toList()
            assertTrue(
                offenders.isEmpty(),
                "$path has lines starting with bare `/`: " +
                    offenders.joinToString { (n, l) -> "line $n: ${l.take(60)}" },
            )
        }
    }

    @Test
    fun `knowledge css pins the Uebersicht card font-size overrides`() {
        val text = read("static/css/knowledge.css")
        assertTrue(
            Regex("""\.kb-page \.card-body \.kb-metric-value\s*\{[^}]*font-size:\s*1\.25rem""").containsMatchIn(text),
            "knowledge.css must pin .kb-metric-value (the Übersicht numbers) to font-size: 1.25rem",
        )
        assertTrue(
            Regex("""\.kb-page \.card-body \.kb-metric-text\s*\{[^}]*font-size:\s*1rem""").containsMatchIn(text),
            "knowledge.css must pin .kb-metric-text (Embedding-Provider/-Modell) to font-size: 1rem",
        )
        assertTrue(
            Regex("""\.kb-page \.card-header h5[^{]*\{[^}]*font-size:\s*1rem""").containsMatchIn(text),
            "knowledge.css must pin every .kb-page card-header h5 to font-size: 1rem (tab-label size)",
        )
        assertTrue(
            Regex("""\.kb-page \.card-body h6[^{]*\{[^}]*font-size:\s*1rem""").containsMatchIn(text),
            "knowledge.css must pin the in-card h6 (e.g. 'Neue Wissensdatenbank anlegen') to font-size: 1rem",
        )
    }

    @Test
    fun `knowledge css forbids bold text anywhere in the knowledge base`() {
        val text = read("static/css/knowledge.css")
        assertTrue(
            Regex("""\.kb-page \*(?::not\(\.btn\))?[^{]*\{[^}]*font-weight:\s*400\s*!important""").containsMatchIn(text),
            "knowledge.css must pin .kb-page * (or .kb-page *:not(.btn)) to font-weight: 400 !important (no bold labels, but buttons may stay bold)",
        )
        assertTrue(
            Regex("""\.kb-page \.table thead th[^{]*\{[^}]*font-weight:\s*400\s*!important""").containsMatchIn(text),
            "knowledge.css must override custom.css's .table thead th 700 !important with 400 !important",
        )
    }

    @Test
    fun `knowledge modal-footer buttons use btn-sm so they match the page-actions buttons`() {
        val standardBtnWithoutSm =
            Regex(
                """class="btn (?:btn-outline-(?:primary|secondary|success|danger|warning)|btn-(?:primary|success|danger|warning))(?:\s+[^"]*)?"""",
            )
        for ((name, path) in listOf(
            "add-source-modals" to "templates/fragments/knowledge/add-source-modals.html",
            "export-password-overlay" to "templates/fragments/knowledge/export-password-overlay.html",
            "delete-base-modal" to "templates/fragments/knowledge/delete-base-modal.html",
        )) {
            val html = read(path)
            val match = standardBtnWithoutSm.find(html)
            assertTrue(
                match == null,
                "$name.html contains a Bootstrap button without `btn-sm` " +
                    "(${match?.value ?: ""}); every footer action button must be `btn btn-sm btn-…` to match the page-actions buttons",
            )
        }
    }

    @Test
    fun `knowledge template renders the danger pane with the two big card headings`() {
        val html = read("templates/knowledge.html")
        val dangerPane =
            Regex("""data-kb-pane="danger"(.*)""", RegexOption.DOT_MATCHES_ALL)
                .find(html)
                ?.groupValues
                ?.get(1) ?: fail("danger pane not found in knowledge.html")
        assertTrue(
            dangerPane.contains("card-header"),
            "the danger pane must contain card-header blocks (the two big headings)",
        )
        assertTrue(
            dangerPane.contains("knowledge.reindex.title") && dangerPane.contains("knowledge.danger.title"),
            "the danger pane must reference the heading i18n keys knowledge.reindex.title and knowledge.danger.title",
        )
    }

    @Test
    fun `knowledge template metric divs carry the dedicated metric classes`() {
        val html = read("templates/knowledge.html")
        for (id in listOf("kbMetricBases", "kbMetricSources")) {
            assertTrue(
                html.contains("class=\"kb-metric-value mb-0\" id=\"$id\""),
                "knowledge.html must render #$id with class kb-metric-value",
            )
        }
        for (id in listOf("kbMetricProvider", "kbMetricModel")) {
            assertTrue(
                html.contains("class=\"kb-metric-text mb-0\" id=\"$id\""),
                "knowledge.html must render #$id with class kb-metric-text",
            )
        }
        assertTrue(
            !Regex("""class="h[25] mb-0 fw-semibold" id="kbMetric""").containsMatchIn(html),
            "knowledge.html must not use Bootstrap h2/h5 classes on the kbMetric* divs",
        )
    }

    private fun read(path: String): String =
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: fail("classpath resource not found: $path")
}
