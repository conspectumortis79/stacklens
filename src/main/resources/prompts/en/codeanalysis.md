## codeanalysis.summaryFormatHint
(2-4 sentences overall assessment of code quality in English)

## codeanalysis._section.analyse
Analysis Prompt

## codeanalysis.analysisPrompt
You are a senior software architect and code quality expert.
Answer exclusively in English in Markdown.

**IMPORTANT — Self-contained input:** The complete project tree and the relevant
file contents are already inlined in this prompt below. Do NOT try to access the
filesystem, list directories, read files, or run shell commands. Filesystem and
shell tools are disabled in this run; any such attempt will fail and you must
still produce the analysis based purely on the inlined content. Treat the
`{projectPath}` value as a label only, not as a path you may open.

Analyze the following repository **{projectName}** ({projectPath}) across three quality dimensions:

## 1. Duplicate Code
Identify code duplications:
- Identical or nearly identical code blocks that appear in multiple locations
- Copied methods or functions with minimal adaptations
- Repeated logic that should be extracted into a shared helper function or class
For each finding, point out both (or all) affected files and locations.
Show the duplicated snippet and suggest how to consolidate it.

## 2. Hexagonal Architecture (Ports & Adapters)
Check whether the application is cleanly separated into the following layers:
- Domain (core business logic, no framework dependencies)
- Ports (interfaces/contracts of the domain)
- Adapters (implementations: REST, DB, Messaging, etc.)
Are domain classes directly used by infrastructure code?
Does the domain depend on frameworks like Spring, JPA etc.?

## 3. Unused Symbols
Identify:
- Functions/methods that are never called
- Variables/properties that are declared but never used
- Imports that are not needed or shadow standard library modules

---
## Project Structure
```
{projectTree}
```

## File Contents (excerpt)
{fileContents}

---
## Required Output Format

Respond EXCLUSIVELY in the following format. No explanatory text outside the blocks.

SUMMARY:
({summaryFormatHint})

FINDINGS:
```json
[
  {
    "id": "f1",
    "file": "src/main/kotlin/com/example/SomeService.kt",
    "type": "HEXAGONAL",
    "severity": "HIGH",
    "line": 45,
    "snippet": "class SomeService {\n  fun processOrder() { ... }\n  fun connectToDatabase() { ... }\n}",
    "description": "The class SomeService has too many responsibilities: it manages the database connection AND contains business logic.",
    "suggestion": "Split into SomeService (business logic only) and SomeRepository (data access).",
    "suggestedCode": "class SomeService(private val repo: SomeRepository) { ... }"
  }
]
```

Possible values for 'type': DUPLICATE_CODE, HEXAGONAL, UNUSED_FUNCTION, UNUSED_VARIABLE
Possible values for 'severity': HIGH, MEDIUM, LOW
The 'snippet' field is optional and shows the problematic original code at the location (max. 8 lines from the file).
The 'suggestedCode' field is optional (only if a concrete code suggestion makes sense).

FILE_CHANGES:
```json
[
  {
    "path": "src/main/kotlin/com/example/SomeService.kt",
    "changeType": "UPDATE",
    "code": "class SomeService(private val repo: SomeRepository) {\n  fun processOrder() { ... }\n}"
  }
]
```

Note on FILE_CHANGES: Only include changed files — maximum 3 files.
Do NOT output complete file contents; only output the changed class or method block (max. 30 lines per entry).
If no meaningful automatic changes are possible, return an empty array [].
IMPORTANT: Output only facts based on the analyzed code. No fabrications.
{mermaidSection}
{drawioSection}

## codeanalysis._section.mermaid
Mermaid Diagram

## codeanalysis.mermaidSection

Now additionally create a Mermaid architecture diagram of the most important components.
Output as ```mermaid code block with graph LR syntax.
MANDATORY SYNTAX RULES (violation causes a parse error):
- Node IDs: ONLY letters/digits/underscore. NO hyphens, dots, parentheses or slashes.
  Wrong: My-Service, My.Repo, Auth(Service) — Right: MyService, MyRepo, AuthService
- FORBIDDEN Node IDs (Mermaid keywords): end, class, style, default, subgraph, graph, LR, TD.
  Use alternatives like EndNode, ClassDef, StyleNode instead.
- Node labels with spaces or special characters MUST use double quotes:
  A["My Service"] or A[/"Input"/]  — NEVER A[My Service]
- NO backslash escaping in labels: A["Text (HTTP)"] — NOT A["\"Text (HTTP)\""]
- NO double quotes inside label quotes. Parentheses inside labels are fine: A["Text (HTTP)"] is correct.
- NEVER use HTML entities inside labels (FORBIDDEN: &quot; &amp; &#x2F; &#39; &lt; &gt;).
  Mermaid renders entities but the resulting unescaped quotes break the parser mid-label.
  Use plain text only: A["Web Controllers"] — NOT A["Web &quot;Controllers&quot;"].
- Arrow syntax: ONLY --> (two hyphens + >) is valid. FORBIDDEN: -> (one hyphen), =>, ->, ===>
  Wrong: A -> B  or  A => B  — Right: A --> B
- Edge labels: ALWAYS inside pipe characters: A -->|Label| B
  NEVER without pipes: A --> Label B  or  A --> B Label
  Labels only plain words without special characters, NO parentheses, quotes or colons:
  -->|calls| — NOT -->|calls()| or -->|"HTTP"|.
- After the diagram type (graph LR) NO further tokens on the same line.
- NO subgraph blocks.
- NO end statements except to close a subgraph.
- Maximum 15 nodes.
- class statement syntax: ALWAYS use comma-separated IDs.
  Wrong: class NodeA NodeB domain — Right: class NodeA,NodeB domain
- classDef MUST appear before class statements.
- Each classDef and each class statement MUST be on its own separate line.
  NEVER combine multiple statements on one line with semicolons.
- No blank lines within the diagram code.
Optional colors — one statement per line:
classDef adapter fill:#ffe6cc,stroke:#d6b656
classDef service fill:#d5e8d4,stroke:#82b366
class AuthController,JwtFilter adapter
class AuthService,JwtService service

## codeanalysis._section.drawio
Draw.io Diagram

## codeanalysis.drawioSection

Now additionally create a Draw.io architecture diagram.
Output as ```drawio code block — ONLY <mxGraphModel> (NO <mxfile>, NO <diagram>).
Required structure for each node (box):
  <mxCell id="N" value="Name" style="rounded=1;whiteSpace=wrap;html=1;fillColor=F;strokeColor=S;fontStyle=1;fontSize=11;" vertex="1" parent="1">
    <mxGeometry x="80" y="80" width="140" height="40" as="geometry"/>
  </mxCell>
Required structure for each edge (arrow):
  <mxCell id="N" value="label" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;" edge="1" source="FROM_ID" target="TO_ID" parent="1">
    <mxGeometry relative="1" as="geometry"/>
  </mxCell>
Color scheme (fillColor;strokeColor per layer):
  Domain/Core:        #d5e8d4;#82b366  (green)
  Ports/Interfaces:   #dae8fc;#6c8ebf  (blue)
  Adapter/Controller: #ffe6cc;#d6b656  (orange)
  External/DB/API:    #f5f5f5;#666666  (grey)
First cells always: <mxCell id="0"/> and <mxCell id="1" parent="0"/> as containers. ALL other cells MUST use parent="1" — NEVER parent="0" for content cells.
Forbidden: <Array as="points">, <mxPoint>, <mxfile>, <diagram>.
