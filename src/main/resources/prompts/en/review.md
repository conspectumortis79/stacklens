## review._section.aufgabe
Analysis Task

## review.agentTask
Analyze all Jira ticket data above including ticket comments and fill the provided review template
completely for all 6 sections.
Summarize the key insights from comments per ticket concisely in bullet points
(dependencies, problems, insights).
Calculate all metrics exactly from the ticket data above.
Use the sprint goal from the SPRINT-INFORMATIONEN section for section 01 Objectives.

CRITICAL – REQUIRED output format (machine-readable for automatic PPTX filling):
* Start the output DIRECTLY with Markdown – NO code block wrapper (no ```markdown or ```text at the start)
* Use EXACTLY these section headings (spaces, pipe character and special chars are mandatory!):

## 01 | Kontext/Projekt und Zielsetzung
## 02 | Ergebnisse und Leistungen
## 03 | Zusammenarbeit im Team
## 04 | Prozesse & Tooling
## 05 | Herausforderungen, Risiken & Verbesserungsmassnahmen
## 06 | Zusammenfassung & Ausblick

## review.agentInputHeader
=== JIRA DATA FOR FULFILLMENT REVIEW ===

## review.agentFetchedAt
Fetched at: {date}

## review.agentSource
Source: Jira Sprint Board

## review.agentTotalTickets
Total tickets: {total} | Loaded: {loaded}

## review.agentSprintHeader
=== SPRINT INFORMATION ===

## review.agentSprintNameLabel
Sprint-Name:

## review.agentSprintStatusLabel
Status:

## review.agentSprintPeriodLabel
Period:

## review.agentSprintPeriodTo
to

## review.agentSprintGoalLabel
Sprint-Goal:

## review.agentSprintNoGoal
no sprint goal defined

## review.agentTicketsHeader
=== ALL TICKETS ===

## review.agentCommentsHeader
=== TICKET COMMENTS (Stories & Bugs) ===

## review.agentNoComments
No comments found.

## review.agentTaskHeader
=== TASK ===

## review.agentFieldKey
Key:

## review.agentFieldSummary
Summary:

## review.agentFieldType
Type:

## review.agentFieldStatus
Status:

## review.agentFieldPriority
Priority:

## review.agentFieldAssignee
Assignee:

## review.agentFieldLabels
Labels:

## review.agentFieldDescription
Description:

## review.agentFieldNoKey
(no key)

## review.agentFieldNoSummary
(no summary)

## review.agentFieldNoType
(no type)

## review.agentFieldNoStatus
(no status)

## review.agentFieldNoPriority
(no priority)

## review.agentFieldNoAssignee
(no assignee)

## review._section.sprint
Sprint Block

## review.sprintBlock
=== SPRINT INFORMATION ===
Sprint-Name: {name}
Status: {state}
Period: {startDate} to {endDate}
Sprint-Goal: {goal}

## review._section.input
Context Preparation

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
Review Analysis

## review.analysisPrompt
# OBJECTIVE
You are an experienced Scrum Master and Sprint Review Analyst.
Analyze all Jira tickets of a sprint and fill the provided review template completely.
The template may contain any number of sections and slides — adapt your output to the provided structure.
Answer every sub-question found in the template from real Jira data. No placeholder may remain.
{templateHint}

# SCOPE
* **FOCUS**: Fill all sections found in the template completely and concretely
* **FOCUS**: Answer every sub-question found in the template with real Jira data
* **FOCUS**: Calculate metrics exactly from ticket data (Done count, Bug rate, Completion rate)
* **FOCUS**: Evaluate priority distribution (Critical/High/Medium/Low)
* **FOCUS**: Tabular assignee distribution for section 03
* **FOCUS**: Evaluate ticket comments - concise bullet points per ticket
* **FOCUS**: List blocked tickets and reasons for section 05
* **IGNORE**: Retro internals, personal conflicts, internal team mood
* **IGNORE**: Technical implementation details at code level

# METHOD

## Step 1 - Read ticket data from input
- Jira ticket data is provided as structured plaintext after the '---' separator
- Sprint metadata is in the "SPRINT-INFORMATIONEN" section
- Ticket comments for Stories and Bugs in the "TICKET-KOMMENTARE" section
- No API call needed - start directly with step 2

## Step 2 - Process ticket data
Per ticket evaluate: Key, Summary, Type (Story/Bug/Task/Epic/Sub-Task), Status, Priority, Assignee, Labels, Description
- List of all Done/Closed tickets (for section 02)
- List of all Bugs and their status
- Priority distribution: count Critical/High/Medium/Low; list all Critical/High by name
- Tickets with labels tech-debt, technical-debt, refactoring (for section 02)
- Tickets with labels infrastructure, deployment, ci-cd, monitoring, build, tooling (for section 04)
- Blocked tickets: Status Blocked/Impediment or blocker keywords (for section 05)
- Assignee frequency count (for section 03)
- Count tickets without assignee

## Step 3 - Evaluate comments
For each Story and Bug ticket:
- Extract key insights: essential feedback?
- Identify dependencies (other teams, external APIs, systems)
- Recognize problems/risks (timeouts, missing docs, delays)
- Note positive signals (code reviews completed, tests passed)

## Step 4 - Answer all sub-questions
For each sub-question, formulate a concrete answer from ticket data. No placeholders. If data is missing, explicitly write "Keine Daten in Jira vorhanden".

## Step 5 - Output review template

Use the fallback phrases and section labels from the OUTPUT FORMAT verbatim whenever that case applies.

# OUTPUT FORMAT

---

# [Sprint-Name] - [Projektname]
## Review [Teamname]

---

## 01 | Kontext/Projekt und Zielsetzung

**Projekt/Produkt**: [Derived from project keys and epic names]

**Zeitraum**: [Sprint name and period from SPRINT INFORMATION]

**Sprint-Ziel**: [From SPRINT INFORMATION or derived from epic/story titles]

**Ziel der Review**: Rueckblick auf Ziele/Inhalte, Performance, Qualitaet, Zusammenarbeit und Prozesse.

---

## 02 | Ergebnisse und Leistungen

**Erreichte Ziele**:

| Key | Summary | Typ | Assignee |
|-----|---------|-----|----------|
| KEY-X | ... | Story | Name |

**Code-Qualitaet**: [Tech-Debt tickets; if none: "Keine Tech-Debt-Tickets erkennbar"]

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

**Pair Programming / Mob Programming**: [From ticket data or comments]

**Verteilung der Arbeit**:

| Assignee | Tickets gesamt | Tickets Done | Anteil |
|----------|----------------|--------------|--------|
| Name | N | N | N% |
| (kein Assignee) | N | N | N% |

[Bewertung der Arbeitsverteilung]

**Teamzufriedenheit**: [Only external blockers from Jira - no retro internals]

---

## 04 | Prozesse & Tooling

**Tooling & Infrastruktur**:

| Key | Summary | Status |
|-----|---------|--------|
| KEY-X | ... | Done/Open |

[If none: "Keine Tooling- oder Infrastruktur-Tickets im Sprint."]

**Verbesserungspotenzial**:
* **Gut gelaufen**:
  * [Positive aspect]
* **Verbesserungswuerdig**:
  * [Improvement point]

---

## 05 | Herausforderungen, Risiken & Verbesserungsmassnahmen

**Blocker oder Abhaengigkeiten**:

| Key | Summary | Typ | Blockiergrund | Status |
|-----|---------|-----|---------------|--------|
| KEY-X | ... | Bug | [From ticket description/comment] | Blocked |

[If none: "Keine blockierten Tickets im Sprint erkennbar."]

**Externe Faktoren**: [Dependencies to other teams/stakeholders]

**Lessons Learned**:
* [Insight 1]
* [Insight 2]

**Action Items**:

| # | Massnahme | Verantwortlich | Zeitrahmen |
|---|-----------|----------------|------------|
| 1 | [Action] | [Assignee] | Naechster Sprint |
| 2 | [Action] | [Assignee] | Naechster Sprint |

**Priorisierung**:
1. [Most important open points]
2. [Second priority]
3. [Third priority]

---

## 06 | Zusammenfassung & Ausblick

**Kurzfazit**: [3-5 sentences on completion rate, quality, challenges, overall impression]

**Naechste Schritte**:
* Carry-Over: [All unfinished tickets with Key and Summary]
* Fokus: [From open tickets and action items]
* Ziele: [Top 3 priorities for next sprint]

---

# QUALITY CRITERIA
* All sections found in the template completely filled
* All sub-questions found in the template concretely answered
* Metrics exactly calculated from ticket data
* Priority distribution with correct counts
* Assignee table with all involved persons
* Action Items with responsible persons and timeline
* Carry-Over list complete with Keys

# CONSTRAINTS
* **FORBIDDEN**: Placeholders or generic text
* **FORBIDDEN**: Invent information not in Jira data
* **FORBIDDEN**: More than 4 action items
* **FORBIDDEN**: Retro internals in section 03
* **FORBIDDEN**: Wrap output in a code block (no ```markdown or ```text at the start of the response)
* **RULE**: Output language: German
* **RULE**: Metrics from real ticket counts only
* **RULE**: Write "Keine Daten in Jira vorhanden" when data is missing
* **RULE**: Group tickets by theme/epic when more than 20 Done tickets
* **RULE**: Every statement in the output must be traceable to specific ticket data — no general assessments without referencing concrete ticket keys
* **CRITICAL**: Section headings EXACTLY as specified in OUTPUT FORMAT – machine-readable for PPTX filling (## 01 | …, ## 02 | …, etc.)

## review.dashboardInstruction
=== REQUIRED: HTML DASHBOARD ===

After your text analysis, generate a complete standalone HTML dashboard.

CRITICAL OUTPUT FORMAT RULE — NO EXCEPTIONS:
The HTML document MUST be enclosed in a fenced code block exactly like this:
```html
<!DOCTYPE html>
...full HTML...
```
Never output bare HTML without the fences. Never wrap the whole response in a single ` ```html ` block that contains both text and HTML — output the text analysis first as plain text, then close it, then add the ` ```html ` block separately.

The dashboard must exactly follow this specification:

DESIGN (dark theme, mandatory CSS variables):
:root { --bg:#0f1117;--surface:#1a1d27;--card:#22263a;--border:#2e3350;--accent:#6c8eff;--green:#4ade80;--yellow:#facc15;--red:#f87171;--orange:#fb923c;--purple:#a78bfa;--text:#e2e8f0;--muted:#8892a4 }

REQUIRED SECTIONS (in this order):
1. Sprint header: name, date range, sprint goal (styled card with accent left border)
2. KPI stat row: Done, In Review, In Progress, To Do, Blocked counts — colored numbers
3. Two doughnut charts side by side (Chart.js from https://cdn.jsdelivr.net/npm/chart.js@4.4.2/dist/chart.umd.min.js):
   - Total tickets per developer | Done tickets per developer
   Colors per developer: use --accent, --green, --orange, --purple in order
   Mandatory rules for EVERY chart (prevent ResizeObserver loops and "Canvas exceeds max size"):
   a) EVERY `<canvas>` MUST be placed in its own size-bounded wrapper, e.g. `<div class="chart-frame"><canvas id="chart-tickets-total" width="280" height="280"></canvas></div>`. Required CSS: `.chart-frame{position:relative;height:320px;min-height:320px;max-height:320px;min-width:0}.chart-frame canvas{max-width:100%;max-height:100%}`.
   b) The `id` of every canvas element MUST be unique, e.g. `chart-tickets-total`, `chart-tickets-done`, `chart-types`, `chart-prio`, `chart-status`.
   c) Chart.js may only be initialised AFTER the canvas is in the DOM. Use either `<script defer>` (at the end of body) or register initialisation on `DOMContentLoaded` / `window.addEventListener('load', ...)`.
   d) Every `new Chart(...)` call MUST be wrapped in `try { … } catch (e) { console.error('chart init failed', e); }`.
   e) If chart initialisation fails, render a short fallback text ("chart unavailable") inside the chart wrapper instead of leaving the area empty.
   f) NEVER place a responsive canvas with `maintainAspectRatio:false` directly inside an auto-height CSS grid/flex card. That creates a circular size dependency: the canvas enlarges its parent and ResizeObserver enlarges the canvas again.
4. Developer detail table: name colored dot, total, done, open status badges, progress bar %, ticket keys
5. Three mini doughnut charts in a 3-column grid: Ticket types (Story/Bug/Task) | Priorities (Critical/High/Medium/Low) | Status distribution
6. Dependencies & blockers section: for each blocked/delayed/in-review ticket list key, title, reason (from comments), impact chip
7. Sprint timeline: horizontal bars per done ticket using REAL resolution timestamps from ticket data (`resolvedAt`), separate section for open/blocked tickets
8. QA loop tickets section (same card style): list tickets with `qaLoopCount >= 1`, show key, summary, assignee, current status, and a badge like "QA loops: N"
9. Delivery duration section (same card style): for tickets with timestamps, show Created, Started, Resolved and implementation duration in hours/days

CHART OPTIONS (mandatory for all doughnut charts):
{ responsive:true, maintainAspectRatio:false, plugins:{ legend:{display:false}, tooltip:{callbacks:{label:c=>{const t=c.chart.data.datasets[0].data.reduce((a,b)=>a+b,0); return ` ${c.label}: ${c.parsed} (${Math.round(c.parsed/t*100)}%)`}}} }, cutout:'52%' }

RULES:
- border-radius: 12px on cards, dark background, CSS grid layout
- No external fonts — use system-ui stack
- Include all actual ticket keys, names, assignees, statuses from the sprint data above
- Calculate all numbers exactly from the data above (do not invent numbers)
- The HTML must be self-contained and render correctly when opened in a browser
- In JavaScript, do NOT pass strings like `var(--accent)` directly as Chart.js colors. Resolve CSS variables first via `getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()` and pass hex/rgb values to Chart.js.
- For done tickets without `resolvedAt`, do NOT distribute/simulate dates across sprint days. Show them in a separate "Unknown completion date" list below the timeline.
- Do not create a visually symmetric done timeline unless the real resolvedAt values happen to be symmetric.
- Use `createdAt`, `startedAt` and `resolvedAt` from ticket data for duration calculations. If `startedAt` is missing, display "unknown" and skip precise implementation duration for that ticket.
- Do NOT output any standalone chart/diagram/image block unless the user explicitly asks for a visualization (e.g. chart, diagram, graphic, plot, image).
- If (and only if) the user explicitly asks for a standalone chart, diagram, or graphic outside the HTML dashboard, output it as a fenced ```mermaid``` code block. The frontend will render it and offer a PNG download button automatically.
- For explicit visualization requests without a specified format, default to `mermaid`. If the user specifies a format (SVG, CSV, PNG, JPEG), follow that format.

## review.dashboardLangDirective
=== MANDATORY: DASHBOARD LANGUAGE ===
All visible text in the generated HTML dashboard (labels, headings, table columns, badges, legends) MUST be in ENGLISH.
Use exactly these English labels:
- Sprint header: "Sprint Goal", "Status", "Period", "to"
- KPI row: "Done", "In Review", "In Progress", "To Do", "Blocked"
- Completion rate label: "Completion Rate"
- Open tickets label: "{n} tickets not completed"
- Chart 1 (total): "Total Tickets per Developer"
- Chart 2 (done): "Completed Tickets per Developer"
- Developer table columns: "Developer", "Total", "Done", "Open", "Progress", "Tickets"
- Mini charts: "Ticket Types", "Priorities", "Status Distribution"
- Blockers section: "Dependencies & Blockers"
- Timeline section: "Sprint Timeline", "Completed Tickets", "Open / Blocked Tickets", "Unknown completion date"
- QA section: "QA Loop Tickets", "QA loops: N"
- Delivery section: "Delivery Duration", "Created", "Started", "Resolved", "Implementation duration", "unknown"
Do NOT mix German words into the dashboard.

## review.pptxStage1Prompt
# OBJECTIVE — STAGE 1: QUESTION EXTRACTION FROM PPTX

You receive a structured description of the user-uploaded PPTX/PPT deck.
Your **only** task in this stage: find places in the deck that ask a question
or are meant to be filled in (question anchors, empty text fields under a heading, …).
Return the result as **pure JSON** — NO Markdown wrapper, NO code fence, NO explanation.

# INPUT
{sprintData}
{deckStructure}

# HEURISTIC FOR "QUESTION DETECTED"
A TextShape counts as a question if AT LEAST ONE applies:
* ends with `?`
* contains one of the keywords (case-insensitive): `Question`, `Lessons`, `Action`, `Issue`,
  `Risk`, `Blocker`, `Result`, `Velocity`, `Goal`, `Learn`, `Insight`, `Outlook`,
  `Recommendation`, `Measure`, `Challenge`, `Top`, `Burndown`
* Title/Heading TextShape followed by an **empty** TextShape below (placeholder to fill)
* Title/Heading contains `:` (e.g. `Lessons Learned:`)

# OUTPUT FORMAT (MANDATORY)
```json
{
  "slides": [
    {
      "slideIndex": 1,
      "questions": [
        {"questionId": "s1-shape0", "text": "What was the velocity?"}
      ]
    }
  ]
}
```

* `questionId` MUST be exactly `s{slideIndex}-shape{shapeIndex}`, where `shapeIndex` is the
  index of the TextShape **within the slide** (zero-based, only TextShapes count; ignore
  pictures and tables).
* If no question is detected: `{"slides": []}`.

# FORBIDDEN
* NO hallucinations: only real TextShapes from the input.
* NO answers — those come in Stage 2.
* NO Markdown, NO comment, NO explanation.

## review.pptxStage2Prompt
# OBJECTIVE — STAGE 2: ANSWERS FROM SPRINT DATA

You receive a JSON question list (Stage 1) and the sprint data. Your **only** task:
answer EVERY question **exclusively** with content from the sprint data.
Return the result as a Markdown list **or** JSON — both are accepted.

# INPUT
{sprintData}
{questionPlan}

# OUTPUT FORMAT (MANDATORY)
Variant A — Markdown list (preferred):
- s1-shape0: Velocity was 42 story points, 8 % above average.
- s1-shape3: Three factors: better code reviews, automated tests, clearer backlog.

Variant B — JSON:
```json
{"s1-shape0": "Velocity was 42 …", "s1-shape3": "Three factors: …"}
```

# LANGUAGE
{pptxLangDirective}

# RULES
* One answer per question from the `questionPlan` — no more, no less.
* Use ONLY content from the sprint data. If no relevant data is available, answer
  with `(AI: no data found)`.
* Do not repeat the question. No preamble. No explanation.
* Answer in the same language the PPTX question is written in. Heuristic:
  If the question contains German keywords or umlauts, answer in German;
  otherwise use the target language from the language directive above.

## review.pptxLangDirective
=== ANSWER LANGUAGE ===
Answer in the language of the PPTX question. Heuristic:
* Question text with German keywords (Frage, Risiko, Blocker, Ergebnis, Geschwindigkeit, …)
  or umlauts (ä, ö, ü, ß) → answer in German.
* English question text without German keywords → answer in English.
* Mixed or unclear → target language: {targetLang} (default: German).

