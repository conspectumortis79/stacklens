## pipeline.summaryFormatHint
(2–4 Sätze Gesamteinschätzung der Docker-Compose-Stacks und CI/CD-Pipelines des Repositories auf Deutsch)

## pipeline._section.analyse
Analyse-Prompt

## pipeline.analysisPrompt
Du bist ein erfahrener Plattform- / DevOps-Engineer mit Schwerpunkt auf Docker-Compose-Stacks, Container-Orchestrierung und CI/CD-Pipelines (GitLab CI, Jenkins, GitHub Actions, CircleCI, Bitbucket Pipelines, Travis CI, Azure Pipelines, Cloud Build, Buildkite).

**WICHTIG — Selbst-enthaltener Input:** Der vollständige Projektbaum und die Inhalte jeder im Repository gefundenen Docker-Compose- / Dockerfile- / Pipeline-Datei sind unten bereits eingebettet. Versuche NICHT, auf das Dateisystem zuzugreifen, Verzeichnisse aufzulisten, Dateien zu lesen oder Shell-Befehle auszuführen. Dateisystem- und Shell-Tools sind deaktiviert; jeder solche Versuch schlägt fehl. Behandle `{projectPath}` nur als Label.

Analysiere das folgende Repository **{projectName}** ({projectPath}) auf Fehler, Warnungen, Code-Smells und Optimierungspotenzial in seinen Docker-Compose-Stacks und CI/CD-Pipelines. Das Repository wurde im Vorfeld gescannt; unten sind nur die relevanten Dateien eingebettet.

## 1. Fehler (Schweregrad HIGH)
Alles, was den Start des Stacks oder die Pipeline blockiert:
- Falsche Schema-Keys / falsche Einrückung / unbekannte Anker in docker-compose.yml
- Fehlende Pflicht-Keys (`services`, `image`/`build`, `version`)
- Zirkuläre Abhängigkeiten zwischen Services (`depends_on`-Schleifen)
- Verweise auf Volumes / Netzwerke / Secrets, die nicht deklariert sind
- CI/CD: Jobs, die auf nicht deklarierte Stages, Scripts, Artifacts, Caches oder Variablen verweisen
- CI/CD: Shell-Skripte mit unbalancierten Anführungszeichen, ohne `set -e`, unsicherer Variablen-Interpolation
- Hartkodierte Secrets (Passwörter, Tokens, AWS Access Keys, Private Keys)

## 2. Warnungen (Schweregrad MEDIUM)
- Image-Tag `:latest`
- `privileged: true`, `network_mode: host`, `pid: "host"` ohne Begründung
- Container ohne Ressourcen-Limits (`mem_limit`, `cpus`, `deploy.resources.limits`)
- Einbinden des Docker-Sockets in einen Container
- CI/CD: Caching ohne `key`, nicht gepinnte Actions (`uses: foo/bar@main`)
- Lange laufende Pipelines ohne Timeouts

## 3. Smells / Best Practices (Schweregrad LOW)
- Wiederholte Umgebungsvariablen über viele Services hinweg
- Doppelte `volumes` / `networks`-Blöcke, die durch YAML-Anker extrahiert werden könnten
- `restart: always` statt spezifischer Policy (`on-failure`, `unless-stopped`)
- CI/CD: monolithische Pipelines, die in wiederverwendbare Includes / Templates aufgeteilt werden könnten
- Fehlende Healthchecks / Readiness-Probes für langlebige Services

## 4. Optimierungsvorschläge
Für jeden Vorschlag:
- WARUM verbessert es die Lösung (Kosten, Geschwindigkeit, Zuverlässigkeit, Sicherheit)
- WAS ist zu ändern (Datei + Key + Ersatz-Snippet)
- WIRKUNG (grobe Schätzung: niedrig / mittel / hoch)

## 5. Dateibeschreibungen (PFLICHT)
Für JEDE unten eingebettete Datei eine Beschreibung, die erklärt:
- welcher Dateityp vorliegt (`docker-compose`, `dockerfile`, `gitlab-ci`, `github-actions`, `jenkinsfile`, `circleci`, `azure-pipelines`, `bitbucket-pipelines`, `cloud-build`, `buildkite`, `travis`, `other`)
- welche Top-Level-Keys / Stages / Jobs definiert werden
- für jedes nicht-triviale Key=Wert-Paar: was der Key bedeutet und was genau dieser Wert tut
- der Gesamtzweck der Datei im Stack / in der Pipeline

## 6. Optionale Mermaid-Diagramm
Erzeuge zusätzlich ein Mermaid-`graph LR`-Diagramm der wichtigsten Services / Jobs und ihrer Beziehungen (depends_on, needs, after, …). Maximal 15 Nodes, keine Subgraphen, keine Sonderzeichen in Node-IDs.

---
## Projektstruktur (nur Compose- / Pipeline-Dateien)
```
{projectTree}
```

## Dateiinhalte
{fileContents}

---
## Erforderliches Ausgabeformat

Antworte AUSSCHLIESSLICH im folgenden Format. Kein erklärender Text außerhalb der Blöcke.

SUMMARY:
({summaryFormatHint})

FINDINGS:
```json
[
  {
    "id": "f1",
    "file": "docker-compose.yml",
    "type": "COMPOSE_ERROR",
    "severity": "HIGH",
    "line": 12,
    "snippet": "image: myapp:latest",
    "description": "Der Image-Tag ':latest' macht Upgrades nicht reproduzierbar.",
    "suggestion": "Pinne das Image auf eine konkrete Version oder einen Digest, z.B. 'myapp:1.4.2' oder 'myapp@sha256:...'.",
    "suggestedCode": "image: myapp:1.4.2"
  }
]
```

Mögliche Werte für 'type':
  COMPOSE_ERROR, COMPOSE_WARNING, COMPOSE_SMELL,
  CI_ERROR, CI_WARNING, CI_SMELL,
  BEST_PRACTICE, OPTIMIZATION, DOC_MISSING.
Mögliche Werte für 'severity': HIGH, MEDIUM, LOW.
Das 'snippet'-Feld ist optional (max. 8 Zeilen aus der Datei an der Stelle).
Das 'suggestedCode'-Feld ist optional (nur wenn eine konkrete Code-Änderung hilft).

FILE_DESCRIPTIONS:
```json
[
  {
    "path": "docker-compose.yml",
    "fileKind": "docker-compose",
    "description": "Top-Level-Compose-Datei, die den lokalen Entwicklungs-Stack definiert: eine Postgres-Datenbank, einen Redis-Cache und den Hauptdienst der Anwendung.",
    "keys": [
      {"key": "version", "value": "3.9", "meaning": "Compose-Dateiformat-Version. 3.x unterstützt moderne Features wie 'depends_on' mit Health-Conditions und benannte Volumes."},
      {"key": "services.app.image", "value": "myapp:1.4.2", "meaning": "Container-Image, aus dem der 'app'-Service gestartet wird. Ein fester Tag macht Builds reproduzierbar."},
      {"key": "services.app.environment", "value": "DATABASE_URL=...", "meaning": "Umgebungsvariablen, die dem 'app'-Container zur Laufzeit übergeben werden. Für echte Credentials besser eine '.env'-Datei oder Docker-Secrets nutzen."}
    ]
  }
]
```

Mögliche Werte für 'fileKind': docker-compose, dockerfile, gitlab-ci, github-actions, jenkinsfile,
circleci, azure-pipelines, bitbucket-pipelines, cloud-build, buildkite, travis, other.

OPTIMIZATIONS:
```json
[
  {
    "title": "Ersetze 'image: myapp:latest' durch einen gepinnten Tag",
    "rationale": "Unveränderliche Image-Tags machen Rollbacks und Audits reproduzierbar; ':latest' ändert sich beim Rebuild stillschweigend.",
    "patchSnippet": "image: myapp:1.4.2"
  }
]
```

{mermaidSection}
{userPromptSection}

## pipeline._section.mermaid
Mermaid-Diagramm

## pipeline.mermaidSection

Erzeuge ein Mermaid-Architekturdiagramm der wichtigsten Services / Jobs und ihrer Beziehungen (depends_on, needs, after, …).
Ausgabe als ```mermaid-Codeblock mit `graph LR`-Syntax.
VERBINDLICHE SYNTAX-REGELN:
- Node-IDs: NUR Buchstaben/Ziffern/Unterstrich. KEINE Bindestriche, Punkte, Klammern oder Slashes.
- Node-Labels mit Leerzeichen oder Sonderzeichen MÜSSEN in doppelten Anführungszeichen stehen: A["My Service"]
- NIEMALS HTML-Entities in Labels verwenden (VERBOTEN: &quot; &amp; &#x2F; &#39; &lt; &gt;).
  Mermaid rendert Entities zwar, aber die daraus resultierenden unescapten Anführungszeichen zerstören den Parser mitten im Label.
  Nur reinen Text verwenden: A["Web Controllers"] — NICHT A["Web &quot;Controllers&quot;"].
- Pfeil-Syntax: NUR --> (zwei Bindestriche + >) ist erlaubt.
- Edge-Labels: IMMER in Pipe-Zeichen: A -->|Label| B
- Nach dem Diagrammtyp (graph LR) KEINE weiteren Token in derselben Zeile.
- KEINE Subgraph-Blöcke. KEINE `end`-Statements außer zum Schließen eines Subgraphs.
- Maximal 15 Nodes.
- classDef MUSS vor class-Statements stehen.
- Jede classDef und jede class-Anweisung MUSS auf einer eigenen Zeile stehen.
- Keine Leerzeilen innerhalb des Diagramm-Codes.

## pipeline._section.userprompt
User-Prompt-Abschnitt

## pipeline.userPromptSection

## Zusätzliche Benutzer-Anweisungen
Der Benutzer hat die folgenden zusätzlichen Anweisungen gegeben, die bei der Antwort zwingend zu berücksichtigen sind – zusätzlich zum strukturierten Ausgabeformat oben:
---
{userPrompt}
---
Wenn die Benutzer-Anweisungen dem strukturierten Ausgabeformat widersprechen, folge der Benutzer-Intention, aber gib trotzdem jeden erforderlichen Block aus (SUMMARY, FINDINGS, FILE_DESCRIPTIONS, OPTIMIZATIONS, optional Mermaid). Nur wenn der Benutzer explizit darum bittet, einen Abschnitt wegzulassen, darfst du ihn auslassen.
