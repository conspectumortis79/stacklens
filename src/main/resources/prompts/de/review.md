## review._section.aufgabe
Analyseaufgabe

## review.agentTask
Analysiere alle Jira-Ticket-Daten oben einschließlich der Ticket-Kommentare und fülle die vorgegebene Review-Vorlage
vollständig für alle 6 Abschnitte aus.
Fasse die wichtigsten Erkenntnisse aus Kommentaren pro Ticket prägnant in Stichpunkten zusammen
(Abhängigkeiten, Probleme, Erkenntnisse).
Berechne alle Metriken exakt aus den Ticket-Daten oben.
Verwende das Sprint-Ziel aus dem Abschnitt SPRINT-INFORMATIONEN für Abschnitt 01 Zielsetzung.

KRITISCH – ERFORDERLICHES Ausgabeformat (maschinenlesbar für automatische PPTX-Befüllung):
* Beginne die Ausgabe DIREKT mit Markdown – KEIN Code-Block-Wrapper (kein ```markdown oder ```text am Anfang)
* Verwende EXAKT diese Abschnittsüberschriften (Leerzeichen, Pipe-Zeichen und Sonderzeichen sind Pflicht!):

## 01 | Kontext/Projekt und Zielsetzung
## 02 | Ergebnisse und Leistungen
## 03 | Zusammenarbeit im Team
## 04 | Prozesse & Tooling
## 05 | Herausforderungen, Risiken & Verbesserungsmassnahmen
## 06 | Zusammenfassung & Ausblick

## review.agentInputHeader
=== JIRA-DATEN FÜR FULFILLMENT REVIEW ===

## review.agentFetchedAt
Abgerufen am: {date}

## review.agentSource
Quelle: Jira Sprint Board

## review.agentTotalTickets
Tickets gesamt: {total} | Geladen: {loaded}

## review.agentSprintHeader
=== SPRINT-INFORMATIONEN ===

## review.agentSprintNameLabel
Sprint-Name:

## review.agentSprintStatusLabel
Status:

## review.agentSprintPeriodLabel
Zeitraum:

## review.agentSprintPeriodTo
bis

## review.agentSprintGoalLabel
Sprint-Ziel:

## review.agentSprintNoGoal
kein Sprint-Ziel definiert

## review.agentTicketsHeader
=== ALLE TICKETS ===

## review.agentCommentsHeader
=== TICKET-KOMMENTARE (Stories & Bugs) ===

## review.agentNoComments
Keine Kommentare gefunden.

## review.agentTaskHeader
=== AUFGABE ===

## review.agentFieldKey
Key:

## review.agentFieldSummary
Zusammenfassung:

## review.agentFieldType
Typ:

## review.agentFieldStatus
Status:

## review.agentFieldPriority
Priorität:

## review.agentFieldAssignee
Assignee:

## review.agentFieldLabels
Labels:

## review.agentFieldDescription
Beschreibung:

## review.agentFieldNoKey
(kein Key)

## review.agentFieldNoSummary
(keine Zusammenfassung)

## review.agentFieldNoType
(kein Typ)

## review.agentFieldNoStatus
(kein Status)

## review.agentFieldNoPriority
(keine Priorität)

## review.agentFieldNoAssignee
(kein Assignee)

## review._section.sprint
Sprint-Block

## review.sprintBlock
=== SPRINT-INFORMATIONEN ===
Sprint-Name: {name}
Status: {state}
Zeitraum: {startDate} bis {endDate}
Sprint-Ziel: {goal}

## review._section.input
Kontextvorbereitung

## review.buildAgentInput
{inputHeader}
{fetchedAt}
{source}
{totalTickets}

{sprintBlock}
{ticketsHeader}
{ticketsBlock}

{commentsHeader}
{commentsBlock}

{taskHeader}
{agentTask}

## review._section.analysis
Review-Analyse

## review.analysisPrompt
# ZIEL
Du bist ein erfahrener Scrum Master und Sprint-Review-Analyst.
Analysiere alle Jira-Tickets eines Sprints und fülle die vorgegebene Review-Vorlage vollständig aus.
Die Vorlage kann beliebig viele Abschnitte und Folien umfassen — passe deine Ausgabe an die vorgegebene Struktur an.
Beantworte jede in der Vorlage gefundene Unterfrage aus realen Jira-Daten. Kein Platzhalter darf bestehen bleiben.
{templateHint}

# UMFANG
* **FOKUS**: Alle in der Vorlage gefundenen Abschnitte vollständig und konkret ausfüllen
* **FOKUS**: Jede in der Vorlage gefundene Unterfrage mit echten Jira-Daten beantworten
* **FOKUS**: Metriken exakt aus Ticket-Daten berechnen (Done-Anzahl, Bug-Rate, Abschlussrate)
* **FOKUS**: Prioritätsverteilung auswerten (Critical/High/Medium/Low)
* **FOKUS**: Tabellarische Assignee-Verteilung für Abschnitt 03
* **FOKUS**: Ticket-Kommentare auswerten – prägnante Stichpunkte pro Ticket
* **FOKUS**: Blockierte Tickets und Gründe für Abschnitt 05 auflisten
* **IGNORIEREN**: Retro-Interna, persönliche Konflikte, interne Teamstimmung
* **IGNORIEREN**: Technische Implementierungsdetails auf Code-Ebene

# METHODE

## Schritt 1 – Ticket-Daten aus Input lesen
- Jira-Ticket-Daten werden als strukturierter Klartext nach dem '---'-Trenner übergeben
- Sprint-Metadaten befinden sich im Abschnitt "SPRINT-INFORMATIONEN"
- Ticket-Kommentare für Stories und Bugs im Abschnitt "TICKET-KOMMENTARE"
- Kein API-Aufruf nötig – direkt mit Schritt 2 beginnen

## Schritt 2 – Ticket-Daten verarbeiten
Pro Ticket auswerten: Key, Summary, Typ (Story/Bug/Task/Epic/Sub-Task), Status, Priorität, Assignee, Labels, Beschreibung
- Liste aller Done/Closed-Tickets (für Abschnitt 02)
- Liste aller Bugs und deren Status
- Prioritätsverteilung: Critical/High/Medium/Low zählen; alle Critical/High namentlich auflisten
- Tickets mit Labels tech-debt, technical-debt, refactoring (für Abschnitt 02)
- Tickets mit Labels infrastructure, deployment, ci-cd, monitoring, build, tooling (für Abschnitt 04)
- Blockierte Tickets: Status Blocked/Impediment oder Blocker-Schlüsselwörter (für Abschnitt 05)
- Assignee-Häufigkeitszählung (für Abschnitt 03)
- Tickets ohne Assignee zählen

## Schritt 3 – Kommentare auswerten
Für jedes Story- und Bug-Ticket:
- Wichtigste Erkenntnisse extrahieren: wesentliches Feedback?
- Abhängigkeiten identifizieren (andere Teams, externe APIs, Systeme)
- Probleme/Risiken erkennen (Timeouts, fehlende Docs, Verzögerungen)
- Positive Signale notieren (Code-Reviews abgeschlossen, Tests bestanden)

## Schritt 4 – Alle Unterfragen beantworten
Für jede Unterfrage eine konkrete Antwort aus Ticket-Daten formulieren. Keine Platzhalter. Falls Daten fehlen, explizit "Keine Daten in Jira vorhanden" schreiben.

## Schritt 5 – Review-Vorlage ausgeben

Verwende die Fallback-Phrasen und Abschnittsbezeichnungen aus dem AUSGABEFORMAT wörtlich, wenn dieser Fall zutrifft.

# AUSGABEFORMAT

---

# [Sprint-Name] - [Projektname]
## Review [Teamname]

---

## 01 | Kontext/Projekt und Zielsetzung

**Projekt/Produkt**: [Aus Projekt-Keys und Epic-Namen abgeleitet]

**Zeitraum**: [Sprint-Name und Zeitraum aus SPRINT-INFORMATIONEN]

**Sprint-Ziel**: [Aus SPRINT-INFORMATIONEN oder aus Epic-/Story-Titeln abgeleitet]

**Ziel der Review**: Rueckblick auf Ziele/Inhalte, Performance, Qualitaet, Zusammenarbeit und Prozesse.

---

## 02 | Ergebnisse und Leistungen

**Erreichte Ziele**:

| Key | Summary | Typ | Assignee |
|-----|---------|-----|----------|
| KEY-X | ... | Story | Name |

**Code-Qualitaet**: [Tech-Debt-Tickets; falls keine: "Keine Tech-Debt-Tickets erkennbar"]

**Metriken**:
* Gesamttickets im Sprint: [N]
* Erledigt (Done/Closed): [N] ([N]%)
* In Bearbeitung: [N]
* Offen: [N]
* Bugs gesamt: [N] | behoben: [N] | offen: [N]
* Bug-Rate: [N]%

**Prioritaetsverteilung**:
* Critical: [N] Tickets
* High: [N] Tickets
* Medium: [N] Tickets
* Low: [N] Tickets

**Sprint-Schwerpunkt (Top-Stories nach Backlog-Reihenfolge)**:

| # | Key | Summary | Status |
|---|-----|---------|--------|
| 1 | KEY-X | ... | Done |

**Hoechst-priorisierte Tickets (Critical und High)**:

| Key | Summary | Typ | Prioritaet | Status |
|-----|---------|-----|------------|--------|
| KEY-X | ... | Story | High | Done |

---

## 03 | Zusammenarbeit im Team

**Pair Programming / Mob Programming**: [Aus Ticket-Daten oder Kommentaren]

**Verteilung der Arbeit**:

| Assignee | Tickets gesamt | Tickets Done | Anteil |
|----------|----------------|--------------|--------|
| Name | N | N | N% |
| (kein Assignee) | N | N | N% |

[Bewertung der Arbeitsverteilung]

**Teamzufriedenheit**: [Nur externe Blocker aus Jira – keine Retro-Interna]

---

## 04 | Prozesse & Tooling

**Tooling & Infrastruktur**:

| Key | Summary | Status |
|-----|---------|--------|
| KEY-X | ... | Done/Offen |

[Falls keine: "Keine Tooling- oder Infrastruktur-Tickets im Sprint."]

**Verbesserungspotenzial**:
* **Gut gelaufen**:
  * [Positiver Aspekt]
* **Verbesserungswuerdig**:
  * [Verbesserungspunkt]

---

## 05 | Herausforderungen, Risiken & Verbesserungsmassnahmen

**Blocker oder Abhaengigkeiten**:

| Key | Summary | Typ | Blockiergrund | Status |
|-----|---------|-----|---------------|--------|
| KEY-X | ... | Bug | [Aus Ticket-Beschreibung/Kommentar] | Blocked |

[Falls keine: "Keine blockierten Tickets im Sprint erkennbar."]

**Externe Faktoren**: [Abhängigkeiten zu anderen Teams/Stakeholdern]

**Lessons Learned**:
* [Erkenntnis 1]
* [Erkenntnis 2]

**Action Items**:

| # | Massnahme | Verantwortlich | Zeitrahmen |
|---|-----------|----------------|------------|
| 1 | [Maßnahme] | [Assignee] | Naechster Sprint |
| 2 | [Maßnahme] | [Assignee] | Naechster Sprint |

**Priorisierung**:
1. [Wichtigste offene Punkte]
2. [Zweite Priorität]
3. [Dritte Priorität]

---

## 06 | Zusammenfassung & Ausblick

**Kurzfazit**: [3–5 Sätze zu Abschlussrate, Qualität, Herausforderungen, Gesamteindruck]

**Naechste Schritte**:
* Carry-Over: [Alle unerledigten Tickets mit Key und Summary]
* Fokus: [Aus offenen Tickets und Action Items]
* Ziele: [Top 3 Prioritäten für den nächsten Sprint]

---

# QUALITÄTSKRITERIEN
* Alle in der Vorlage gefundenen Abschnitte vollständig ausgefüllt
* Alle in der Vorlage gefundenen Unterfragen konkret beantwortet
* Metriken exakt aus Ticket-Daten berechnet
* Prioritätsverteilung mit korrekten Zählungen
* Assignee-Tabelle mit allen beteiligten Personen
* Action Items mit Verantwortlichen und Zeitrahmen
* Carry-Over-Liste vollständig mit Keys

# EINSCHRÄNKUNGEN
* **VERBOTEN**: Platzhalter oder generischer Text
* **VERBOTEN**: Informationen erfinden, die nicht in den Jira-Daten enthalten sind
* **VERBOTEN**: Mehr als 4 Action Items
* **VERBOTEN**: Retro-Interna in Abschnitt 03
* **VERBOTEN**: Ausgabe in einen Code-Block einwickeln (kein ```markdown oder ```text am Anfang der Antwort)
* **REGEL**: Ausgabesprache: Deutsch
* **REGEL**: Metriken nur aus echten Ticket-Zählungen
* **REGEL**: "Keine Daten in Jira vorhanden" schreiben, wenn Daten fehlen
* **REGEL**: Tickets nach Thema/Epic gruppieren, wenn mehr als 20 Done-Tickets vorhanden
* **REGEL**: Jede Aussage in der Ausgabe muss auf spezifische Ticket-Daten zurückführbar sein – keine allgemeinen Einschätzungen ohne Verweis auf konkrete Ticket-Keys
* **KRITISCH**: Abschnittsüberschriften EXAKT wie im AUSGABEFORMAT angegeben – maschinenlesbar für PPTX-Befüllung (## 01 | …, ## 02 | …, usw.)

## review.dashboardInstruction
=== PFLICHT: HTML-DASHBOARD ===

Generiere nach deiner Textanalyse ein vollständiges, eigenständiges HTML-Dashboard.

KRITISCHE AUSGABEREGEL — KEINE AUSNAHMEN:
Das HTML-Dokument MUSS in einem Fenced-Code-Block genau wie folgt eingeschlossen sein:
```html
<!DOCTYPE html>
...vollständiges HTML...
```
Gib niemals nacktes HTML ohne die Fences aus. Wickle die gesamte Antwort NICHT in einen einzigen ` ```html `-Block ein, der sowohl Text als auch HTML enthält — gib die Textanalyse zuerst als Klartext aus, schließe sie dann ab, und füge danach separat den ` ```html `-Block hinzu.

Das Dashboard muss exakt dieser Spezifikation folgen:

DESIGN (dunkles Theme, Pflicht-CSS-Variablen):
:root { --bg:#0f1117;--surface:#1a1d27;--card:#22263a;--border:#2e3350;--accent:#6c8eff;--green:#4ade80;--yellow:#facc15;--red:#f87171;--orange:#fb923c;--purple:#a78bfa;--text:#e2e8f0;--muted:#8892a4 }

PFLICHTABSCHNITTE (in dieser Reihenfolge):
1. Sprint-Header: Name, Zeitraum, Sprint-Ziel (gestylte Card mit farbigem linken Rand)
2. KPI-Zeile: Fertig, In Review, In Bearbeitung, To Do, Blockiert — farbige Zahlen
3. Zwei Donut-Diagramme nebeneinander (Chart.js von https://cdn.jsdelivr.net/npm/chart.js@4.4.2/dist/chart.umd.min.js):
   - Tickets pro Entwickler (gesamt) | Abgeschlossene Tickets pro Entwickler
   Farben pro Entwickler: --accent, --green, --orange, --purple der Reihe nach
   Pflichtregeln für JEDES Chart (verhindert ResizeObserver-Schleifen und "Canvas exceeds max size"):
   a) JEDES `<canvas>` MUSS in einem eigenen, größenbegrenzten Wrapper liegen, z.B. `<div class="chart-frame"><canvas id="chart-tickets-total" width="280" height="280"></canvas></div>`. Pflicht-CSS: `.chart-frame{position:relative;height:320px;min-height:320px;max-height:320px;min-width:0}.chart-frame canvas{max-width:100%;max-height:100%}`.
   b) Die `id` jedes Canvas-Elements MUSS eindeutig sein, z.B. `chart-tickets-total`, `chart-tickets-done`, `chart-types`, `chart-prio`, `chart-status`.
   c) Chart.js darf NUR initialisiert werden NACHDEM das Canvas im DOM hängt. Verwende entweder `<script defer>` (am Ende des body) oder registriere die Initialisierung auf `DOMContentLoaded` / `window.addEventListener('load', ...)`.
   d) Jeder `new Chart(...)`-Aufruf MUSS in `try { … } catch (e) { console.error('chart init failed', e); }` gekapselt sein.
   e) Wenn die Chart-Initialisierung fehlschlägt, zeige im Chart-Wrapper einen kurzen Fallback-Text ("Diagramm nicht verfügbar") an, statt die Seite leer zu lassen.
   f) Setze ein responsives Canvas mit `maintainAspectRatio:false` NIEMALS direkt in eine auto-hohe CSS-Grid-/Flex-Card. Das erzeugt eine zirkuläre Größenabhängigkeit: Canvas vergrößert Parent, ResizeObserver vergrößert Canvas erneut.
4. Entwickler-Detailtabelle: Name mit farbigem Punkt, Gesamt, Fertig, Offene Status-Badges, Fortschrittsbalken %, Ticket-Keys
5. Drei Mini-Donut-Diagramme in einem 3-Spalten-Grid: Ticket-Typen (Story/Bug/Task) | Prioritäten (Critical/High/Medium/Low) | Status-Verteilung
6. Abhängigkeiten & Blocker: für jedes blockierte/verzögerte/in-review Ticket Key, Titel, Grund (aus Kommentaren), Impact-Chip auflisten
7. Sprint-Timeline: horizontale Balken pro fertigem Ticket anhand ECHTER Auflösungs-Timestamps aus den Ticket-Daten (`resolvedAt`), separater Abschnitt für offene/blockierte Tickets
8. QA-Schleifen-Tickets (gleicher Card-Stil): Tickets mit `qaLoopCount >= 1` auflisten, Key, Summary, Assignee, aktueller Status und Badge "QA-Schleifen: N" anzeigen
9. Lieferzeit-Abschnitt (gleicher Card-Stil): für Tickets mit Timestamps Erstellt, Gestartet, Abgeschlossen und Implementierungsdauer in Stunden/Tagen anzeigen

DIAGRAMM-OPTIONEN (Pflicht für alle Donut-Diagramme):
{ responsive:true, maintainAspectRatio:false, plugins:{ legend:{display:false}, tooltip:{callbacks:{label:c=>{const t=c.chart.data.datasets[0].data.reduce((a,b)=>a+b,0); return ` ${c.label}: ${c.parsed} (${Math.round(c.parsed/t*100)}%)`}}} }, cutout:'52%' }

REGELN:
- border-radius: 12px auf Cards, dunkler Hintergrund, CSS-Grid-Layout
- Keine externen Fonts — system-ui-Stack verwenden
- Alle echten Ticket-Keys, Namen, Assignees und Status aus den Sprint-Daten oben einbeziehen
- Alle Zahlen exakt aus den Daten oben berechnen (keine Zahlen erfinden)
- Das HTML muss eigenständig sein und korrekt im Browser dargestellt werden
- In JavaScript KEINE Strings wie `var(--accent)` direkt als Chart.js-Farben übergeben. CSS-Variablen zuerst via `getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()` auflösen und Hex-/RGB-Werte an Chart.js übergeben.
- Für fertige Tickets ohne `resolvedAt` KEINE Daten über Sprint-Tage verteilen/simulieren. Diese in einer separaten Liste "Unbekanntes Abschlussdatum" unterhalb der Timeline anzeigen.
- Keine visuell symmetrische Done-Timeline erstellen, außer die echten resolvedAt-Werte sind zufällig symmetrisch.
- `createdAt`, `startedAt` und `resolvedAt` aus den Ticket-Daten für Dauerberechnungen verwenden. Falls `startedAt` fehlt, "unbekannt" anzeigen und die genaue Implementierungsdauer für dieses Ticket überspringen.
- KEINE eigenständigen Diagramm-/Grafik-/Bildblöcke ausgeben, außer der Benutzer fragt explizit nach einer Visualisierung (z.B. Diagramm, Grafik, Plot, Bild).
- Wenn (und nur wenn) der Benutzer explizit nach einem eigenständigen Diagramm oder einer Grafik außerhalb des HTML-Dashboards fragt, als Fenced-` ```mermaid ` ```-Code-Block ausgeben. Das Frontend rendert es automatisch und bietet einen PNG-Download-Button an.
- Für explizite Visualisierungsanfragen ohne angegebenes Format standardmäßig `mermaid` verwenden. Falls der Benutzer ein Format angibt (SVG, CSV, PNG, JPEG), dieses Format verwenden.

## review.dashboardLangDirective
=== PFLICHT: SPRACHE DES DASHBOARDS ===
Alle sichtbaren Texte im generierten HTML-Dashboard (Labels, Überschriften, Tabellenspalten, Badges, Legenden) MÜSSEN auf DEUTSCH sein.
Verwende exakt diese deutschen Bezeichnungen:
- Sprint-Header: "Sprint-Ziel", "Status", "Zeitraum", "bis"
- KPI-Zeile: "Fertig", "In Review", "In Bearbeitung", "To Do", "Blockiert"
- Abschlussrate-Label: "Abschlussrate"
- Nicht abgeschlossene Tickets: "{n} Tickets nicht abgeschlossen"
- Diagramm 1 (gesamt): "Tickets pro Entwickler (gesamt)"
- Diagramm 2 (fertig): "Abgeschlossene Tickets pro Entwickler"
- Entwickler-Tabelle Spalten: "Entwickler", "Gesamt", "Fertig", "Offen", "Fortschritt", "Tickets"
- Mini-Diagramme: "Ticket-Typen", "Prioritäten", "Status-Verteilung"
- Blocker-Abschnitt: "Abhängigkeiten & Blocker"
- Timeline-Abschnitt: "Sprint-Timeline", "Fertige Tickets", "Offene / Blockierte Tickets", "Unbekanntes Abschlussdatum"
- QA-Abschnitt: "QA-Schleifen-Tickets", "QA-Schleifen: N"
- Lieferzeit-Abschnitt: "Lieferzeit", "Erstellt", "Gestartet", "Abgeschlossen", "Implementierungsdauer", "unbekannt"
Mische KEINE englischen Bezeichnungen in das Dashboard.

## review.pptxStage1Prompt
# ZIEL — STAGE 1: FRAGEN-EXTRAKTION AUS PPTX

Du erhältst eine strukturierte Beschreibung der vom Nutzer hochgeladenen PPTX-/PPT-Präsentation.
Deine **einzige** Aufgabe in diesem Schritt: Finde in der Präsentation Stellen, an denen Fragen
gestellt werden oder die zum Ausfüllen gedacht sind (Frage-Anker, leere Textfelder unter einer
Überschrift, …). Liefere das Ergebnis als **reines JSON** — KEIN Markdown-Wrapper, KEIN
Codefence, KEINE Erklärung.

# EINGABE
{sprintData}
{deckStructure}

# HEURISTIK FÜR "FRAGE ERKANNT"
Ein TextShape zählt als Frage, wenn mindestens EINES zutrifft:
* endet mit `?`
* enthält eines der Schlüsselwörter (case-insensitive): `Frage`, `Lessons`, `Action`, `Issue`,
  `Risiko`, `Blocker`, `Ergebnis`, `Velocity`, `Ziel`, `Learn`, `Insight`, `Ausblick`,
  `Empfehlung`, `Massnahme`, `Herausforderung`, `Top`, `Burndown`
* Title/Heading-TextShape mit einem Inhalt, gefolgt von einem **leeren** TextShape darunter
  (Platzhalter zum Ausfüllen)
* Title/Heading enthält `:` (z. B. `Lessons Learned:`)

# AUSGABEFORMAT (PFLICHT)
```json
{
  "slides": [
    {
      "slideIndex": 1,
      "questions": [
        {"questionId": "s1-shape0", "text": "Wie war die Velocity?"}
      ]
    }
  ]
}
```

* `questionId` MUSS exakt `s{slideIndex}-shape{shapeIndex}` sein, wobei `shapeIndex` der Index
  des TextShapes **innerhalb der Folie** ist (nullbasiert, nur TextShapes zählen, Bilder und
  Tabellen NICHT).
* Wenn keine Frage erkennbar: `{"slides": []}`.

# VERBOTEN
* KEINE Halluzination: nur echte TextShapes aus der Eingabe.
* KEINE Antworten — die kommen in Stage 2.
* KEIN Markdown, KEIN Kommentar, KEINE Erklärung.

## review.pptxStage2Prompt
# ZIEL — STAGE 2: ANTWORTEN AUS SPRINT-DATEN

Du erhältst eine JSON-Fragenliste (Stage 1) und die Sprint-Daten. Deine **einzige** Aufgabe:
Beantworte JEDE Frage aus der Liste **ausschließlich** mit Inhalten aus den Sprint-Daten.
Liefere das Ergebnis als Markdown-Liste **oder** JSON — beides ist erlaubt.

# EINGABE
{sprintData}
{questionPlan}

# AUSGABEFORMAT (PFLICHT)
Variante A — Markdown-Liste (bevorzugt):
- s1-shape0: Velocity war 42 Story-Points, 8 % über Schnitt.
- s1-shape3: Drei Faktoren: bessere Code-Reviews, automatisierte Tests, klareres Backlog.

Variante B — JSON:
```json
{"s1-shape0": "Velocity war 42 …", "s1-shape3": "Drei Faktoren: …"}
```

# SPRACHE
{pptxLangDirective}

# REGELN
* Für jede Frage aus dem `questionPlan` eine Antwort — nicht weniger, nicht mehr.
* Verwende NUR Inhalte aus den Sprint-Daten. Wenn keine relevanten Daten vorhanden sind,
  antworte mit `(KI: keine Daten gefunden)`.
* Keine Wiederholung der Frage. Keine Einleitung. Keine Erklärung.
* Antworte in derselben Sprache, in der die PPTX-Frage gestellt wurde. Heuristik:
  Wenn die Frage deutsche Schlüsselwörter oder Umlaute enthält, antworte auf Deutsch;
  sonst in der Zielsprache aus der Sprach-Direktive oben.

## review.pptxLangDirective
=== ANTWORTSPRACHE ===
Antworte in der Sprache der PPTX-Frage. Heuristik:
* Fragetexte mit deutschen Schlüsselwörtern (Frage, Risiko, Blocke, Ergebnis, Geschwindigkeit, …)
  oder Umlauten (ä, ö, ü, ß) → antworte auf Deutsch.
* Englische Fragetexte ohne deutsche Schlüsselwörter → antworte auf Englisch.
* Gemischt oder unklar → Zielsprache: {targetLang} (Standard: Deutsch).

