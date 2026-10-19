package com.stacklens.application.service.logging

import com.stacklens.adapter.out.filesystem.ClasspathTranslationCatalogAdapter
import com.stacklens.application.port.out.TranslationCatalogPort
import com.stacklens.testsupport.TestJsonCodec
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader

private const val LOGGING_FIX_PREFIX = "logging_fix."

class LoggingFixMarkersTest {
    private val markers = catalogBackedLoggingFixMarkers()

    @Test
    fun `skip marker is rendered in german with the heuristic threshold interpolated`() {
        assertEquals(
            "[Diagnose übersprungen — Log-Input zu dünn: weniger als 5 nicht-leere Zeilen und kein " +
                "Stack-Trace/Exception-Marker gefunden. Bitte vollständigen Log-Ausschnitt (±5 min um den " +
                "ersten error-Eintrag) nachliefern.]",
            markers.skipMarker("de"),
        )
    }

    @Test
    fun `skip marker is rendered in english for en and EN`() {
        val expected =
            "[Diagnosis skipped — log input too thin: fewer than 5 non-blank lines and no " +
                "stack-trace/exception marker found. Please supply a complete log excerpt (±5 min around " +
                "the first error entry).]"

        assertEquals(expected, markers.skipMarker("en"))
        assertEquals(expected, markers.skipMarker("EN"))
    }

    @Test
    fun `an unknown language falls back to the german marker`() {
        assertTrue(markers.skipMarker("").startsWith("[Diagnose übersprungen"), markers.skipMarker(""))
        assertTrue(markers.skipMarker("fr").startsWith("[Diagnose übersprungen"), markers.skipMarker("fr"))
    }

    @Test
    fun `empty answer markers carry the translated stage name`() {
        assertEquals("[Diagnose-Stage ohne Ergebnis — KI-Antwort war leer]", markers.emptyResultMarker("diagnose", "de"))
        assertEquals("[Test-Stage ohne Ergebnis — KI-Antwort war leer]", markers.emptyResultMarker("tests", "de"))
        assertEquals("[Plan stage produced no result — the AI answer was empty]", markers.emptyResultMarker("plan", "en"))
    }

    @Test
    fun `downstream stages report a blocked diagnosis in the run language`() {
        assertEquals("[Plan-Stage übersprungen — Diagnose nicht möglich]", markers.stageSkippedNoDiagnosisMarker("plan", "de"))
        assertEquals("[Code stage skipped — diagnosis not possible]", markers.stageSkippedNoDiagnosisMarker("code", "en"))
    }

    @Test
    fun `downstream stages report a missing repository with the stage specific reason`() {
        val german =
            "[Plan-Stage übersprungen — kein Repository ausgewählt]\n" +
                "Ohne Repository kann die KI weder Architektur noch Coding-Guidelines analysieren."
        val english =
            "[Code stage skipped — no repository selected]\n" +
                "Without a repository no code fix can be created."

        assertEquals(german, markers.stageSkippedNoRepositoryMarker("plan", "de"))
        assertEquals(english, markers.stageSkippedNoRepositoryMarker("code", "en"))
        assertEquals(
            "[Test-Stage übersprungen — kein Repository ausgewählt]\nOhne Repository können keine Tests erstellt werden.",
            markers.stageSkippedNoRepositoryMarker("tests", "de"),
        )
    }

    @Test
    fun `an empty category selection is rendered in the run language`() {
        assertEquals("(alle)", markers.categoriesAll("de"))
        assertEquals("(all)", markers.categoriesAll("en"))
    }

    @Test
    fun `every skip signature resolves to translated text in both catalog languages`() {
        val signatures = markers.skipSignatures()

        assertEquals(LoggingFixMarkers.SKIP_SIGNATURE_KEYS.size * 2, signatures.size)
        assertTrue(signatures.none { it.contains("logging_fix.") }, signatures.toString())
        assertTrue(signatures.contains("Diagnose übersprungen"), signatures.toString())
        assertTrue(signatures.contains("Diagnosis skipped"), signatures.toString())
    }

    @Test
    fun `skip detection recognises the rendered markers of both languages`() {
        val rendered =
            listOf(
                markers.skipMarker("de"),
                markers.skipMarker("en"),
                markers.emptyResultMarker("plan", "de"),
                markers.emptyResultMarker("plan", "en"),
                markers.stageSkippedNoDiagnosisMarker("code", "de"),
                markers.stageSkippedNoDiagnosisMarker("code", "en"),
                markers.stageSkippedNoRepositoryMarker("tests", "de"),
                markers.stageSkippedNoRepositoryMarker("tests", "en"),
            )

        rendered.forEach { assertTrue(markers.isSkipMarker(it), it) }
    }

    @Test
    fun `skip detection still recognises markers persisted before the translations were introduced`() {
        val legacy =
            listOf(
                "[Diagnose übersprungen — Log-Input zu dünn: weniger als 5 nicht-leere Zeilen und kein " +
                    "Stack-Trace/Exception-Marker gefunden. Bitte vollständigen Log-Ausschnitt (±5 min um den " +
                    "ersten error-Eintrag) nachliefern.]",
                "[Diagnosis skipped — log input too thin: fewer than 5 non-blank lines and no " +
                    "stack-trace/exception marker found. Please supply a complete log excerpt (±5 min around " +
                    "the first error entry).]",
                "[Plan-Stage übersprungen — Diagnose nicht möglich]",
                "[Code-Stage übersprungen — kein Repository ausgewählt]",
                "[Test-Stage ohne Ergebnis — KI-Antwort war leer]",
                "[Diagnose stage produced no result — the AI answer was empty]",
            )

        legacy.forEach { assertTrue(markers.isSkipMarker(it), it) }
    }

    @Test
    fun `skip detection ignores a real diagnosis and a partial translation of it`() {
        assertFalse(markers.isSkipMarker("Root cause: the connection pool is exhausted after 30s."))
        assertFalse(markers.isSkipMarker("Die Diagnose zeigt einen leeren Ergebnis-Puffer im Writer."))
    }

    @Test
    fun `german and english catalogs carry the same logging_fix keys`() {
        val catalog: TranslationCatalogPort = ClasspathTranslationCatalogAdapter(DefaultResourceLoader(), TestJsonCodec.create())
        val germanKeys = translatedKeys(catalog, "de")
        val englishKeys = translatedKeys(catalog, "en")

        assertTrue(germanKeys.isNotEmpty())
        assertEquals(germanKeys, englishKeys)
    }

    private fun translatedKeys(
        catalog: TranslationCatalogPort,
        language: String,
    ): Set<String> {
        val keys = catalog.load(language).keys
        return keys.filter { it.startsWith(LOGGING_FIX_PREFIX) }.toSet()
    }
}
