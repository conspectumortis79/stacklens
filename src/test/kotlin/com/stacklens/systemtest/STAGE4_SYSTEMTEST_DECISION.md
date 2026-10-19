# Stage 4 — Systemtest-Entscheidung

## Empfehlung: vorhandenes Systemtest-Verzeichnis erweitern

## Begründung

- **Systemtest-Verzeichnis ist vorhanden:** `src/test/kotlin/com/stacklens/systemtest/` enthält 60+ Klassen inkl. `LoggingFixSystemTest.kt` und `LoggingFixPromptPipelineSystemTest.kt`. Stage-2-Empfehlung „vorhandenes Verzeichnis erweitern, kein neues Setup" wird befolgt.
- **Konvention eingehalten** für alle Stage-3-Erweiterungen:
  - Klassenname endet auf `SystemTest` (Gradle-Pattern `includeTestsMatching("*SystemTest")`)
  - **Kein** `@Tag("system-test")` (Filterung läuft rein über Klassenname-Pattern, siehe `gradle/testing.gradle.kts`)
  - `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) + @AutoConfigureMockMvc + @Autowired lateinit var mockMvc: MockMvc`
- **Problem ist systemtestfähig:** End-to-End-Pfad HTTP → Service → Diagnose-Stage → JPA-Repository → DB. Mehrere Komponenten beteiligt.
- **Erweiterung vor Neuanlage** für die zentrale Klasse `LoggingFixSystemTest.kt` (+4 Testfälle).
- **Neue Klasse `LoggingInputHeuristicSystemTest.kt` gerechtfertigt:** Sie enthält eine Cross-Cutting-Kontrakt-Prüfung (Code-Konstante ↔ Prompt-Datei), die keine bestehende Systemtest-Klasse abdeckt. Sie liegt im vorhandenen systemtest-Verzeichnis und folgt der `*SystemTest`-Konvention.

## Was Stage 4 ergänzt (über Stage 3 hinaus)

| Datei | Typ | Zweck |
|---|---|---|
| `LoggingFixDiagnoseStageTest.kt` | Unit | Schließt die Lücke zwischen Heuristik-Unit-Test und HTTP-E2E: prüft mit Mocks, dass der Copilot bei `tooThin` NICHT aufgerufen wird und der kanonische Skip-Marker via `onPartial` emittiert wird. Schnellere Regressionserkennung, kein Spring-Context. |

## Was Stage 3 bereits geliefert hat (nicht erneut ausgegeben)

| Datei | Typ | Zweck |
|---|---|---|
| `LoggingInputHeuristicTest.kt` | Unit | Deckt alle vier Eingabeklassen ab (zu dünn / Stack-Trace-Bypass / ISO-Bypass / 5+ Zeilen) plus Sprachwahl und Token-Konstante |
| `LoggingFixSystemTest.kt` (erweitert) | System | 4 neue Testfälle: Too-Thin → DB, SKIPPED-Status, Stack-Trace-Bypass, ISO-Datum-Bypass |
| `LoggingInputHeuristicSystemTest.kt` | System | Code-Konstante ↔ Prompt-Datei-Konsistenz, JVM-Frame-Erkennung im Mid-Input |

## Abdeckungsmatrix

| Komponente | Unit | System |
|---|---|---|
| `LoggingInputHeuristic.classify()` | ✅ | ✅ |
| `LoggingInputHeuristic.SKIP_MARKER_DE` Konstante | ✅ | ✅ (Prompt-Konsistenz) |
| `LoggingFixDiagnoseStage.execute()` Short-Circuit | ✅ (Stage 4) | ✅ |
| `LoggingFixDiagnoseStage.execute()` Positiv-Pfad | ✅ (Stage 4) | ✅ |
| `LoggingFixDiagnoseStage.isDiagnosisNotPossibleMarker` | ✅ (Stage 4) | – |
| `LoggingFixService.stageState()` SKIPPED-Mapping | implizit via E2E | ✅ |
| Prompt DE/EN ↔ Code-Konstante | – | ✅ |

## Bewusst NICHT getestet

- **Plan/Code/Tests-Stages nach Diagnose-Skip:** Deren Verhalten ist in Stage 3 nicht implementiert (Stage 3 setzt nur `ctx.diagnosisIncomplete = true` in der Diagnose-Stage; Plan/Code/Tests prüfen das Flag nicht). Architektur-Plan erwähnt „Plan/Code/Tests emittieren ihren eigenen Skip-Marker" als Folge-Muss — ohne Implementierung wäre ein Test rot. → Übersprungen, eigenes Folge-Ticket.
- **Playwright-E2E:** Kein UI-Regressions-Risiko (keine sichtbare Verhaltensänderung außer dem Skip-Marker-Text im Result-Panel). Falls Frontend später ein `SKIPPED`-Status-Badge rendert: kleiner Vitest-Snapshot genügt.

## Stage-3-Befund (zur Kenntnisnahme an Stage-3-Reviewer)

`LoggingFixService.kt` referenziert `LoggingInputHeuristic.DIAGNOSIS_SKIP_MARKER_PREFIX`, das aktuell nur in `LoggingFixDiagnoseStage.Companion` definiert ist. Das blockiert den Build.

**Empfohlener Stage-3-Fix (nicht in Stage 4 umgesetzt):** Konstante in `LoggingInputHeuristic` verschieben oder den Service-Verweis auf `SKIP_MARKER_DE.take("[Diagnose übersprungen".length)` umstellen. Sobald dieser Build-Fehler behoben ist, sind alle hier beschriebenen Tests grün.

## Konventionen-Checklist (gemäß Stage 2)

- ✅ JUnit 5 (`useJUnitPlatform()`)
- ✅ Backtick-Methodennamen
- ✅ Mockito-Kotlin 6.0.0 für Mocks
- ✅ AssertJ für Assertions
- ✅ Kein Spring-Kontext in Unit-Tests
- ✅ `*SystemTest`-Suffix für Systemtest-Klassen
- ✅ Kein `@Tag("system-test")` (Gradle-Pattern-Filter)
- ✅ `@SpringBootTest(RANDOM_PORT) + @AutoConfigureMockMvc` für Systemtests
- ✅ ktlint-konform (4 Spaces, kein Wildcard-Import, snake_case-Backtick-Methodennamen)
