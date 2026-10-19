## pipeline.summaryFormatHint
(2–4 sentences overall assessment of the repository's Docker Compose stacks and CI/CD pipelines in English)

## pipeline._section.analyse
Pipeline Analysis

## pipeline.analysisPrompt
You are a senior platform / DevOps engineer specialising in Docker Compose stacks, container orchestration and CI/CD pipelines (GitLab CI, Jenkins, GitHub Actions, CircleCI, Bitbucket Pipelines, Travis CI, Azure Pipelines, Cloud Build, Buildkite).

**IMPORTANT — Self-contained input:** The complete project tree and the contents of every Docker Compose / Dockerfile / pipeline file found in this repository are already inlined below. Do NOT try to access the filesystem, list directories, read files or run shell commands. Filesystem and shell tools are disabled; any such attempt will fail. Treat `{projectPath}` as a label only.

Analyse the following repository **{projectName}** ({projectPath}) for issues, smells and optimisation opportunities in its Docker Compose stacks and CI/CD pipelines. The repository has been pre-scanned; only the relevant files are inlined below.

## 1. Errors (HIGH severity)
Identify anything that will prevent the stack from starting or the pipeline from running:
- Wrong schema keys / wrong indentation / unknown anchors in docker-compose.yml
- Missing required keys (`services`, `image`/`build`, `version`)
- Circular dependencies between services (`depends_on` loops)
- Volume / network / secret references that are not declared
- CI/CD: jobs that reference undeclared stages, scripts, artifacts, caches, or variables
- CI/CD: shell scripts with unbalanced quotes, missing `set -e`, unsafe variable interpolation
- Hard-coded secrets (passwords, tokens, AWS access keys, private keys)

## 2. Warnings (MEDIUM severity)
- `:latest` image tags
- `privileged: true`, `network_mode: host`, `pid: "host"` without justification
- Containers without resource limits (`mem_limit`, `cpus`, `deploy.resources.limits`)
- Mounting the Docker socket into a container
- CI/CD: caching without a key, un-pinned actions (uses `@main`)
- Long-running pipelines without timeouts

## 3. Smells / Best practices (LOW severity)
- Repeating the same environment variables across many services
- Duplicated `volumes` / `networks` blocks that could be extracted into YAML anchors
- `restart: always` instead of a specific policy (`on-failure`, `unless-stopped`)
- CI/CD: monolithic pipelines that could be split into reusable includes / templates
- Missing healthchecks / readiness probes for long-running services

## 4. Optimisation suggestions
For each suggestion, describe:
- WHY it improves things (cost, speed, reliability, security)
- WHAT to change (file + key + replacement snippet)
- IMPACT (rough estimate: low / medium / high)

## 5. File descriptions (REQUIRED)
For EACH file inlined below, produce a per-file description that explains:
- what kind of file it is (`docker-compose`, `dockerfile`, `gitlab-ci`, `github-actions`, `jenkinsfile`, `circleci`, `azure-pipelines`, `bitbucket-pipelines`, `cloud-build`, `buildkite`, `travis`, `other`)
- which top-level keys / stages / jobs it defines
- for each non-trivial key=value pair: what the key means and what this specific value does
- the file's overall purpose in the stack / pipeline

## 6. Optional Mermaid diagram
Additionally create a Mermaid `graph LR` diagram of the most important services / jobs and their relationships (depends_on, needs, …). Maximum 15 nodes, no subgraph, no special characters in node IDs.

---
## Project Structure (compose / pipeline files only)
```
{projectTree}
```

## File Contents
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
    "file": "docker-compose.yml",
    "type": "COMPOSE_ERROR",
    "severity": "HIGH",
    "line": 12,
    "snippet": "image: myapp:latest",
    "description": "The image tag ':latest' makes upgrades non-reproducible.",
    "suggestion": "Pin the image to a specific version or digest, e.g. 'myapp:1.4.2' or 'myapp@sha256:...'.",
    "suggestedCode": "image: myapp:1.4.2"
  }
]
```

Possible values for 'type':
  COMPOSE_ERROR, COMPOSE_WARNING, COMPOSE_SMELL,
  CI_ERROR, CI_WARNING, CI_SMELL,
  BEST_PRACTICE, OPTIMIZATION, DOC_MISSING.
Possible values for 'severity': HIGH, MEDIUM, LOW.
The 'snippet' field is optional (max. 8 lines from the file at the location).
The 'suggestedCode' field is optional (only when a concrete code change helps).

FILE_DESCRIPTIONS:
```json
[
  {
    "path": "docker-compose.yml",
    "fileKind": "docker-compose",
    "description": "Top-level Compose file that defines the local development stack: a Postgres database, a Redis cache and the main application service.",
    "keys": [
      {"key": "version", "value": "3.9", "meaning": "Compose file format version. 3.x supports modern features such as 'depends_on' with health conditions and named volumes."},
      {"key": "services.app.image", "value": "myapp:1.4.2", "meaning": "Container image the 'app' service is started from. Pinning the tag makes builds reproducible."},
      {"key": "services.app.environment", "value": "DATABASE_URL=...", "meaning": "Environment variables injected into the 'app' container at runtime. Prefer a '.env' file or Docker secrets for real credentials."}
    ]
  }
]
```

Possible values for 'fileKind': docker-compose, dockerfile, gitlab-ci, github-actions, jenkinsfile,
circleci, azure-pipelines, bitbucket-pipelines, cloud-build, buildkite, travis, other.

OPTIMIZATIONS:
```json
[
  {
    "title": "Replace 'image: myapp:latest' with a pinned tag",
    "rationale": "Immutable image tags make rollbacks and audits reproducible; ':latest' changes silently on rebuild.",
    "patchSnippet": "image: myapp:1.4.2"
  }
]
```

{mermaidSection}
{userPromptSection}

## pipeline._section.mermaid
Mermaid Diagram

## pipeline.mermaidSection

Create a Mermaid architecture diagram of the most important services / jobs and their relationships (depends_on, needs, after, …).
Output as ```mermaid code block with `graph LR` syntax.
MANDATORY SYNTAX RULES:
- Node IDs: ONLY letters/digits/underscore. NO hyphens, dots, parentheses or slashes.
- Node labels with spaces or special characters MUST use double quotes: A["My Service"]
- NEVER use HTML entities inside labels (FORBIDDEN: &quot; &amp; &#x2F; &#39; &lt; &gt;).
  Mermaid renders entities but the resulting unescaped quotes break the parser mid-label.
  Use plain text only: A["Web Controllers"] — NOT A["Web &quot;Controllers&quot;"].
- Arrow syntax: ONLY --> (two hyphens + >) is valid.
- Edge labels: ALWAYS inside pipe characters: A -->|Label| B
- After the diagram type (graph LR) NO further tokens on the same line.
- NO subgraph blocks. NO end statements except to close a subgraph.
- Maximum 15 nodes.
- classDef MUST appear before class statements.
- Each classDef and each class statement MUST be on its own separate line.
- No blank lines within the diagram code.

## pipeline._section.userprompt
User prompt section

## pipeline.userPromptSection

## Additional user instructions
The user has provided the following additional instructions that MUST be honoured when answering, in addition to the structured output format above:
---
{userPrompt}
---
If the user's instructions conflict with the structured output format, follow the user's intent but still emit every required block (SUMMARY, FINDINGS, FILE_DESCRIPTIONS, OPTIMIZATIONS, optional Mermaid). Only when the user explicitly asks you to skip a section may you omit it.
