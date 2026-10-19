# StackLens — Benutzerhandbuch

## Überblick

StackLens ist eine Desktop-Anwendung, die Entwicklungsteams dabei hilft, **über alle Codebasen, Services, Dokumentationen und kuratierten Wissensquellen hinweg von einer einzigen Oberfläche aus zu suchen**. Sie verbindet sich mit GitLab, Datenbanken, Confluence, Grafana und Jira, führt KI-gestützte Analysen durch und stellt mit der lokalen **Wissensdatenbank (RAG)** geprüfte Inhalte und Analyseergebnisse im globalen KI-Chat bereit.

---

## Erste Schritte

### Erster Start

Wenn Sie StackLens zum ersten Mal öffnen, landen Sie auf der **Such**-Seite (`/` leitet auf `/search` weiter) mit leerer Sitzungshistorie. Sie müssen mindestens eine Datenquelle konfigurieren, bevor Sie mit der Suche beginnen können.

1. Klicken Sie auf **Konfiguration** in der linken Seitenleiste.
2. Fügen Sie eine GitLab-Instanz, eine Datenbankverbindung oder eine andere Quelle hinzu.
3. Kehren Sie zu **Neue Suche** zurück und starten Sie Ihre erste Suche.

---

## Startseite

StackLens hat kein separates Dashboard: die Root-URL leitet auf die **Such**-Seite weiter, die Einstiegspunkt für Stichwortsuchen ist und zugleich die Sitzungshistorie enthält.

| Bereich | Beschreibung |
|---------|--------------|
| **Stichwortfeld + Quellen-Pills** | Stichwörter eingeben, GitLab, Datenbank, Jira, Confluence und GitHub als Suchumfang umschalten |
| **Erweiterte Panel** | Quellen-Auswahl pro Typ und Cross-Reference-Schalter (DB-Treffer werden automatisch als Code-Verwendung nachgesucht) |
| **Ergebnis-Tabs** | Alle Ergebnisse plus ein Tab pro Quelle, mit Live-Status während der Lauf |
| **Sitzungshistorie** | Frühere Sitzungen mit Status (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`); Zeile anklicken zum Wiederöffnen, **Alle löschen** zum Leeren |

## Neue Suche

Die Suchseite ist die Kernfunktion von StackLens.

### Eine Suche starten

1. Geben Sie einen **Suchbegriff** in das Suchfeld ein (z. B. einen Funktionsnamen, Tabellennamen, eine Fehlermeldung oder einen Confluence-Begriff).
2. Wählen Sie eine oder mehrere **Quellen** aus — Checkboxen erscheinen für jeden aktiven Quellentyp (GitLab, Datenbank, Confluence, Grafana, Jira).
3. Filtern Sie GitLab-Ergebnisse optional nach **Repository** über den Repository-Selektor.
4. Klicken Sie auf **Suche starten**.

Nach dem Klicken erscheint ein Fortschrittsfenster mit einem Statusindikator. Die Suche läuft im Hintergrund — Sie können sie jederzeit mit dem Button **Suche abbrechen** abbrechen.

### Suchergebnisse

Ergebnisse werden nach Quellentyp gruppiert auf der Ergebnisseite angezeigt. Für jeden Treffer sehen Sie:

- **Quelle** (GitLab-Projekt, DB-Verbindung, Confluence-Space usw.)
- **Datei / Tabelle / Dokument** Name und Zeile/Abschnitt
- **Code-Kontext** oder Inhaltsausschnitt rund um den Suchbegriff
- **KI-Analyse** — eine KI-generierte Zusammenfassung aller gefundenen Ergebnisse

### KI-Querverweissuche

Wenn die KI-Analyse wichtige Symbole in einer Quelle identifiziert (z. B. einen Datenbanktabellennamen im Code), kann StackLens automatisch eine **Querverweissuche** in GitLab auslösen, um alle Verwendungen zu finden. Querverweisergebnisse erscheinen als eingerückte Unterkarten unter dem ursprünglichen Ergebnis.

---

## Repository-Analyse

Die Repository-Analyse-Seite bietet eine KI-gestützte strukturelle Analyse ganzer Code-Repositories.

### Lokale Repositories

1. Klicken Sie auf **Repository hinzufügen** (Schaltfläche oben rechts), um ein lokales Git-Repository auf Ihrem Rechner zu registrieren.
2. Geben Sie einen **Namen** und den vollständigen **Dateisystempfad** zum Repository-Stammverzeichnis an (verwenden Sie den Ordner-Auswahl-Button zum Durchsuchen).
3. Klicken Sie auf **Speichern** — die Repository-Karte erscheint im linken Bereich.

**So analysieren Sie ein Repository:**

1. Klicken Sie auf eine Repository-Karte, um sie auszuwählen.
2. Klicken Sie auf **Code analysieren** in der Kopfzeile des rechten Bereichs.
3. StackLens scannt alle relevanten Quelldateien und sendet sie an die KI, die folgendes generiert:
   - Eine **Markdown-Analyse** mit Architektur, Mustern und bemerkenswerten Code-Abschnitten
   - Ein interaktives **Draw.io-Architekturdiagramm** (wechselt automatisch zum Diagramm-Tab)
   - Ein **Mermaid-Flussdiagramm** mit wichtigen Beziehungen

**Stichwort-Suche innerhalb eines Repos:**

Geben Sie ein Stichwort in die **Suche im Repo**-Leiste ein und drücken Sie Enter oder klicken Sie das Suchsymbol. Ergebnisse erscheinen direkt mit Dateiname, Zeilennummer und Codeausschnitt — keine KI erforderlich, sofortige Ergebnisse.

### Remote GitLab-Repositories

Wenn Sie GitLab-Verbindungen konfiguriert haben, erscheint im linken Bereich unterhalb der lokalen Repos ein Abschnitt **GitLab-Repositories**.

1. Klicken Sie auf einen GitLab-Konfigurations-Eintrag, um ihn aufzuklappen — die Projektliste lädt automatisch.
2. Verwenden Sie das Filtereingabefeld, um ein Projekt nach Namen zu finden.
3. Klicken Sie auf eine Projektkarte, um sie auszuwählen.
4. Klicken Sie auf **Code analysieren** oder verwenden Sie die Stichwort-Suchleiste — identischer Ablauf wie bei lokalen Repos.

> **Hinweis:** Wenn noch keine GitLab-Konfiguration vorhanden ist, wird ein direkter Link zur Konfigurationsseite angezeigt.

### Analyse-Tabs

| Tab | Inhalt |
|-----|--------|
| **Text** | Vollständige KI-Analyse als gerenderte Markdown |
| **Diagramm** | Interaktives Draw.io-Architekturdiagramm (Zoom, Verschieben, Export) |
| **Mermaid** | Mermaid-Flussdiagramm — als `.mmd`-Datei herunterladen |

---

## Code-Qualitätsanalyse

Die Code-Qualitätsanalyse-Seite bietet eine KI-gestützte Überprüfung der Code-Qualität eines Repositories und generiert direkt anwendbare Refactoring-Vorschläge.

### Eine Analyse starten

1. Wählen Sie im linken Bereich ein **lokales Repository** oder klappen Sie eine **GitLab-Konfiguration** auf, um ein Projekt zu wählen.
2. Klicken Sie auf **Analyse starten** (oder die entsprechende Aktionsschaltfläche in der Bereichskopfzeile).
3. StackLens scannt die Repository-Dateien und sendet sie an die KI, die drei Kategorien analysiert:

| Kategorie | Was erkannt wird |
|-----------|-----------------|
| **Duplikater Code** | Semantisch oder textlich ähnliche Codeblöcke, die zusammengeführt oder extrahiert werden können |
| **Hexagonale Verstöße** | Geschäftslogik in Adaptern, direkter Infrastrukturzugriff aus der Domänenschicht |
| **Ungenutzter Code** | Tote Funktionen und Variablen, die die kognitive Last erhöhen |

Jeder Fund enthält einen **Schweregrad** (HIGH / MEDIUM / LOW), die betroffene Datei und den Ort, eine Beschreibung des Problems sowie einen KI-generierten Vorschlag.

### Ergebnis-Tabs

| Tab | Inhalt |
|-----|--------|
| **Zusammenfassung** | KI-Textzusammenfassung der Gesamt-Code-Qualität |
| **Funde** | Filterbare Liste aller einzelnen Funde mit Schweregradvergabe |
| **Dateiänderungen** | Vollständiger aktualisierter Dateiinhalt für jeden vorgeschlagenen Fix, bereit zum Kopieren oder Übertragen |

### GitLab Merge Request erstellen

Falls Dateiänderungen verfügbar sind, können Sie diese direkt nach GitLab übertragen:

1. Klicken Sie im Tab **Dateiänderungen** auf **Merge Request erstellen**.
2. Wählen Sie die Ziel-GitLab-Konfiguration und bestätigen Sie den Repository-Namen.
3. StackLens erstellt einen neuen Branch, committet alle Dateiänderungen und öffnet einen Merge Request.
4. Die MR-URL wird als anklickbarer Link angezeigt.

---

## Code-QA-Analyse

Die Code-QA-Analyse-Seite prüft GitLab- und GitHub-Merge-Requests auf Erfüllung der Akzeptanzkriterien sowie Einhaltung der konfigurierten Coding Guidelines und generiert direkt anwendbare Inline-Code-Vorschläge.

### Eine Analyse starten

1. Fügen Sie die **Merge-Request-URL** (GitLab oder GitHub) in das Eingabefeld ein.
2. Wählen Sie optional eine **GitLab / GitHub-Konfiguration** — oder lassen Sie StackLens die passende Konfiguration automatisch erkennen.
3. Geben Sie optional einen **Jira-Ticket-Schlüssel** ein, um Akzeptanzkriterien aus dem Ticket zu laden.
4. Aktivieren Sie optional **Code-Vorschläge vorbereiten**, damit gefundene Coding-Verstöße als GitLab- oder GitHub-"Apply suggestion"-Kommentare gepostet werden können.
5. Aktivieren Sie optional **Automatische Integrationstests ausführen**, damit StackLens die betroffenen Docker-Services startet und Tests ausführt.
6. Klicken Sie auf **Analyse starten**.

### Was im Hintergrund abläuft

1. StackLens lädt den vollständigen Diff, Titel, Beschreibung und — wenn angegeben — das Jira-Ticket.
2. Die KI prüft:
   - Erfüllung der Akzeptanzkriterien aus dem verknüpften Jira-Ticket
   - Einhaltung der konfigurierten Coding Guidelines (Clean Code, Sicherheit, Test-Coverage-Hinweise)
   - Potenzielle Fehler oder Regressionen im Diff
3. Das strukturierte Review-Ergebnis wird gestreamt und im Ergebnis-Tab angezeigt.
4. Wenn Code-Vorschläge aktiviert sind, können Sie einzelne Vorschläge direkt im MR posten.

### Ergebnis-Tabs

| Tab | Inhalt |
|-----|--------|
| **KI-Gedanken** | Live-Stream der KI-Ausgabe während der Analyse |
| **Ergebnis** | Vollständiger Review-Bericht als Markdown |

### Code-Vorschläge posten

Wenn **Code-Vorschläge vorbereiten** aktiviert ist, zeigt StackLens nach der Analyse eine Liste von Inline-Vorschlägen an:

1. Klicken Sie auf **Im MR als Vorschlag posten**, um einen Vorschlag als GitLab-"Apply suggestion"- oder GitHub-"Commit suggestion"-Kommentar zu übermitteln.
2. Der MR-Autor kann den Vorschlag mit einem einzigen Klick übernehmen.

---

## Sprint-Review-Analyse

Die Sprint-Review-Analyse-Seite erstellt einen strukturierten KI-Bericht für einen abgeschlossenen Jira-Sprint, einschließlich optionalem PowerPoint-Export.

### Eine Analyse starten

1. Wählen Sie eine **Jira-Konfiguration** aus dem Dropdown.
2. Fügen Sie eine **Jira-Sprint-Board-URL** in das Eingabefeld ein (z. B. die URL Ihres aktiven Sprint-Boards).
3. Laden Sie optional eine **PPTX-Vorlage** hoch (für markenkonforme Ausgabe) oder wählen Sie eine vorhandene Vorlagendatei vom Server aus.
4. Klicken Sie auf **Analyse starten**.

**Was im Hintergrund abläuft:**

1. StackLens parst die Sprint-Board-URL, um die Board-ID und den aktiven Sprint zu ermitteln.
2. Sprint-Metadaten (Name, Daten, Ziel) und alle Sprint-Issues (Stories, Bugs, Tasks, bis zu 500) werden aus Jira geladen.
3. Kommentare werden für Stories und Bugs geladen, um vollständigen Kontext bereitzustellen.
4. Die KI erhält die vollständigen Sprint-Daten und generiert eine strukturierte Review mit:
   - Sprint-Zusammenfassung mit Velocity und Abschlussrate
   - Highlights, Blocker und Follow-up-Punkte pro Issue-Typ
   - Retrospektive Erkenntnisse und Empfehlungen

### Ergebnis-Export

Nach Abschluss einer Analyse können Sie das Ergebnis in drei Formaten exportieren:

| Format | Beschreibung |
|--------|--------------|
| **Markdown** | Vollständige Analyse als `.md`-Datei |
| **HTML** | In sich geschlossene HTML-Seite zum Teilen |
| **PowerPoint (.pptx)** | Präsentationsdatei, optional auf Basis der hochgeladenen Vorlage |

Sie können das Ergebnis auch direkt in einem serverseitigen Pfad speichern über die Option **In Pfad speichern**.

---

## Sprint-Review (ADF-Tabelle)

Die Sprint-Review-Seite (Seitenleisten-Eintrag **Sprint-Review**, Route `/sprint-review`) überprüft einen gesamten Jira-Sprint auf einmal und postet eine einzige ADF-V3-Statustabelle in die Beschreibung eines gewählten Ziel-Tickets — ideal für Sprint-Abschluss-Zusammenfassungen im Stil „erledigt / benötigt Refinement / offene Frage", die das ganze Team direkt in Jira lesen kann.

### Ein Review starten

1. Wählen Sie eine **Jira-Konfiguration** aus dem Dropdown.
2. Geben Sie den **Sprint-Namen** ein (z. B. `Sprint 42` oder `My Project Sprint 7`).
3. Geben Sie den **Ziel-Ticket-Schlüssel** ein — das Ticket, dessen Beschreibung die Statustabelle erhält (z. B. `ARCH-101`).
4. Optional: Aktivieren Sie **Verbesserungsvorschläge einschließen**, damit die KI pro Ticket einen einzeiligen Refinement-Hinweis anhängt.
5. Optional: Fügen Sie eine **benutzerdefinierte Prompt-Vorlage** ein, um die eingebaute Standardvorlage `sprintreview.review_ticket` zu überschreiben.
6. Klicken Sie auf **Start**.

**Was im Hintergrund abläuft:**

1. StackLens löst den Sprint aus der konfigurierten Jira-Instanz auf.
2. Jedes Issue im Sprint wird geladen und von der KI überprüft.
3. Pro Ticket wird ein Status berechnet: `✓ Bearbeitet` (erledigt), `? Offene Frage` (offene Frage), `✎ Refinement nötig` (Refinement nötig).
4. Die KI baut eine ADF-V3-Tabelle mit einer Zeile pro Ticket zusammen und speichert sie lokal — die Tabelle wird **nie automatisch** in Jira gepostet.
5. Sobald der Lauf `COMPLETED` ist, blendet die Ergebnis-Karte die Schaltfläche **In Jira posten** ein.

### Tabelle in Jira posten

Klicken Sie in der Ergebnis-Karte auf **In Jira posten**, um die gespeicherte ADF-Tabelle an die Beschreibung des Ziel-Tickets anzuhängen. Die Schaltfläche ist erst nach erfolgreichem Abschluss aktiv; das Posten erfolgt einmalig (keine automatische Wiederholung). Der Lauf-Datensatz behält die Tabellen-JSON und die Ziel-URL, sodass das Posten später über `POST /api/sprint-review/{id}/resend` erneut ausgelöst werden kann.

### Ergebnis-Felder

| Feld | Beschreibung |
|------|--------------|
| `totalIssues` | Anzahl der überprüften Sprint-Issues |
| `rowsJson` | Pro-Ticket-Review-Zeilen (Schlüssel, Zusammenfassung, Status, Kommentar) |
| `tableAdfJson` | Bereit-zum-Posten ADF-V3-Tabelle |
| `sprintId` / `sprintState` / `sprintUrl` | Aufgelöste Sprint-Metadaten |

### Verlauf und Aufräumen

Alle Läufe werden neueste-zuerst aufgelistet. Jede Zeile bietet Abbrechen, Löschen und die Aktion **Erneut in Jira senden**. Mit `DELETE /api/sprint-review` lässt sich der Verlauf komplett leeren.

---

## Sprint-Vorbereitung

Die Sprint-Vorbereitungsseite führt eine KI-gestützte Qualitätsprüfung eines Jira-Parent-Tickets und aller zugehörigen Kindtickets durch, bevor ein Sprint beginnt. Sie stellt sicher, dass jedes Ticket die Akzeptanzkriterien-Vorlage Ihres Teams erfüllt, klare Vorbedingungen und Abhängigkeiten aufweist und vollständig sprint-ready ist.

### Eine Analyse starten

1. Wählen Sie eine **Jira-Konfiguration** aus dem Dropdown.
2. Geben Sie den **Parent-Ticket-Schlüssel** ein (z. B. `EPIC-42` oder `SPRINT-10`) — StackLens lädt dieses Ticket und alle verknüpften Kindtickets.
3. Wählen Sie optional ein oder mehrere **lokale Repositories** oder **GitLab-Projekte** als zusätzlichen Code-Kontext für die KI.
4. Wählen Sie optional eine oder mehrere **Datenbank-Konfigurationen** als Schema-Kontext.
5. Entscheiden Sie, ob nur das eingegebene Ticket analysiert werden soll (**Einzelticket-Modus**, Standard) oder alle verknüpften Untertickets einbezogen werden sollen.
6. Klicken Sie auf **Analyse starten**.

**Was im Hintergrund abläuft:**

1. Das Parent-Ticket und seine direkten Kindtickets werden aus Jira geladen.
2. Für jedes Ticket prüft die KI:
   - Ob die Beschreibung der Ticket-Vorlage Ihres Teams entspricht
   - Ob Akzeptanzkriterien klar definiert und testbar sind
   - Ob Vorbedingungen und Abhängigkeiten aufgelistet sind
   - Ob das Ticket so wie vorliegend umsetzbar ist oder Klärungsbedarf besteht
3. Für jedes Ticket wird ein KI-generierter **Verbesserungsvorschlag** erstellt, der direkt als Jira-Kommentar gepostet werden kann.

### Ergebnis

Nach Abschluss zeigt der Ergebnisbereich:
- Eine Zusammenfassung pro Ticket mit dem KI-Urteil
- Konkrete Verbesserungsvorschläge im Markdown-Format

### Verbesserung in Jira posten

Klicken Sie auf **Als Kommentar posten** neben dem Ergebnis eines Tickets, um den KI-Verbesserungsvorschlag direkt als Jira-Kommentar auf dem betreffenden Ticket zu veröffentlichen.

> **Tipp:** Nutzen Sie die Sprint-Vorbereitung zu Beginn jeder Sprint-Planungssitzung, um unvollständige Tickets zu erkennen, bevor der Sprint beginnt.

---

## Pipeline-Analyse

Die Pipeline-Analyse-Seite untersucht Docker-Compose-Stacks und CI/CD-Pipelines eines Repositories KI-gestützt auf Fehler, Warnungen, Smells und Optimierungen.

### Unterstützte Dateitypen

| Kategorie | Dateien |
|-----------|---------|
| Docker Compose | `docker-compose.yml`, `docker-compose.yaml`, `docker-compose.override.yml`, `compose.yml`, `compose.yaml` |
| Dockerfiles | `Dockerfile`, `Dockerfile.dev`, `Dockerfile.prod`, `Dockerfile.test` |
| CI/CD | `.gitlab-ci.yml`, `Jenkinsfile`, `.github/workflows/*.yml`, `.circleci/config.yml`, `bitbucket-pipelines.yml`, `.travis.yml`, `azure-pipelines.yml`, `cloudbuild.yaml`, `buildkite.yml` |

### Quelltypen

- **Lokale Repositories** — Registrierung über die **+**-Schaltfläche im linken Bereich (gemeinsamer Datei-Browser mit der Code-Analyse, themed über alle vier Themes: Dark, Modern, Klassisch/Legacy, Pop!_OS).
- **GitLab-Projekte** — Klappen Sie einen GitLab-Konfigurationseintrag auf; die Projektliste wird live vom verbundenen GitLab-Server geladen.
- **GitHub-Repositories** — Server-seitig gerendert; zur Anzeige wird kein GitHub-API-Aufruf benötigt (erst beim Start der Analyse).

### So starten Sie eine Analyse

1. Wählen Sie im linken Bereich ein **lokales Repository**, ein **GitLab-Projekt** oder ein **GitHub-Repository**.
2. Optional: Geben Sie im Feld **Optionale Benutzer-Anweisungen** eine fokussierende Eingabe ein, z. B. „Nur Produktions-Compose beachten, Build-Jobs ignorieren".
3. Klicken Sie auf **Starten**.
4. Der Status wird alle 2,5 s aktualisiert; bei Abschluss erscheinen die Tabs.

### Ergebnis-Tabs

| Tab | Inhalt |
|-----|--------|
| **Zusammenfassung** | Markdown-Übersicht des gesamten Stacks |
| **Befunde** | Strukturierte Findings mit Schweregrad `HIGH` / `MEDIUM` / `LOW`, Datei, Zeile, Snippet und Vorschlag. Typen: `COMPOSE_ERROR`, `COMPOSE_WARNING`, `COMPOSE_SMELL`, `CI_ERROR`, `CI_WARNING`, `CI_SMELL`, `BEST_PRACTICE`, `OPTIMIZATION`, `DOC_MISSING` |
| **Dateien** | Pro-Datei-Beschreibung jedes `key=value` (z. B. `restart: unless-stopped`, `:latest`-Tags, Healthchecks, `depends_on`, Caches, Artifacts) |
| **Optimierungen** | Kürzere, handlungsorientierte Vorschläge mit Patch-Snippets |
| **Mermaid** | Optionale Topologie-Grafik der Services und `depends_on`-Beziehungen (Download als `.mmd`) |

### Hinweise

- Die KI-Eingabe enthält den vollständigen Projektbaum und jede relevante Datei inline — StackLens versucht nicht, auf das Dateisystem zuzugreifen oder Shell-Befehle auszuführen.
- Der Lauf ist `@Async` und blockiert den Browser nicht.
- Der Verlauf ist global und nach `createdAt` absteigend sortiert; er enthält alle Analysen aller Repositories.

---

## Ticket-Zusammenfassung

Die Ticket-Zusammenfassungsseite ruft ein einzelnes Jira-Ticket ab und erstellt eine kompakte KI-Zusammenfassung von Beschreibung und allen Kommentaren — ideal, um lange, stark kommentierte Tickets schnell zu verstehen, ohne sie vollständig lesen zu müssen.

### Eine Zusammenfassung starten

1. Wählen Sie eine **Jira-Konfiguration** aus dem Dropdown (die erste aktive Konfiguration ist vorausgewählt).
2. Geben Sie den **Ticket-Schlüssel** ein (z. B. `PROJ-1234`).
3. Optional können Sie ein eigenes **Prompt-Template** einfügen — leer lassen, um das eingebaute Standard-Template (`ticketsummary.summarize_ticket`) zu verwenden. Klicken Sie auf **Standard anzeigen**, um das eingebaute Template als Vorschau anzuzeigen.
4. Klicken Sie auf **Zusammenfassung starten**.

**Was im Hintergrund abläuft:**

1. StackLens ruft das Ticket (Beschreibung, Typ, Status, Priorität, Bearbeiter, Ersteller, Labels) und alle Kommentare in chronologischer Reihenfolge ab.
2. Die kombinierten Eingaben werden an die KI gesendet, die einen strukturierten Markdown-Bericht mit den folgenden Abschnitten erzeugt:
   - **Überblick** — TL;DR in einem Absatz
   - **Anforderungen** — was das Ticket verlangt
   - **Akzeptanzkriterien** — testbare Bedingungen aus der Beschreibung
   - **Diskussion** — Kernpunkte und Entscheidungen aus dem Kommentarverlauf
   - **Offene Punkte** — Unklarheiten, Blocker oder fehlende Informationen
   - **Nächste Schritte** — konkrete Folgeaktionen
3. Das Markdown-Ergebnis wird serverseitig zu HTML gerendert und neben der Markdown-Quelle gespeichert.
4. **Abbrechen**: Während eines laufenden Vorgangs steht der **Abbrechen**-Button zur Verfügung; der Abbruch wird zwischen Jira-Abruf und KI-Aufruf berücksichtigt.

### Ergebnis lesen

- Die gerenderte **HTML-Zusammenfassung** wird in der Ergebnis-Karte angezeigt.
- Ein **Anzeigen**-Schalter zeigt das **Original**-Panel: einen Klartext-Schnappschuss der Beschreibung plus aller Kommentare, die an die KI gefüttert wurden — für transparenten Vergleich und schnelles Faktenchecken.
- Die **Status**-Anzeige zeigt `PENDING` → `RUNNING` → `COMPLETED` (oder `FAILED` / `CANCELLED`).
- Alle Läufe werden in der **Verlauf**-Liste am unteren Seitenrand gespeichert; klicken Sie auf einen Eintrag, um ihn erneut zu laden.

> **Tipp:** Nutzen Sie die Ticket-Zusammenfassung beim Onboarding auf ein neues Ticket, zur Vorbereitung von Meetings oder als Ausgangspunkt für die Verfeinerung vor der Sprint-Vorbereitung.

---

## Ticket-Analyse

Die Ticket-Analyse-Seite führt eine mehrstufige KI-Untersuchung eines Jira-Tickets durch.

1. Wählen Sie eine **Jira-Konfiguration** aus dem Dropdown.
2. Geben Sie den **Ticket-Schlüssel** ein (z. B. `PROJ-1234`).
3. Wählen Sie optional eine oder mehrere **GitLab-Konfigurationen** — StackLens sucht in diesen Repositories nach Merge Requests, die mit dem Ticket in Zusammenhang stehen könnten.
4. Klicken Sie auf **Analysieren**.

**Was im Hintergrund abläuft (9 Schritte):**

1. Das ursprüngliche Ticket (Zusammenfassung, Beschreibung, Kommentare, Typ, Status, Labels, Komponenten) wird aus Jira geladen.
2. Die KI extrahiert eine strukturierte Problembeschreibung, Ursache, betroffene Komponenten und Suchbegriffe.
3. StackLens durchsucht Jira nach ähnlichen Tickets anhand dieser Suchbegriffe.
4. Falls GitLab-Konfigurationen ausgewählt sind, durchsucht StackLens alle zugänglichen Projekte nach Merge Requests, die den Ticket-Schlüssel oder die extrahierten Suchbegriffe referenzieren.
5. Die KI fasst alle Erkenntnisse zusammen und erstellt eine strukturierte Analyse: Problemzusammenfassung, ähnliche Tickets, relevante MRs und eine Empfehlung.
6. StackLens identifiziert die relevantesten Repositories (basierend auf Jira-Komponenten, betroffenen Komponenten und MR-Projektpfaden) und lädt repräsentative Quelldateien als Kontext.
7. Die KI erstellt einen **Implementierungsplan**: betroffene Komponenten, Risikobereiche und einen empfohlenen Lösungsansatz.
8. Die KI generiert konkrete **Code-Implementierungsvorschläge**: betroffene Repositories, zu ändernde Quelldateien, Testdateien und ggf. benötigte Datenbankmigrationen.
9. StackLens klont das Repository, führt die **vorhandene Testsuite** auf einer temporären Kopie aus, um die vorgeschlagenen Änderungen zu validieren, und erstellt einen Merge Request oder Pull Request.

### Ergebnis-Tabs

Nach Abschluss einer Analyse wird das Ergebnis in mehreren Tabs angezeigt:

| Tab | Inhalt |
|-----|--------|
| **Analyse** | Vollständige KI-Synthese als Markdown — Problemzusammenfassung, ähnliche Tickets, MR-Referenzen, Empfehlung |
| **Code-Implementierung** | Datei-für-Datei Code-Vorschläge (Produktionsdateien + Testdateien + DB-Migrationen), gruppiert nach Repository |
| **Merge Requests** | Während der Analyse gefundene GitLab-MRs |
| **Pull Requests** | Für lokale Repositories mit GitHub-Remote erstellte GitHub-PRs |
| **Rohdaten** | JSON-Schnappschuss aller Zwischenergebnisse (Ticket-Daten, Problemanalyse, ähnliche Tickets, verwandte MRs) |

### GitLab Merge Request erstellen

Wenn die Analyse Code-Implementierungsvorschläge enthält, können Sie diese direkt nach GitLab übertragen:

1. Wechseln Sie im Tab **Code-Implementierung** zum gewünschten Repository.
2. Klicken Sie auf **GitLab MR erstellen** und bestätigen Sie die GitLab-Konfiguration und den Repository-Namen.
3. StackLens wird:
   - Einen neuen Branch mit dem Ticket-Schlüssel als Namen erstellen (z. B. `PROJ-1234`)
   - Alle vorgeschlagenen Dateien in diesen Branch committen
   - Einen Merge Request mit der Ticket-Zusammenfassung als Titel und einer Beschreibung mit Link zurück zum Jira-Ticket öffnen
4. Die MR-URL wird zurückgegeben und als anklickbarer Link angezeigt.

### GitHub Pull Request erstellen

Für **lokale Repositories** mit einem konfigurierten GitHub-Remote erkennt StackLens den Remote automatisch und erstellt einen Pull Request:

1. Wechseln Sie im Tab **Code-Implementierung** zum lokalen Repository-Bereich.
2. Klicken Sie auf **GitHub PR erstellen**.
3. StackLens erkennt die GitHub-Remote-URL aus der Git-Konfiguration des Repositories, erstellt einen Branch, überträgt die vorgeschlagenen Änderungen und öffnet einen PR.
4. Die PR-URL erscheint im Statusbereich und im Tab **Pull Requests**.

Aktuelle Analysen werden unterhalb des Formulars für schnellen Zugriff aufgelistet.

---

## Ticket-Erstellung

Die Ticket-Erstellungsseite ermöglicht es, jede formlose Beschreibung, jeden Screenshot oder jedes Dokument in ein strukturiertes Jira-Ticket zu verwandeln — die KI generiert Zusammenfassung, Beschreibung, Akzeptanzkriterien, Labels und Ticket-Typ für Sie.

### Ein Ticket generieren

1. Tippen oder fügen Sie Ihre Feature-Anfrage, Fehlerbeschreibung oder Idee in das **Eingabefeld** ein.
2. Hängen Sie optional eine Datei (Bild oder PDF) als zusätzlichen Kontext an — StackLens liest den Inhalt und bezieht ihn in den KI-Prompt ein.
3. Klicken Sie auf **Generieren**.

StackLens sendet die Eingabe an die KI, die folgendes erstellt:

| Feld | Beschreibung |
|------|--------------|
| **Zusammenfassung** | Ein prägnanter einzeiliger Ticket-Titel |
| **Beschreibung** | Vollständige strukturierte Beschreibung mit Kontext und Anforderungen |
| **Akzeptanzkriterien** | Nummerierte, testbare Kriterien aus der Eingabe abgeleitet |
| **Ticket-Typ** | Vorgeschlagener Typ: Story, Bug oder Task |
| **Priorität** | Vorgeschlagene Priorität: Minor, Major, Critical oder Blocker |
| **Labels** | Aus der Eingabe extrahierte relevante Tags |
| **Offene Punkte** | Unklarheiten oder offene Fragen, die die KI identifiziert hat |

### Das Ticket in Jira anlegen

Nach Durchsicht des KI-generierten Ergebnisses können Sie es direkt nach Jira übermitteln:

1. Wählen Sie eine **Jira-Konfiguration** aus dem Dropdown.
2. Geben Sie den **Projekt-Schlüssel** ein (z. B. `PAY`).
3. Passen Sie bei Bedarf Zusammenfassung, Beschreibung, Ticket-Typ, Priorität oder Labels an.
4. Klicken Sie auf **In Jira anlegen**.

StackLens erstellt das Ticket über die Jira-API und gibt den neuen Ticket-Schlüssel (z. B. `PAY-1234`) mit einem anklickbaren Link zurück, um es direkt in Jira zu öffnen.

### Verlauf

Alle bisherigen Generierungen werden gespeichert und unterhalb des Formulars aufgelistet. Jeder Eintrag zeigt die ursprüngliche Eingabe, die KI-generierten Ticket-Felder und den Jira-Schlüssel, falls das Ticket eingereicht wurde.

> **Tipp:** Fügen Sie ein PDF-Anforderungsdokument oder einen Mockup-Screenshot als Dateianhang ein — die KI extrahiert die relevanten Informationen und erstellt daraus automatisch ein Ticket.

---

## Agenten-Pipeline

Die Agenten-Pipeline-Seite führt einen vollautomatisierten, mehrstufigen KI-Entwicklungsworkflow aus, der eine Feature-Beschreibung — oder ein Jira-Ticket — in PHP-Code, Tests, Dokumentation und einen GitLab Merge Request verwandelt.

### Einen Pipeline-Lauf starten

**Aus Freitext:**
1. Klicken Sie auf **Freitext** und tippen oder fügen Sie eine Feature-Anfrage, User Story oder Aufgabenbeschreibung ein.
2. Wählen Sie optional ein **lokales Repository** aus, um den Lauf mit einer Codebasis zu verknüpfen — StackLens schreibt die generierten Dateien dorthin.
3. Klicken Sie auf **Pipeline starten**.

**Aus einem Jira-Ticket:**
1. Klicken Sie auf **Jira-Ticket** und wählen Sie eine Jira-Konfiguration aus dem Dropdown.
2. Geben Sie den **Ticket-Schlüssel** ein (z. B. `PROJ-1234`).
3. Wählen Sie optional ein verknüpftes lokales Repository.
4. Klicken Sie auf **Pipeline starten**.

### Pipeline-Stufen

Die Pipeline durchläuft 13 Stufen sequenziell. Jede Stufe verwendet einen dedizierten KI-Agenten:

| Stufe | Agent | Rolle |
|-------|-------|-------|
| 0 (nur Jira) | **Lena** | Analysiert das Jira-Ticket vor und extrahiert strukturierte Eingaben für Nora |
| 1 | **Nora** | Anforderungsanalystin — schreibt User Story, Akzeptanzkriterien und technischen Kontext |
| Ⓘ (nur Repo) | **Ivan** | Setup — führt `git pull` und `make update`/`make install` im lokalen Repository aus |
| 2 | **Cole** | Implementierungsplaner — durchsucht das verknüpfte Repository und erstellt einen detaillierten Implementierungsplan |
| 3 | **Max** | PHP-Architekt — entwirft die hexagonale Architektur und Komponentenstruktur |
| 4 | **Finn** | PHP-Entwickler — schreibt produktionsreife PHP-Quelldateien gemäß Architekturplan |
| 5 | **Faye** | Frontend-Entwicklerin — erstellt Frontend-Dateien (Twig-Templates, Vue-Komponenten, JS, CSS) |
| 6 | **Vera** | Infrastruktur-Reviewerin — prüft docker-compose, Makefile und nginx-Konfiguration |
| 7 | **Tess** | Test-Ingenieurin — schreibt PHPUnit-Unit-Tests; löst bei Bedarf einen Finn-Loopback aus |
| 8 | **Syd** | Integrations- & Systemtest-Ingenieurin — schreibt PHPUnit-Integrations- und Systemtests |
| 9 | **Quinn** | Code-Qualitäts-Reviewerin — prüft Coverage und Style; kann Tess/Syd-Loopbacks auslösen |
| 10 | **Sage** | Sicherheitsanalytikerin — erkennt Sicherheitslücken und behebt diese |
| 11 | **Rex** | Build & Fix-Schleife — führt `make sniff`, `make test` und `make coverage` aus und wiederholt mit KI-geführten Korrekturen bei Fehlern |

Der Fortschritt wird live angezeigt: jede Stufenkarte aktualisiert sich mit einem Status-Badge (`PENDING` → `RUNNING` → `DONE`) und Streaming-Ausgabe.

### Ergebnisse lesen

Nach Abschluss aller Stufen werden die Ergebnisse in aufklappbaren Stufenkarten angezeigt. Sie können:

- **Dateien vorschau**: StackLens analysiert Finns Ausgabe nach Code-Blöcken (`### path/to/File.php`) und zeigt eine Dateivorschauliste.
- **Dateien schreiben**: Klicken Sie auf **Dateien schreiben**, um die generierten Dateien in das verknüpfte lokale Repository zu übertragen.
- **Ausgabe kopieren**: Jede Stufenkarte hat einen Kopierschalter für die vollständige Markdown-Ausgabe.

### Agenten-Prompts anpassen

Alle Agenten-Prompts sind editierbar. Öffnen Sie die Agenten-Pipeline-Einstellungsseite, um den Prompt für jeden Agenten (Nora bis Rex) anzusehen, zu bearbeiten oder zurückzusetzen. Benutzerdefinierte Überschreibungen werden in `data/custom-agents/` gespeichert und bleiben nach JAR-Updates erhalten.

---

## Tooling

Die Tooling-Seite ermöglicht es, eine Skriptbeschreibung, einen Verfahrenstext oder einen Code-Ausschnitt in ein lauffähiges, eigenständiges Kotlin-Tool zu verwandeln — kompiliert zu einer ausführbaren JAR-Datei und direkt aus StackLens heraus ausführbar.

### Ein Tool erstellen

1. Klicken Sie auf **Neues Tool** (oder den **+**-Button).
2. Geben Sie einen **Tool-Namen** ein.
3. Fügen Sie den **Skript-Inhalt** ein — das kann ein Shell-Skript, eine Confluence-Verfahrensseite, Pseudocode oder eine beliebige Textbeschreibung dessen sein, was das Tool tun soll.
4. Klicken Sie auf **Generieren**.

StackLens sendet den Inhalt an die KI, die eigenständigen Kotlin-Code generiert und zu einer ausführbaren JAR-Datei kompiliert. Ein Status-Badge zeigt den Fortschritt:

| Status | Bedeutung |
|--------|-----------|
| `PENDING` | In der Warteschlange, noch nicht gestartet |
| `ANALYZING` | Eingabe wird vorbereitet |
| `GENERATING` | KI schreibt Kotlin-Code |
| `BUILDING` | JAR wird kompiliert (bis zu 2 automatische Wiederholungen bei Kompilierfehlern) |
| `READY` | Tool ist verfügbar und ausführbar |
| `FAILED` | Ein Fehler ist aufgetreten — Fehlermeldung auf der Karte prüfen |

> **Automatischer Retry bei Kompilierfehlern:** Falls der KI-generierte Code nicht kompiliert, sendet StackLens die Compiler-Fehler automatisch zur Korrektur an die KI zurück und wiederholt die Kompilierung — bis zu 2 Mal, bevor ein Fehler gemeldet wird.

### Ein Tool ausführen

Sobald ein Tool den Status `READY` erreicht hat, klicken Sie auf **Ausführen**. Die JAR-Datei des Tools wird serverseitig ausgeführt (`java -jar`) und die Ausgabe wird direkt auf der Karte angezeigt.

### Ein Tool bearbeiten und überarbeiten

| Aktion | So geht's |
|--------|-----------|
| **KI-Überarbeitung** | Klicken Sie auf **Überarbeiten** und beschreiben Sie Ihre Änderungen in Textform. Die KI schreibt den Code mit Ihren Anweisungen um und kompiliert neu. |
| **Code bearbeiten** | Klicken Sie auf **Code bearbeiten**, um den generierten Kotlin-Quellcode im integrierten Editor zu öffnen. Nehmen Sie manuelle Änderungen vor und klicken Sie auf **Neu kompilieren**. |
| **Prompt bearbeiten** | Klicken Sie auf **Prompt bearbeiten**, um den Generierungs-Prompt zu ändern und die KI von Grund auf neu auszuführen. |

### JAR exportieren und importieren

- **Exportieren**: Laden Sie die kompilierte JAR über den Button **JAR exportieren** herunter. Die JAR enthält den Tool-Namen und das Icon im Manifest, damit StackLens diese beim Import wiederherstellen kann.
- **Importieren**: Verwenden Sie **JAR importieren**, um eine zuvor exportierte (oder kompatible) JAR einzuspielen. StackLens liest die eingebetteten Metadaten und registriert das Tool sofort mit Status `READY`.

### Icons

Jedes Tool erhält automatisch eine visuelle Identität:
- Ein **Bootstrap-Icon**, das von der KI anhand des Tool-Zwecks ausgewählt wird (auf der Karte und in der Seitenleiste angezeigt).
- Ein **PNG-Badge** (512×512) mit den Initialen des Tools auf grünem Verlaufshintergrund — in der JAR eingebettet und als macOS-Finder-Icon verwendet (sofern `fileicon` installiert ist).

---

## Grafana-Analyse

Die Grafana-Analyse-Seite ermöglicht die Arbeit mit Grafana-Dashboards und die KI-gestützte Neuerstellung oder Anpassung von Dashboards.

### Voraussetzungen

Sie benötigen mindestens eine **Grafana-Konfiguration** auf der Konfigurationsseite (Abschnitt Grafana) mit einer gültigen Basis-URL und einem API-Token/Anmeldedaten.

### Ein Dashboard hinzufügen

1. Wählen Sie eine **Grafana-Konfiguration** aus dem Dropdown oben auf der Seite.
2. Fügen Sie eine **Grafana-Dashboard-URL** in das Eingabefeld ein (z. B. `https://grafana.example.com/d/abc123/my-dashboard`).
3. Klicken Sie auf **Dashboard hinzufügen**.

Das Dashboard wird als Live-iFrame in das Dashboard-Raster eingebettet. Sie können mehrere Dashboards gleichzeitig hinzufügen — sie erscheinen nebeneinander.

Um ein Dashboard zu entfernen, klicken Sie auf den **×**-Button in seiner Kopfzeile.

### KI-Dashboard-Neuerstellung

Verwenden Sie diesen Bereich, um ein neues Dashboard zu erstellen oder ein vorhandenes mit KI-Unterstützung umzustrukturieren.

1. Falls oben Dashboards eingebettet sind, werden diese automatisch als Kontext für die KI einbezogen.
2. Geben Sie Ihre **Anweisungen** in das Textfeld ein (z. B. „CPU-Panel umstrukturieren und Arbeitsspeicher-Schwellwerte hinzufügen").
3. Klicken Sie auf **KI-Dashboard neu erstellen**.

Die KI gibt zurück:
- Eine strukturierte **Zusammenfassung** der vorgenommenen Änderungen
- Eine Liste der **Änderungen**
- Ein **neu erstelltes Dashboard-JSON**, das Sie direkt in Grafana importieren können

---

## SQL-Analyse

Die SQL-Analyse-Seite bietet drei KI-gestützte SQL-Funktionen auf einer einzigen Seite.

### 1. Eine Abfrage analysieren

1. Fügen Sie eine beliebige SQL-Abfrage in das **SQL**-Feld ein.
2. Fügen Sie optional Ihre Tabellen-DDL in das **DDL**-Feld ein, um der KI Schema-Kontext zu geben.
3. Klicken Sie auf **Abfrage analysieren**.

Die KI gibt zurück:
- **Syntaxstatus** — gültig, Warnung oder Fehler mit Details
- **Optimierungsvorschläge** — jeweils mit Schweregrad (HIGH / MEDIUM / LOW) und einem konkreten Vorschlag
- **Index-Empfehlungen** — Spaltenebene Index-Vorschläge
- **Abfrage-Erkenntnisse** — allgemeine Kommentare zu Absicht, Komplexität und möglichen Problemen

### 2. Testdaten generieren

Geben Sie eine Abfrage (und optionale DDL) an und klicken Sie auf **Testdaten generieren**, damit die KI folgendes erstellt:
- `CREATE TABLE`-Anweisungen für alle referenzierten Tabellen
- Realistische `INSERT`-Anweisungen mit Beispielwerten, die die Abfrage sinnvolle Ergebnisse liefern lassen

Die generierten Testdaten können direkt gegen eine lokale Datenbank ausgeführt werden — kein manuelles Schreiben von Fixtures erforderlich.

### 3. Eine Abfrage aus Anforderungen generieren

1. Geben Sie Ihre Tabellenstruktur in das **DDL**-Feld ein.
2. Beschreiben Sie, was die Abfrage erreichen soll, im Feld **Ziel / Anforderungen** (in einfacher Sprache).
3. Klicken Sie auf **Abfrage generieren** — die KI erstellt das SQL für Sie.

### Verlauf

Alle analysierten Abfragen werden automatisch im **Verlauf** gespeichert (klicken Sie auf die Verlaufsschaltfläche, um vergangene Einträge anzuzeigen). Jeder Eintrag speichert SQL, DDL und generierte Testdaten.

---

## Logging

Die Logging-Seite (`/logging`) nimmt rohe Log-Ausgabe — Terminal-Mitschnitt, `.log`-Datei, CI-Ausgabe, Stacktrace aus einem Ticket — und macht daraus eine sortierte Problemliste sowie optional einen Code-Fix in einem verknüpften Repository.

### Log-Ausgabe analysieren

1. Öffnen Sie **Logging** im Bereich Analysen.
2. Fügen Sie Ihren Log-Text in das Feld **Log-Text** ein (max. 50.000 Zeichen) und klicken Sie **Analysieren**.
3. Die Seite klassifiziert jede Zeile und hebt sie hervor; die Zusammenfassung zeigt **Zeilen gesamt** und **Zeilen mit Problemen**, über **„Nur Problemlinien anzeigen“** filtern Sie die Ansicht.

Erkannte Kategorien: CRITICAL, ERROR, Exception, Stack Trace, Speicher/Ressource, Connection, HTTP-Fehler, Datenbank, Timeout, WARNING und Deprecated. Jede Kategorie erscheint als Badge — per Klick schließen Sie Zeilenklassen ein oder aus —, die Legende erklärt sie. **Rohtext kopieren** übernimmt den Originaltext in die Zwischenablage oder in den KI-Chat. Die Klassifizierung läuft vollständig im Browser; erst der Fix-Aufruf spricht mit der KI.

### Die Ursache in einem Repository beheben

1. Fügen Sie ein lokales Repository hinzu oder wählen Sie es in der Karte **Lokale Repositories** links aus.
2. StackLens vergleicht die Klassen-/Namespace-Namen aus dem Log mit dem Repository und meldet **„Passt zu den Logs“**, **„Passt nicht zu den Logs“** oder **„Stack unbekannt“**. Bei einer Abweichung ist der Fix-Button deaktiviert, weil die Logs höchstwahrscheinlich aus einer anderen Codebasis stammen.
3. Wählen Sie die betroffenen Problem-Badges aus und klicken Sie **„Fix für ausgewählte Probleme“**. Die Pipeline durchläuft dann ihre vier KI-Stufen: **① Diagnose — Root Cause** → **② Architektur & Coding-Guidelines** → **③ Code-Fix** → **④ Unit- & Systemtests**. Der Fortschritt wird pro Stufe als *Ausstehend*, *Läuft…*, *Abgeschlossen*, *Übersprungen*, *Abgebrochen* oder *Fehler* angezeigt.
4. Wenn die Pipeline Dateien erzeugt hat, öffnet **Dateien in Repository schreiben** eine Vorschau mit Pfad, Inhalt und Beschreibung je Datei; nach dem Bestätigen werden sie in das verknüpfte Repository geschrieben.

Ohne verknüpftes Repository läuft nur die Diagnose — Plan, Code und Test werden mit explizitem Hinweis übersprungen, weil die KI weder Architektur prüfen noch kompilierenden Code erzeugen kann.

### Verlauf und Wiederherstellung

Lokale Analysen stehen in der Karte **Verlauf**, Fix-Läufe in **Fix-Verlauf**; **Laden** / **Öffnen** stellt einen Lauf wieder her, einzelne oder alle abgeschlossenen Läufe lassen sich löschen. Ein durch einen Neustart unterbrochener Lauf wird geheilt und bleibt nie auf `RUNNING` stehen (ein veralteter `RUNNING`-Eintrag wird nach `stacklens.analysis.stale-running.default-timeout` als `FAILED` angezeigt). Jeder KI-Aufruf schreibt einen Token-Nutzungs-Datensatz (`kind=logging_fix`), sichtbar in der Entwickler-Fußzeile.

---

## Bild-Extraktion

Die Seite **Bild-Extraktion** verwandelt den Text in einem Bild in editierbaren, kopierbaren Klartext. Sie kombiniert ein multimodales KI-Modell mit einer lokalen Tesseract-OCR-Fallback-Engine, sodass Sie gescannte Dokumente, Screenshots, Whiteboard-Fotos oder gerenderte PDF-Seiten lesen können, ohne StackLens zu verlassen.

### Unterstützte Formate und Limits

| Format | Hinweise |
|--------|----------|
| **TIFF** (`.tif`, `.tiff`) | Inklusive mehrseitiger TIFFs (nur erste Seite) |
| **PNG** (`.png`) | Inklusive transparenter Hintergründe |
| **JPG / JPEG** (`.jpg`, `.jpeg`) | Standard-JPEG mit EXIF-Metadaten |

Maximale Dateigröße: **20 MB pro Datei**. Größere Dateien werden mit einem expliziten Fehler abgelehnt.

### Eine Extraktion starten

1. **Bild bereitstellen** — wählen Sie eine der folgenden Optionen:
   - **Drag-and-drop** der Datei auf die Dropzone auf der Seite.
   - **Klick auf die Dropzone**, um den System-Dateidialog zu öffnen.
   - **Klick auf *Dateibrowser öffnen***, um das Server-seitige Dateisystem zu durchsuchen (nützlich, wenn das Bild auf einem Volume liegt, das die JVM sehen kann).
2. Optional einen **Speicherpfad** angeben (ein Server-Verzeichnis). Ist er gesetzt, wird der extrahierte Text dort zusätzlich als `<originalDateiname>.txt` in UTF-8 abgelegt.
3. **OCR-Engine** wählen:
   - **KI zuerst** *(empfohlen)* — das multimodale KI-Modell liest das Bild und transkribiert den Text. Bei Fehlern (Provider-Fehler, zu komplexes Bild, Modell lehnt die Datei ab) fällt StackLens automatisch auf die lokale Tesseract-Binary zurück, sodass die Anfrage dennoch Text liefert.
   - **Nur Tesseract** — überspringt die KI komplett und nutzt direkt die lokale Tesseract-Binary. Schneller, vollständig offline, kein API-Kontingent. Verwenden Sie diese Option, wenn das Bild sauberer gedruckter Text ist und Sie Kosten/Latenz niedrig halten möchten.
4. Klicken Sie auf **Text extrahieren**. Eine Status-Pille zeigt den aktuellen Zustand (`RUNNING` → `COMPLETED` / `FAILED`); ein **Abbrechen**-Button stoppt den Lauf zwischen Upload und KI-Aufruf.

### Ergebnis lesen

Die Ergebnis-Karte zeigt den **extrahierten Text** in einer scrollbaren, schreibgeschützten Textarea sowie einen kompakten **Metadaten-Block** mit Quelldateiname, MIME-Typ, Dateigröße, tatsächlich verwendeter OCR-Engine und absolutem Pfad der gespeicherten `.txt`-Datei (sofern ein Speicherpfad gesetzt wurde). Zwei Komfort-Buttons ermöglichen:

- **Kopieren** des Textes in die Zwischenablage.
- **Download** als `.txt`-Datei lokal.

### Qualitätstipps

- **Scharfe, kontrastreiche Bilder** liefern die besten KI-Ergebnisse. Skalieren Sie Screenshots vor dem Upload auf ihre native Auflösung.
- Für **gescannte PDFs** rendern Sie die Seite zunächst als PNG (300 DPI) — die KI kann sie dann direkt transkribieren.
- Wenn die KI bei Handschrift oder stilisierten Schriften unsicher ist, wechseln Sie zu **Nur Tesseract** für einen deterministischeren Durchlauf.
- Wenn das Ergebnis `NO_TEXT_FOUND` lautet, enthält das Bild wahrscheinlich keinen lesbaren Text — versuchen Sie eine schärfere Version desselben Bereichs.

### Verlauf

Jede Extraktion wird als **ImageExtractionRecord** in der lokalen H2-Datenbank gespeichert (neueste zuerst, bis zu 200 Datensätze). Klappen Sie die **Verlauf**-Karte am Ende der Seite auf, um alle bisherigen Läufe mit Status, Dateigröße und Zeitstempel zu sehen. Im Verlauf können Sie:

- Einen früheren Datensatz **erneut laden**, um den extrahierten Text erneut anzuzeigen.
- Einen einzelnen Datensatz **löschen** oder den **gesamten Verlauf** über die Buttons in der Verlaufskarte entfernen.

Ein Lauf, der länger als `stacklens.analysis.stale-running.default-timeout` (Standard 5 Minuten) auf `RUNNING` steht, wird als `FAILED` angezeigt; nach einem Neustart werden verbliebene `PENDING`-/`RUNNING`-Zeilen auf `CANCELLED` gesetzt, sodass die Verlaufsliste nie Zombie-Einträge enthält.

### KI-Chat-Integration

Klicken Sie auf den **KI Chat**-Button in der Kopfzeile der Seite, um den KI-Assistenten-Drawer mit Bild-Extraktions-Kontext zu öffnen. Der Drawer enthält fertige Vorschläge (unterstützte Formate, Dateigrößenlimits, OCR-Engines, Verhalten bei leeren Bildern, Mehrfach-Extraktion usw.) und verweist auf den aktuellen Lauf, sodass Folgefragen zu einem bestimmten Ergebnis möglich sind, ohne ihn zu verlieren.

### Eigene Prompts

Der Standard-Extraktions-Prompt ist `imageextraction.extractText` (konfigurierbar unter *KI-Prompt*). Er weist das multimodale Modell an, Lesereihenfolge, Absatzstruktur, Umlaute und Zeichensetzung beizubehalten und für Bilder ohne lesbaren Text `NO_TEXT_FOUND` auszugeben. Öffnen Sie den Prompt-Editor aus der Kopfzeile, um ihn pro Anwendungsfall zu überschreiben (z. B. ein strengerer Nur-Rechnungen-Prompt).

---

## Lasttest-Analyse

Die Lasttest-Analyse-Seite analysiert PHP-Quellcode aus einem GitLab-Projekt auf Performance-Engpässe und generiert k6-Lasttest-Skripte.

### Eine Analyse starten

1. Klappen Sie im linken Bereich eine **GitLab-Konfiguration** auf, um die verfügbaren Projekte anzuzeigen.
2. Klicken Sie auf ein Projekt, um es auszuwählen.
3. Klicken Sie auf **Analyse starten**.

StackLens lädt die PHP-Quelldateien und führt eine zweistufige KI-Analyse durch:

1. **Engpass-Erkennung** — die KI analysiert den Code auf:

| Engpass-Typ | Beschreibung |
|-------------|-------------|
| Langsame DB-Query | Datenbankaufrufe ohne Indizes, Full-Table-Scans oder unbegrenzte Ergebnismengen |
| Fehlender Cache | Daten, die wiederholt ohne Caching abgefragt werden |
| Synchrone Kette | Sequenzielle blockierende Aufrufe, die parallel ausgeführt werden könnten |
| PHP-FPM Engpass | Prozessmanager-Sättigung, Worker-Erschöpfungs-Muster |
| Ressourcen-Lock | Mutex-, Datei-Lock- oder Konflikte bei geteilten Ressourcen |
| Fehlende Asynchronität | Langwierige Aufgaben, die den Web-Worker blockieren statt in einer Queue zu landen |
| N+1-Query | Durch Schleifen amplifizierte Abfrage-Muster |

2. **Skript-Generierung** — erzeugt:
   - Ein **k6-Skript** (`.js`) für die erkannten Endpunkte

### k6 aus StackLens ausführen

Nach einer erfolgreichen Analyse auf **Ausführen** unter der k6-Skriptkarte klicken. Vor der Ausführung die Last-Parameter konfigurieren:

> **Hinweis:** Beim Betrieb als Docker-Container ist k6 bereits im Image enthalten (`/usr/local/bin/k6`) — kein zusätzlicher Installations-Schritt nötig. Bei nativem Start (`./start.sh`) muss k6 separat über `scripts/install-k6.sh` installiert werden.

| Parameter | Beschreibung |
|-----------|-------------|
| Umgebungs-URL | Basis-URL des Zielsystems |
| Peak VUs | Maximale gleichzeitige virtuelle Nutzer |
| Ramp-up | Sekunden, um Nutzerzahl schrittweise von 0 auf Peak zu erhöhen |
| Lastphase (Steady) | Sekunden, in der die Last konstant bei Peak VUs gehalten wird |
| Ramp-down | Sekunden, um Nutzerzahl nach der Lastphase schrittweise auf 0 zu senken |
| Bearer Token | Optionales Auth-Token (gesendet als `Authorization: Bearer …`) |

Aus Docker-Compose oder Konfigurationsdateien des Repos automatisch erkannte Umgebungen stehen im Umgebungs-Dropdown zur schnellen Auswahl bereit.

### k6-Ergebnisse lesen

Nach Abschluss eines k6-Runs werden folgende Metriken angezeigt:

| Kachel | Beschreibung |
|--------|-------------|
| Ergebnis | Bestanden (Exit 0) oder fehlgeschlagen (Exit ungleich 0) |
| Checks | Anteil bestandener k6-Checks (benutzerdefinierte Bedingungen im Skript) |
| P95 | 95. Perzentile der HTTP-Antwortzeit — 95 % der Anfragen waren schneller |
| Fehlerrate | Anteil der HTTP-Anfragen, die mit einem Fehler endeten |
| Anfragen | Gesamtanzahl + Durchsatz in Anfragen/Sek. |
| Laufzeit | Gesamtlaufzeit des Tests inkl. Ramp-up und Ramp-down |
| Median | Median (P50) der Antwortzeit |
| P90 | 90. Perzentile der Antwortzeit |
| Max VUs | Spitzenwert gleichzeitig aktiver virtueller Nutzer |
| Empfangene Daten | Gesamtmenge der vom Server empfangenen Daten |
| Gesendete Daten | Gesamtmenge der an den Server gesendeten Daten — relevant bei POST- oder Upload-Szenarien |
| Dropped Iterations | Anzahl der VU-Iterationen, die k6 nicht starten konnte — deutet auf Ressourcenerschöpfung oder eine zu aggressive Laststufe hin. Wird nur angezeigt, wenn > 0 |
| TCP-Connect Ø | Durchschnittliche TCP-Verbindungszeit (`http_req_connecting`). Nur bei Werten > 0 angezeigt — null bedeutet, Keep-Alive-Verbindungen wurden wiederverwendet |
| TLS-Handshake Ø | Durchschnittliche TLS-Handshake-Zeit (`http_req_tls_handshaking`). Erscheint nur bei HTTPS-Endpunkten |
| Stddev | Standardabweichung der HTTP-Antwortzeit — hohe Werte deuten auf inkonsistentes Server-Verhalten unter Last hin |

### Verlauf

Alle Analysen eines Projekts werden im Tab **Verlauf** gespeichert. Sie können zwischen vergangenen Analysen wechseln, um Ergebnisse im Laufe der Zeit zu vergleichen.

---

## Sicherheitsanalyse

Die Sicherheitsanalyse-Seite führt ein mehrrundiges KI-gestütztes Sicherheits-Audit („Bug Hunting“) eines Code-Repositories durch. Jede Iteration baut auf der vorherigen auf und sucht nach immer tieferen Schwachstellen.

### Eine Analyse starten

1. Wählen Sie im linken Bereich ein **lokales Repository**, klappen Sie eine **GitLab-Konfiguration** auf oder wählen Sie ein **GitHub-Repository**.
2. Legen Sie die **Anzahl der Iterationen** fest (Standard: 3 — mehr Iterationen finden tiefere Probleme, benötigen aber mehr Zeit).
3. Klicken Sie auf **Sicherheitsanalyse starten**.

StackLens lädt die Quelldateien und führt die iterative KI-Analyse durch. Ein Live-Fortschrittsanzeiger zeigt die aktuelle Iteration und den Schritt.

### Ergebnis-Tabs

| Tab | Inhalt |
|-----|--------|
| **Zusammenfassung** | KI-generierter Überblick über die allgemeine Sicherheitslage |
| **Befunde** | Filtierbare Liste einzelner Befunde mit Schweregrad-Badges (HIGH / MEDIUM / LOW) |
| **Dateiänderungen** | Vorgeschlagene Korrekturen für jeden Befund — bereit zum Kopieren oder Anwenden |

Jeder Befund enthält:
- Betroffene **Datei** und Zeilennummer
- **Typ** (z. B. SQL Injection, XSS, Unsichere Deserialisierung, Fehlende Auth, OWASP-Kategorie)
- **Schweregrad** (HIGH / MEDIUM / LOW)
- **Beschreibung** der Schwachstelle
- KI-generierter **Korrekturvorschlag**

### Fix generieren

Klicken Sie auf **Fix generieren** bei einem einzelnen Befund, damit StackLens einen konkreten Code-Fix erstellt und ihn direkt in die Versionsverwaltung überträgt:

- Für **GitLab**-Projekte — ein neuer Branch wird erstellt und ein Merge Request geöffnet. Die MR-URL öffnet sich automatisch im **Standard-Browser Ihres Systems**.
- Für **GitHub**-Repositories — ein Pull Request wird erstellt. Die PR-URL öffnet sich im **Standard-Browser Ihres Systems**.
- Für **lokale Repositories ohne Remote** — die korrigierten Dateiinhalte werden inline zur manuellen Übernahme angezeigt.

### Verlauf

Alle vergangenen Analysen eines Projekts erscheinen im Tab **Verlauf**. Sie können frühere Läufe aufrufen, um Sicherheitsverbesserungen über die Zeit nachzuverfolgen.

---

## Docker-Test (Entwicklermodus)

Die Seite **Docker-Test** (`/docker-test`) ist ein In-App-Kontrollfeld für den lokalen Docker-Compose-Stack, der die Demo-Daten von StackLens bereitstellt. Sie erscheint nur dann in der Seitenleiste, wenn die Entwickleroption *Docker-Testumgebung* aktiviert ist, und ist für Engineers gedacht, die an StackLens selbst arbeiten oder Features gegen eine echte, wegwerfbare Umgebung ausprobieren.

### Was sie tut

- **Demo-Stack starten** — `docker compose up` für alle Demo-Services (`fake-atlassian`, `fake-gitlab`, `fake-github`, `mysql-demo`, `fake-backend`, `prometheus`, `grafana`, `fake-exporter`) mit einem Klick. Der Fortschritt wird per Server-Sent-Events zurück auf die Seite gestreamt, sodass Sie sehen, welcher Container als nächstes startet.
- **Demo-Stack zurücksetzen** — `docker compose down -v` für dieselben Services, inklusive Löschen der Volumes, sodass der nächste `start` ein sauberer Zustand ist. Nützlich, um einen Bug aus einer leeren Datenbank zu reproduzieren.
- **Live-Log-Tail** — die Seite zeigt die Live-Ausgabe von `docker compose` während der Operation, mit einer farbigen Status-Pille (`running` / `ok` / `failed`).
- **Demo-JSON-Fixtures bearbeiten** — die mitgelieferten Mock-Services (Atlassian, GitLab, GitHub, Backend) lesen ihre Daten aus JSON-Dateien im Container. Die Seite listet diese Dateien und erlaubt das Anzeigen oder Bearbeiten inline. Änderungen werden zurück in den Container gespeichert, sodass der nächste API-Aufruf das neue Fixture zurückgibt.

### Voraussetzungen

- Docker (oder eine kompatible Runtime) muss installiert sein und der aktuelle Benutzer muss auf `/var/run/docker.sock` zugreifen können. Unter Linux setzt das Installations-Skript die passende GID; unter macOS genügt Docker Desktop.
- Für die **Auto-Start**-Variante aktivieren Sie die Entwickleroption *Demo-Container automatisch mit StackLens starten*. StackLens führt dann bei jedem Start `docker compose up` für das `demo`-Profil aus — praktisch, wenn Sie täglich gegen die Mocks iterieren.

### Eine Docker-Test-Sitzung durchführen

1. Öffnen Sie die Seite **Docker-Test** aus der Seitenleiste.
2. Klicken Sie auf **Demo-Stack starten**, um die Container hochzufahren. Die Status-Pille wird blau, solange die Operation läuft, und grün (`ok`), sobald jeder Container gesund ist. Fehler zeigen eine rote Pille; die Live-Ausgabe von `docker compose` — inklusive Fehlertext — läuft im Log-Panel der Seite mit.
3. Nutzen Sie StackLens wie gewohnt — jede Analyse, die einen echten Upstream benötigt (Jira, GitLab, GitHub, Grafana, MySQL, k6), spricht nun mit den lokalen Mocks.
4. Wenn Sie fertig sind, klicken Sie auf **Demo-Stack zurücksetzen**, um alles abzubauen und die Volumes zu löschen. Die Seite kehrt in den Ruhezustand zurück.
5. Öffnen Sie das Accordion **Datendateien**, um ein Fixture zu prüfen oder zu ändern. Speichern Sie mit **Speichern** — die Änderung wird ohne Container-Neustart beim nächsten API-Aufruf wirksam.

### Hinweise

- Die Start/Reset-Endpunkte werden per SSE gestreamt, sodass die Verbindung offen bleibt, bis die Operation endet. Das Schließen des Tabs bricht den zugrundeliegenden `docker compose`-Aufruf **nicht** ab.
- Die Seite spricht nie Ihre echten Services an — alles ist auf das `demo`-Profil in `docker/docker-compose.yml` beschränkt.

---

## JWT-Analyse

Die JWT-Analyse-Seite dekodiert und prüft beliebige JSON Web Tokens, ohne den Token an einen externen Service zu senden. Die gesamte Verarbeitung erfolgt auf dem StackLens-Server.

### Eine Analyse durchführen

1. Fügen Sie die JWT-Zeichenkette in das Textfeld ein (die vollständige `eyJ...`-Token-Zeichenkette).
2. Klicken Sie auf **Analysieren**.

### Ergebnisse lesen

**Validierungsstatus:**

| Status | Bedeutung |
|--------|-----------|
| `VALID` | Token hat einen `exp`-Claim, der noch nicht erreicht wurde |
| `EXPIRED` | Token hat einen `exp`-Claim, der in der Vergangenheit liegt |
| `UNLIMITED` | Token hat keinen `exp`-Claim |

**Angezeigte Claims:**

| Feld | JWT-Claim | Beschreibung |
|------|-----------|--------------|
| Algorithmus | `alg` (Header) | Signaturalgorithmus (z. B. `RS256`, `HS256`) |
| Token-Typ | `typ` (Header) | Üblicherweise `JWT` |
| Aussteller | `iss` | Der Service, der den Token ausgestellt hat |
| Subjekt | `sub` | Die Entität, die der Token repräsentiert (z. B. Benutzer-ID) |
| Zielgruppe | `aud` | Beabsichtigte(r) Empfänger |
| Ablauf | `exp` | Formatiert als UTC-Zeitstempel + lesbarer Countdown |
| Ausgestellt am | `iat` | Wann der Token ausgestellt wurde (UTC) |
| Nicht vor | `nbf` | Token ist vor diesem Zeitpunkt nicht gültig (UTC) |

Sowohl der dekodierte **Header** als auch das **Payload** werden als formatiertes JSON für vollständige Claim-Inspektion angezeigt.

---

## Wissensdatenbank (RAG)

Die Seite **Wissensdatenbank** (`/knowledge`) verwaltet dauerhaftes Wissen, das der globale KI-Chat als zusätzlichen Kontext verwenden kann. Beim ersten Start legt StackLens automatisch zwei Wissensdatenbanken an:

- **Allgemeines Wissen** für manuelle Texte, Dateien sowie Confluence- und Jira-Quellen.
- **StackLens-Analyseergebnisse** als systemverwaltetes Ziel für Ergebnisse aus Review-, Sprint-Review- und Ticket-Workflows.

### Embedding-Verbindung einrichten

Bevor Inhalte vektorisiert oder im Chat gefunden werden können, öffnen Sie den Tab **Embedding-Konfiguration**:

1. Wählen Sie einen Provider und tragen Sie das Embedding-Modell ein.
2. Ergänzen Sie je nach Provider die URL oder den API-Key.
3. Klicken Sie auf **Verbindung testen**. Ein erfolgreicher Test zeigt Provider, Modell und Vektordimension.
4. Klicken Sie auf **Speichern**.

| Provider | Typische Konfiguration | Hinweise |
|----------|------------------------|----------|
| **Ollama** | `http://localhost:11434`, Modell `nomic-embed-text` | Läuft lokal; das Modell muss in Ollama installiert sein. |
| **OpenAI** | Modell z. B. `text-embedding-3-small` + API-Key | Inhalte und Chat-Anfragen werden zum Erzeugen der Vektoren an OpenAI gesendet. |
| **OpenRouter** | Embedding-Modell + OpenRouter-API-Key | Verwendet den OpenRouter-kompatiblen Embedding-Endpunkt. |

> **Wichtig:** Die Embedding-Konfiguration ist von der normalen KI-Konfiguration getrennt. Auch die Chat-Frage wird für die semantische Suche mit diesem Provider eingebettet. Ist der Provider nicht erreichbar oder sind Embeddings deaktiviert, liefert die Wissenssuche keine Treffer; es gibt keinen Stichwort-Fallback.

Wenn Sie zu einem Modell mit anderer Vektordimension wechseln, müssen die Quellen mit dem neuen Modell erneut eingelesen werden. Synchronisieren Sie Jira-/Confluence-Quellen beziehungsweise legen Sie manuelle Texte und Dateien neu an und bauen Sie danach unter **Wartung und Zurücksetzen** den Index neu auf.

### Wissensdatenbanken verwalten

Im Tab **Wissensdatenbanken** können Sie fachlich getrennte Bases anlegen, beispielsweise „Payment“, „Betrieb“ oder „Onboarding“.

- Jede Quelle gehört genau zu einer Wissensdatenbank.
- Neu angelegte Bases stehen dem globalen KI-Chat automatisch zur Verfügung.
- Nur **aktive** Bases werden durchsucht. Nicht systemverwaltete Bases lassen sich über den Pause-/Play-Button deaktivieren oder wieder aktivieren.
- Die systemverwaltete Base für Analyseergebnisse kann in der UI weder deaktiviert noch gelöscht werden.
- Beim Löschen einer Base werden deren Quellen, Dokumente, Chunks, Embeddings und Lucene-Index entfernt. Zur Sicherheit muss der Name exakt bestätigt werden.

Die **Übersicht** zeigt die Anzahl der Bases und Quellen sowie den aktuell konfigurierten Embedding-Provider und das Modell.

### Quellen hinzufügen und aktualisieren

Öffnen Sie den Tab **Inhalte** und wählen Sie den passenden Quellentyp:

| Quelle | Eingabe und Verhalten |
|--------|-----------------------|
| **Manuell** | Titel und Freitext. Geeignet für Runbooks, Architekturentscheidungen oder Teamkonventionen. |
| **Datei** | PDF, DOC, DOCX, TXT, MD oder MARKDOWN bis 50 MB. Der integrierte Dateibrowser liest die Datei lokal ein. |
| **Confluence** | Bestehende Confluence-Verbindung, Startseiten-URL, optional Unterseiten und maximale Tiefe. Die URL muss zur konfigurierten Instanz gehören. |
| **Jira** | Bestehende Jira-Verbindung, Root-Ticket-Key oder URL, optional Untertickets, Kommentare und maximale Tiefe. |
| **Analyseergebnisse** | Abgeschlossene Review-, Sprint-Review-, Ticket-Erstellungs-, Ticket-Aufbereitungs- und Ticket-Zusammenfassungs-Ergebnisse werden automatisch in die systemverwaltete Base publiziert. |

Beim Speichern extrahiert StackLens den Text, zerlegt ihn mit Überlappung in Chunks, erzeugt Embeddings und schreibt Metadaten sowie Chunk-Text nach H2 und die Vektoren in den lokalen Lucene-KNN-Index. Der Quellenstatus wechselt von `PENDING` zu `READY`; bei Problemen erscheint `FAILED` mit einer Fehlermeldung.

- **Synchronisieren** lädt Jira- und Confluence-Hierarchien erneut und ersetzt ihre bisherigen Dokumente und Chunks.
- **Löschen** entfernt die Quelle einschließlich aller daraus erzeugten Dokumente, Chunks und Vektoren.
- Manuelle Texte und Dateien sind Momentaufnahmen. Für einen neuen Stand löschen Sie die Quelle und fügen sie erneut hinzu.

### Wissen im globalen KI-Chat verwenden

Bei jeder Chat-Nachricht durchsucht StackLens alle aktiven Wissensdatenbanken:

1. Die Frage wird mit dem konfigurierten Embedding-Modell vektorisiert.
2. Lucene sucht lokal per KNN und Kosinus-Ähnlichkeit nach den passendsten Chunks.
3. Die besten Treffer werden in den Chat-Prompt eingefügt.
4. Verwendete Treffer erscheinen als Quellen-Chips und als Citation wie `[KB:document-4711#chunk-3]`.

Die eigentlichen Chunk-Texte, Metadaten und Indizes bleiben lokal. Bei einem entfernten Embedding-Provider verlassen jedoch die zu vektorisierenden Inhalte und Chat-Suchfragen das System. Verwenden Sie Ollama, wenn diese Daten vollständig lokal verarbeitet werden sollen.

### Export und Import

**Alle exportieren** erstellt eine passwortgeschützte `.slbundle`-Datei. Das Bundle enthält pro Wissensdatenbank ein eigenes `.slknowledge`-Archiv mit Quellen, Dokumenten, Chunks und standardmäßig den Embedding-Vektoren. Die Einzelarchive werden mit **AES-256-GCM** verschlüsselt; das Passwort wird nicht gespeichert. Verbindungsdaten und API-Keys sind nie Bestandteil des Exports.

Der Import-Dateibrowser akzeptiert `.slknowledge` und `.slbundle`. Geben Sie das Exportpasswort ein und wählen Sie einen Modus:

| Modus | Wirkung |
|-------|---------|
| **Neue Kopie** | Legt neue Bases an und schreibt stabile IDs neu; vorhandene Daten bleiben unverändert. |
| **Zusammenführen** | Fügt den Archivinhalt zur passenden lokalen Base hinzu und behält lokale Inhalte. |
| **Ersetzen** | Leert die passende Ziel-Base und stellt anschließend den Archivstand wieder her. |
| **Nur fehlende** | Ergänzt nur noch nicht vorhandene beziehungsweise veraltete Inhalte. |

Bewahren Sie Passwort und Archiv gemeinsam sicher auf. Ohne das Passwort kann ein verschlüsseltes Wissensarchiv nicht wiederhergestellt werden.

### Wartung, Reindexierung und Zurücksetzen

- **Alle Wissensdatenbanken reindexieren** liest vorhandene Chunks und Vektoren aus H2 und baut nur den lokalen Lucene-Index neu auf. Verwenden Sie dies nach einem Indexschema-Update oder nach einer erneuten Einbettung; die Quelldaten bleiben erhalten.
- **Wissensdatenbank zurücksetzen** löscht alle Bases, Quellen, Dokumente, Chunks, Vektoren, Jobs, die Embedding-Konfiguration und den Lucene-Index. Danach werden die beiden leeren Standard-Bases erneut angelegt.
- Der allgemeine Werks-Reset unter **Konfiguration → Zurücksetzen** bewahrt die Wissensdatenbank standardmäßig. Nur `KNOWLEDGE_RESET_ON_FACTORY_RESET=true` bezieht sie in diesen Reset ein.

---

## Konfiguration

Die Konfigurationsseite verwaltet alle Datenquellenverbindungen. Klappen Sie jeden Abschnitt auf, um Einträge hinzuzufügen oder zu bearbeiten.

Oben auf der Seite können Sie alle Konfigurationen **exportieren** (verschlüsselt, AES-256-GCM, passwortgeschützt) oder ein zuvor exportiertes Backup **importieren**. Der Export umfasst alle 8 Verbindungstypen: GitLab, Datenbank, Confluence, Grafana, Jira, KI-Anbieter, GitHub-Repositories und MCP-Server. Zusätzlich werden die gespeicherte Sprache, das Theme, alle Entwickleroptionen (Prompt-Buttons, Textfilter, Token-Anzeige, Docker-Testumgebung und deren Autostart) sowie die übrigen dateibasierten UI-Einstellungen gesichert. Beim Import auf einer bereits konfigurierten Instanz kann die Option **Nicht überschreiben** (`skipIfNotEmpty`) vorhandene Einträge und UI-Einstellungen schützen — nur Konfigurationstypen ohne bestehende Einträge werden aus dem Backup befüllt.

### GitLab

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename für diese Verbindung |
| Basis-URL | GitLab-Instanz-URL oder Gruppen-URL (z. B. `https://gitlab.example.com/my-group`) — der Gruppenpfad wird verwendet, um Projektsuchen einzugrenzen |
| API-Token | Persönliches Zugriffstoken mit `read_api`-Berechtigung |
| Aktiv | Umschalten zum Aktivieren/Deaktivieren dieser Verbindung |

### Datenbank

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename |
| Typ | `PostgreSQL`, `MySQL` oder `SQL Server` |
| Host | Datenbankserver-Hostname oder IP |
| Port | TCP-Port (Standard: PostgreSQL 5432, MySQL 3306, SQL Server 1433) |
| Datenbank | Datenbankname |
| Benutzername / Passwort | Authentifizierungsdaten |
| Schema | Optional: Scan auf bestimmte Schemata einschränken (kommagetrennt) |
| Aktiv | Umschalten zum Aktivieren/Deaktivieren |

### Confluence

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename |
| Basis-URL | Confluence-Server-URL |
| Modus | **Cloud** (REST API `/wiki/rest/api`) oder **Server/DC** (REST API `/rest/api`) |
| Benutzername | Confluence-Benutzername |
| API-Token | Confluence-API-Token oder Passwort |
| Space-Schlüssel | Kommagetrennte Liste der zu durchsuchenden Spaces |
| Suchwurzel-URL | Optional: Suche auf einen bestimmten Seitenunterbaum einschränken |
| Aktiv | Umschalten zum Aktivieren/Deaktivieren |

### Grafana

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename |
| Basis-URL | Grafana-Instanz-URL |
| API-Token | Service-Account-Token mit Viewer-Berechtigungen |
| Org-ID | Grafana-Organisations-ID (Standard: 1) |
| Panel-Abfragen scannen | Auch Panel-Abfrage-JSON-Ziele durchsuchen (nicht nur Dashboard-/Panel-Titel) |
| Aktiv | Umschalten zum Aktivieren/Deaktivieren |

### Jira

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename |
| Basis-URL | Jira-Instanz-URL |
| Modus | **Cloud** (REST API v3, ADF-Beschreibungen) oder **Server/DC** (REST API v2, Klartextbeschreibungen) |
| Benutzername | Jira-Benutzername oder E-Mail |
| API-Token | API-Token (Cloud) oder Passwort (Server/DC) |
| Projekt-Schlüssel | Optional: kommagetrennte Projekt-Schlüsselfilter (z. B. `PAY,ARCH`) |
| Aktiv | Umschalten zum Aktivieren/Deaktivieren |

### GitHub-Repositories

Konfigurieren Sie GitHub-Repository-Verbindungen für die Verwendung in der Code-Qualitätsanalyse, Bug-Hunting und Ticket-Analyse.

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename für diese Verbindung |
| Owner | GitHub-Benutzername oder Organisationsname (z. B. `my-org`) |
| Repository | Repository-Name (z. B. `my-repo`) |
| Branch | Optional: Standard-Branch für den Scan (z. B. `main`). Falls leer, wird der Standard-Branch des Repositories verwendet. |
| Access Token | GitHub Personal Access Token (PAT) mit `repo`-Berechtigung. Falls leer, wird das globale GitHub-Token aus der KI-Konfiguration für öffentliche Repositories verwendet. |
| Aktiv | Umschalten zum Aktivieren/Deaktivieren dieser Verbindung |

### MCP-Server

Konfigurieren Sie Model-Context-Protocol-(MCP)-Server-Verbindungen. Ein MCP-Server fungiert als Gateway, das StackLens-Funktionen mit Jira, Confluence, GitLab und GitHub über das MCP-Protokoll verbindet. Wenn aktiv, kann StackLens Aufrufe über den MCP-Server routen statt jeden Dienst direkt zu kontaktieren — und jeder MCP-kompatible Client (z. B. VS Code GitHub Copilot) kann denselben Server verwenden.

| Feld | Beschreibung |
|------|--------------|
| Name | Anzeigename für diesen MCP-Server |
| Basis-URL | URL des MCP-Servers (z. B. `http://localhost:7456`) |
| Auth-Token | Optionales Bearer-Token (konfiguriert über `MCP_AUTH_TOKEN` auf der Server-Seite) |
| Aktiv | Master-Schalter — leitet alle konfigurierten Anbieter über diesen MCP-Server |
| Route Jira / Confluence / GitLab / GitHub / Grafana / Datenbank | Anbieter-spezifische Routing-Schalter |
| Jira / Confluence / GitLab / GitHub / Grafana Anmeldedaten | Upstream-Anmeldedaten, die an den MCP-Server weitergeleitet werden |
| Autostart verwaltet | Wenn aktiviert, startet StackLens den gebündelten MCP-Server-Prozess automatisch |

**Von StackLens synchronisieren:** Klicken Sie auf **Von StackLens synchronisieren**, um die MCP-Server-Anmeldedaten automatisch aus Ihren vorhandenen aktiven StackLens-Verbindungen (Jira, Confluence, GitLab, GitHub) zu befüllen. Dabei wird eine Standard-Zeile namens „StackLens MCP" erstellt oder bei späteren Synchronisierungen aktualisiert.

**Verbindungstest:** Klicken Sie auf **Verbindung testen**, um zu prüfen, ob der MCP-Server erreichbar ist, und seine verfügbaren Tools aufzulisten.

**Gebündelter MCP-Server-Prozess:** StackLens enthält einen eingebauten MCP-Server (`mcp-server/`). Sie können ihn direkt von der MCP-Konfigurationsseite aus starten, stoppen und neu starten, ohne die App zu verlassen.

### KI

Konfigurieren Sie den für alle Analysen verwendeten KI-Anbieter:

| Feld | Beschreibung |
|------|--------------|
| GitHub-Token | Persönliches Zugriffstoken für GitHub Models oder Copilot CLI Authentifizierung |
| Modell | KI-Modellname (z. B. `gpt-4o`, `claude-sonnet-4.6`) |
| Sprache | Anzeigesprache der Benutzeroberfläche: **English** oder **Deutsch**. Steuert auch die Sprache, in der alle KI-Analyseergebnisse geschrieben werden. Änderung wirkt sofort — kein Neustart erforderlich. |

Der aktive KI-Anbieter wird auf der KI-Einstellungsseite ausgewählt (der Wert wird in `AiConfig.aiProvider` gespeichert). Die verfügbaren Anbieter spiegeln das `AiProvider`-Enum:

| Anbieter | Backend | Standardmodell | Token |
|----------|---------|----------------|-------|
| `copilot` (Standard) | GitHub Copilot CLI / GitHub Models (OpenAI-kompatible API) | `gpt-5.4-mini` | `GITHUB_TOKEN` |
| `claudecode` | Anthropic-Claude-API über den `claudecode`-Adapter (Anthropic-Messages-API, Streaming) | `claude-sonnet-4-5` | `ANTHROPIC_API_KEY` |
| `openai` | OpenAI-API (Streaming + Non-Streaming) | `gpt-5.2` | `OPENAI_API_KEY` |
| `openrouter` | OpenRouter-REST-API (routet zu jedem unterstützten Upstream-Modell) | `openai/gpt-4.1` | `OPENROUTER_API_KEY` |
| `ollama` | Lokale Ollama-Instanz unter `http://localhost:11434` (oder `OLLAMA_URL`) | `gpt-5.4-mini` | keins |
| `lmstudio` | Lokale LM Studio-Instanz unter `http://localhost:1234` (oder `LM_STUDIO_URL`); das Modell wird vor jeder Anfrage automatisch geladen | `local-model` | keins |
| `compatible` | Beliebige OpenAI-kompatible Chat-Completions-API mit eigener Basis-URL (QwenCloud, DeepSeek, Groq, vLLM, llama.cpp, …) | `qwen3.8-flash` | `OPENAI_COMPATIBLE_API_KEY` → `DASHSCOPE_API_KEY` |

**Anbieterverhalten im Detail:**

- **`copilot`** — nutzt `CopilotCliAdapter` zur Ansteuerung der `gh copilot`-CLI; fällt auf den GitHub-Models-REST-Endpunkt zurück, falls die CLI nicht im `PATH` ist. **Modell** auf ein beliebiges GitHub-Models- oder Copilot-Modell setzen.
- **`claudecode`** — ruft die Anthropic-Messages-API direkt über `AnthropicToolLoop` auf (verwendet von `ClaudeCodeAdapter`). Das Token löst `ClaudeCodeTokenLookupPortAdapter` in dieser Reihenfolge auf: gespeicherter Key in den KI-Einstellungen, `CLAUDE_CODE_OAUTH_TOKEN`, `ANTHROPIC_API_KEY`, dann das Claude-Code-Login selbst (`ClaudeCodeCredentialFileStore` für `~/.claude/.credentials.json` / `~/.config/claude-code/auth.json`, danach `ClaudeCodeCredentialKeychainStore` für den macOS-Keychain-Eintrag). Ein abgelaufenes Login-Token wird über `AnthropicOAuthTokenAdapter` erneuert und in den Store zurückgeschrieben. **Modell** auf den Anthropic-Modell-Identifier setzen (z. B. `claude-sonnet-4-6`, `claude-opus-4-6`).
- **`ollama`** — verwendet `OllamaAdapter` gegen den OpenAI-kompatiblen Endpunkt `/v1/chat/completions`. **Modell** auf den installierten Modellnamen setzen (z. B. `qwen3-coder:30b-a3b-q4_k_m`). **Kontextgröße** (Standard: 32.768 Tokens) ist in den KI-Einstellungen konfigurierbar.
- **`lmstudio`** — verwendet `LmStudioAdapter` gegen die LM-Studio-REST-API; der Adapter lädt das Modell bei Bedarf via `POST /api/v1/models/load` und prüft `GET /api/v1/models`, um redundante Ladevorgänge zu vermeiden.
- **`openai`** — `OpenAiAdapter` gegen die OpenAI-REST-API. **Modell** auf das gewünschte OpenAI-Modell setzen (z. B. `o3`, `gpt-4.1`).
- **`openrouter`** — `OpenRouterAdapter` gegen die OpenRouter-REST-API. **Modell** auf den OpenRouter-Modell-Identifier setzen (z. B. `anthropic/claude-3.5-sonnet`, `openai/gpt-4o`). Über die Schaltfläche **Testen** auf der KI-Einstellungsseite können Schlüssel und Modell vor dem Speichern geprüft werden.
- **`compatible`** — `OpenAiCompatibleAdapter` gegen `POST {Basis-URL}/chat/completions` mit `Authorization: Bearer`. Die **Basis-URL** ist ein Freitextfeld; die Vorgabe-Liste enthält die geprüften QwenCloud-Endpunkte (Pay-as-you-go international, Pay-as-you-go Festland-China, Token Plan, Coding Plan) sowie DeepSeek, Groq und OpenAI. Wichtig bei QwenCloud: Token-Plan- und Coding-Plan-Keys (`sk-sp-…`) sind an **ihre eigene** Basis-URL gebunden — passt beides nicht zusammen, antwortet der Endpunkt mit HTTP 401. Die **Modell-Auswahl** ist ein Dropdown: sobald der Anbieter `GET {Basis-URL}/models` veröffentlicht, lädt StackLens die Modellliste des Accounts und stellt alle verfügbaren Modelle zur Auswahl — dasselbe Verhalten wie bei GitHub Copilot. Ein Wechsel der Vorgabe oder der Basis-URL verwirft die alte Auswahl und lädt die Liste des neuen Endpunkts neu; das gespeicherte Modell bleibt beim Verlassen und Wiederaufrufen der Konfiguration ausgewählt. Veröffentlicht der Endpunkt keine Liste, ersetzt ein Freitextfeld das Dropdown und die Modell-ID (z. B. `qwen3.8-flash`, `deepseek-v4-pro`) wird manuell eingetragen; validiert wird die Eingabe dann über eine Miniatur-Chat-Anfrage. Der Standardwert für Basis-URL und Modell ist über `stacklens.ai.compatible.default-base-url` bzw. `AI_COMPATIBLE_MODEL` konfigurierbar.

**Repository-Datei-Tools (lokale Anbieter):** Das Trio `RepoFileToolDefinitions` / `RepoFileToolExecutor` / `RepoFileTextToolCallParser` stellt eine Mini-Datei-API mit vier Werkzeugen (`list`, `read`, `write`, `delete`) bereit, die jeder Anbieter mit OpenAI-kompatibler Tool-Schleife (`openai`, `openrouter`, `compatible`, `lmstudio`, `ollama`) aufrufen kann, um Dateien in einem Ziel-Repository zu untersuchen und zu ändern. `ClaudeCodeAdapter` nutzt dieselben Werkzeuge über die Anthropic-Tool-Schleife (`AnthropicToolLoop`).

**Token-Nutzungsverfolgung:** Wenn die Entwickleroption *Token-Anzeige* aktiviert ist, zeigt jede Analyse-Seite eine kleine Fußleiste mit Prompt- / Completion- / Gesamt-Tokens, dem aktiven Anbieter und Modell sowie der Wanduhr-Dauer. Die Daten werden live von `GET /api/ai-usage/{kind}/{id}` abgerufen und vom `TokenTrackingCopilotPort`-Decorator befüllt.

### KeePassX-Import

StackLens kann Verbindungsdaten direkt aus einer **KeePassX-XML-Exportdatei** importieren. Damit können GitLab-, Datenbank-, Confluence-, Grafana- und Jira-Verbindungen aus einer bestehenden KeePass-Datenbank befüllt werden, ohne Anmeldedaten manuell einzugeben.

**So importieren Sie:**
1. Klicken Sie auf **KeePassX importieren** im Verbindungsbereich.
2. Ein Datei-Browser öffnet sich — navigieren Sie zur exportierten `.xml`-Datei und klicken Sie sie an.
3. StackLens analysiert die XML-Datei und zeigt eine Vorschautabelle der gefundenen Einträge.
4. Überprüfen Sie die Einträge und klicken Sie auf **Importieren**, um die entsprechenden Verbindungen anzulegen.

Um eine andere Datei auszuwählen, klicken Sie in der Vorschau auf **Andere Datei** — der Browser öffnet sich erneut.

### Autostart

StackLens kann beim Anmelden automatisch starten.

| Option | Beschreibung |
|--------|-------------|
| **Desktop-Modus** | Startet mit dem vollständigen JavaFX-Fenster (Standard) |
| **Server-Modus** | Startet nur als Hintergrund-Server (kein Fenster, zugänglich per Browser auf Port 8090) |

Autostart aktivieren oder deaktivieren über den Schalter im System-Bereich am unteren Ende der Konfigurationsseite. Unter macOS wird eine LaunchAgent-Plist erstellt; unter Linux eine `.desktop`-Autostart-Datei.

### Essenzielle Tools prüfen

StackLens benötigt die **GitHub CLI** (`gh`) und die **GitHub Copilot CLI** (`gh copilot`) für die Agenten-Pipeline und Copilot-basierte KI-Funktionen. Der Tools-Bereich auf der Konfigurationsseite zeigt an, ob diese installiert sind. Falls `gh` fehlt, klicken Sie auf **Installieren**, um die automatische Installation über Homebrew (macOS) oder das offizielle GitHub-apt-Repository (Ubuntu/Pop!_OS) auszulösen.

---

## KI Chat

Auf jeder Analyseseite steht ein kontextsensitiver KI-Assistent zur Verfügung. Klicken Sie auf den **KI Chat**-Button oben rechts in der Seitenkopfzeile, um die Chat-Seitenleiste zu öffnen.

Die Seitenleiste ist mit dem Kontext der aktuellen Seite vorbelegt:

- Auf der **Sicherheitsanalyse**-Seite — die aktiven Befunde und der Analysetext.
- Auf der **Code-Analyse**-Seite — die aktuellen Code-Qualitäts-Befunde und die Zusammenfassung.
- Auf der **Ticket-Analyse**-Seite — die aktuellen Ticket-Daten und das Analyseergebnis.
- Auf der **SQL-Analyse**-Seite — die aktuelle Abfrage und das DDL.
- Auf der **JWT-Analyse**-Seite — die dekodierten Token-Claims.
- Auf anderen Seiten — allgemeiner Kontext zur aktuellen Ansicht.

Verwenden Sie den KI Chat, um Rückfragen zu stellen, Erklärungen anzufordern, einzelne Befunde zu vertiefen oder Hilfe zu erhalten, ohne die aktuelle Seite verlassen zu müssen.

Zusätzlich wird jede Nachricht gegen die aktiven **Wissensdatenbanken** geprüft. Relevante Chunks werden vor der Antwort als RAG-Kontext eingefügt. Die verwendeten Dokumente bleiben über Quellen-Chips und `[KB:…]`-Citations sichtbar, sodass Sie nachvollziehen können, worauf sich die Antwort stützt.

---

## Seitenleiste

Die linke Seitenleiste bietet Zugriff auf alle StackLens-Funktionen sowie mehrere Schnellzugriff-Werkzeuge. Jede Sektion — **Analysen**, **Werkzeuge**, **Konfiguration**, **Hilfe** — ist per Drag-and-Drop umsortierbar, auch sectionübergreifend. Die Reihenfolge wird pro Browser im `localStorage`-Schlüssel `sl-nav-section-order` gespeichert.

### Analyseseiten

Einträge und Standard-Reihenfolge (Beschriftungen wie in der deutschen UI):

| Eintrag | Seite |
|---------|-------|
| Sicherheit | Sicherheitsanalyse (Bug Hunting) — `/security-analysis` |
| Code | Code-Qualitätsanalyse — `/code-analysis` |
| Pipeline | Pipeline-Analyse — `/pipeline-analysis` |
| Grafana | Grafana-Dashboard-Analyse — `/grafana-analysis` |
| JWT | JWT-Token-Analyse — `/jwt-analysis` |
| Lasttest | Lasttest-Analyse — `/lasttest-analysis` |
| Sprint | Sprint-Review-Analyse — `/review-analysis` (Markdown / HTML-Export eines abgeschlossenen Sprints, PPTX-Vorlage wird in-App gerendert) |
| Ticket-Aufbereitung | Sprint-Vorbereitung — `/ticket-refinement` |
| Sprint-Vorbereitung | Sprint-Review (ADF-Tabelle) — `/sprint-review`: KI prüft jedes Ticket eines Sprints und postet eine pro-Ticket ADF-V3-Statustabelle an ein Ziel-Ticket |
| Ticket-Erstellung | KI-Ticket-Erstellung — `/ticket-creation` |
| Ticket-Zusammenfassung | KI-Ticket-Zusammenfassung — `/ticket-summary` |
| SQL | SQL-Analyse — `/sql-analysis` |
| Logging | Log-Analyse + KI-Fix-Pipeline — `/logging` |
| Bild-Extraktion | KI-Text-Extraktion aus Bildern — `/image-extraction` |
| Ticket Implementation (PHP) | Ticket-Analyse + Agenten-Pipeline — `/ticket-implementation` |
| Code QA | Code-QA-Analyse (GitLab / GitHub Merge Requests und Pull Requests) — `/code-qa` |

### Werkzeuge

| Eintrag | Seite |
|---------|-------|
| Tool Erstellung | Tooling (KI-generierte Kotlin-Tools) — `/tooling` |
| Wissensdatenbank | Lokale RAG-Wissensdatenbanken, Quellen, Embeddings sowie Import/Export — `/knowledge` |
| Suche | Neue Suche — `/search` |
| Testdocker *(nur Entwicklermodus, in Produktion versteckt)* | Docker-Test-Umgebung — `/docker-test` |
| *ein Eintrag pro kompiliertem Tool* | Dynamischer Tool-Katalog — `/tool/{id}` |

### Konfiguration und Hilfe

| Eintrag | Seite |
|---------|-------|
| Konfiguration | Alle Verbindungsarten — `/config` |
| Dokumentation | In-App-Dokumentation — `/docs` |

### Dynamischer Tool-Katalog

Kompilierte Tools aus dem **Tooling**-Bereich erscheinen automatisch als benannte, anklickbare Einträge in der Seitenleiste unterhalb der Navigationslinks. Jedes Tool zeigt sein KI-generiertes Symbol. Ein Klick auf einen Tool-Eintrag öffnet es direkt im Tool-Viewer, wo Sie es ausführen und die Ausgabe einsehen können.

### Schnellzugriff-Buttons

Am unteren Ende der Seitenleiste befinden sich Symbol-Buttons für schnellen Zugriff auf Entwicklerwerkzeuge.

---

## Dokumentation

Die **Dokumentations**-Seite (`/docs`) ist über die Seitenleiste erreichbar und enthält zwei Tabs:

### Tab Benutzerhandbuch

Das Handbuch, das Sie gerade lesen — eine funktionsweise Einführung in alle StackLens-Fähigkeiten. Verfügbar auf Englisch und Deutsch (wechselt automatisch mit der konfigurierten UI-Sprache).

### Tab Anwendungsdokumentation

Detaillierte technische Referenz einschließlich:

- Architekturüberblick und Modulstruktur
- Vollständige REST-API-Referenz (alle Endpunkte, Anfrage-/Antwortformate)
- Datenmodell (alle Entitäten und ihre Spalten)
- Build-, Packaging- und Testanweisungen

Beide Tabs unterstützen eine Volltextsuche im Dokument: Tippen Sie in die Suchleiste oben auf der Seite und verwenden Sie Enter / Shift+Enter, um zwischen Treffern zu navigieren.

---

## Tipps & Shortcuts

- **Eine Suchsitzung erneut öffnen:** Klicken Sie auf eine beliebige Zeile der Sitzungshistorie auf der Suchseite, um zu diesen Ergebnissen zurückzuspringen.
- **Eine laufende Suche abbrechen:** Verwenden Sie den **Suche abbrechen**-Button, der auf der Suchseite während einer laufenden Suche erscheint.
- **Ein Diagramm herunterladen:** Verwenden Sie im Diagramm-Tab der Repository-Analyse den **Herunterladen**-Button, um die Draw.io-XML-Datei zu speichern. Klicken Sie auf **In Draw.io öffnen**, um es direkt in der Draw.io-Desktop-App zu öffnen.
- **Multi-Repository-Analyse:** Markieren Sie in der Repository-Analyse mehrere Repository-Karten und klicken Sie auf **Multi-Analyse starten**, um eine kombinierte repository-übergreifende Analyse zu erstellen.
- **Nur-Stichwort- vs. vollständige Analyse:** Die **Suche im Repo**-Leiste liefert sofortige lokale Ergebnisse. **Code analysieren** sendet Dateien an die KI für eine umfangreichere Analyse auf Architekturebene.
- **Code-Qualitäts-MR:** Verwenden Sie in der Code-Qualitätsanalyse die Funktion **Merge Request erstellen** im Tab Dateiänderungen, um alle KI-Refactoring-Vorschläge als einzelnen GitLab-MR zu übertragen — kein manuelles Kopieren erforderlich.
- **Sprint-Review-PPTX:** Laden Sie beim Start einer Sprint-Review-Analyse eine markenkonforme PowerPoint-Vorlage hoch, um den KI-Bericht in einem präsentationsfertigen Format zu erhalten.
- **SQL-Testdaten:** Nutzen Sie **Testdaten generieren** auf der SQL-Analyse-Seite, um fertige Fixtures für eine beliebige Abfrage zu erstellen — kein manuelles Schreiben von Testdaten erforderlich.
- **Wissensdatenbank sichern:** Verwenden Sie **Alle exportieren**, um alle Bases als verschlüsseltes `.slbundle` zu sichern. Prüfen Sie das Passwort, bevor Sie lokale Quellen löschen.
- **Wissensindex nach Modellwechsel:** Lesen Sie die Quellen mit dem neuen Embedding-Modell erneut ein und führen Sie anschließend **Alle Wissensdatenbanken reindexieren** aus, damit Vektordimension und Lucene-Index zusammenpassen.
- **Konfigurationsbackup:** Verwenden Sie **Exportieren** auf der Konfigurationsseite, um ein verschlüsseltes Backup zu speichern. Das Backup kann auf einem anderen Gerät oder nach einer Neuinstallation von StackLens importiert werden. Die Wissensdatenbanken werden separat über `.slbundle` gesichert.
- **Sprachumschaltung:** Ändern Sie die UI-Sprache (English / Deutsch) im KI-Konfigurationsbereich oben auf der Konfigurationsseite — die Änderung wirkt sofort ohne Neustart.
- **JWT-Inspektion:** Fügen Sie einen beliebigen JWT-Token auf der JWT-Analyse-Seite ein, um sofort dessen Claims, Status und Ablauf zu sehen — keine externen Tools erforderlich.
- **Sprint-Vorbereitung:** Führen Sie die Sprint-Vorbereitung vor jeder Sprint-Planungssitzung auf Ihrem Jira-Epic aus, damit die KI Tickets mit fehlenden Akzeptanzkriterien oder unklaren Vorbedingungen kennzeichnet.
- **In Jira posten:** Nach einer Sprint-Vorbereitungsanalyse klicken Sie auf **Als Kommentar posten** neben einem Ticket-Ergebnis, um den KI-Verbesserungsvorschlag direkt als Jira-Kommentar zu veröffentlichen, ohne StackLens zu verlassen.
- **Ticket-Erstellung aus Dokumenten:** Hängen Sie ein PDF-Anforderungsdokument oder einen Screenshot an — StackLens extrahiert die relevanten Informationen und erstellt daraus automatisch ein strukturiertes Jira-Ticket.
- **Sicherheits-Fix MR:** Ein Klick auf **Fix generieren** bei einem Sicherheitsanalyse-Befund öffnet den resultierenden GitLab-MR oder GitHub-PR im Standard-Browser Ihres Systems zur sofortigen Überprüfung.
- **Agenten-Pipeline aus Text:** Fügen Sie eine Feature-Beschreibung, User Story oder Aufgabe in den Freitext-Modus der Agenten-Pipeline ein, um PHP-Code, Tests und Dokumentation ohne Jira-Ticket zu erhalten.
- **Agenten-Pipeline aus Jira:** Nutzen Sie den Jira-Modus, damit StackLens das Ticket lädt, die vollständige Analyse durchführt, Code generiert und automatisch einen GitLab Merge Request erstellt.
- **Sicherheits-Iterationen:** Erhöhen Sie die Iterationsanzahl in der Sicherheitsanalyse, um tiefere Schwachstellen zu finden — 5 Iterationen liefern einen gründlichen Pen-Test für kritische Systeme.
- **KeePassX-Import:** Klicken Sie auf KeePassX importieren auf der Konfigurationsseite, um alle Verbindungen aus einem KeePass-XML-Export in einem Schritt zu laden.
- **Bild-Extraktion Shortcut:** Ziehen Sie einen Screenshot oder ein Whiteboard-Foto auf die Bild-Extraktions-Seite und verwenden Sie *KI zuerst* — die KI verarbeitet unsaubere Eingaben und fällt bei Bedarf automatisch auf Tesseract zurück.
- **Nur-Tesseract-Modus:** Wenn Sie nur saubere OCR für gedruckten Text benötigen und die KI überspringen wollen, schalten Sie die OCR-Engine auf *Nur Tesseract* — sie ist vollständig offline und verbraucht kein API-Kontingent.
- **Docker-Test Auto-Start:** Wenn Sie StackLens gegen die lokalen Mocks entwickeln, aktivieren Sie die Entwickleroption *Demo-Container automatisch starten*, damit das `demo`-Profil bei jedem Start hochgefahren wird.
- **StackLens beenden:** Klicken Sie auf den **Beenden**-Button am unteren Ende der linken Seitenleiste und bestätigen Sie den Dialog.
