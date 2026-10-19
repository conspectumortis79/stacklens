## codeqa.composeSelector
A monorepo was cloned. It contains multiple docker-compose files:
{listing}

The merge request "{title}" (branch: {sourceBranch}) changed these files:
{changedFiles}

Which docker-compose file is most relevant for testing these changes?
Respond with ONLY the number (e.g. "2"). No explanation.

## codeqa.testPlan
You are an expert DevOps/QA engineer analyzing a merge request. Your job is NOT
just to verify that the affected docker-compose services come up — you must also
find concrete ways to **exercise the actually changed code under real conditions**
(unit tests, integration tests, type-checks, contract checks, smoke calls against
new endpoints, etc.) so the merge request is validated against the live code, not
only against the service health.

## Merge Request
Branch: {sourceBranch} → {targetBranch}
Title: {title}

## Changed Files ({changedFilesCount} total)
{changedFiles}

## Compose File Location
{composeDirNote}

### docker-compose.yml
```yaml
{composeContent}
```

## Repository File Structure
```
{repoFileList}
```

## Detected Test Infrastructure
{testInfraInfo}

## Detected Project Type(s)
{projectTypes}

## Task
You have THREE jobs — in this order:

### Job 1: Affected services
Identify which docker-compose service names are DIRECTLY affected by the changed
files. Consider build contexts, volumes, shared config, and service dependencies.

### Job 2: Exercise the CHANGED code (this is the new, critical part)
For each meaningful change in the MR, find at least one concrete way to **execute
the changed code** under real conditions inside the running docker-compose stack.
Do NOT stop at "service is up + curl returns 200" — that proves nothing about the
MR.

Pick commands based on the detected project types — examples:

- **node / typescript**: run the project's actual test runner targeted at the
  changed files. Example:
  `docker-compose exec app sh -c 'cd /app && npx jest --runInBand src/changed/file.test.ts'`
  or `npx vitest run path/to/changed.test.ts`,
  or `npm test -- --testPathPattern=src/changed` if the project uses mocha/jest
  with a script alias. Add `|| true` if you only want to log a failure.
- **python**: `docker-compose exec api sh -c 'cd /srv && pytest -xvs tests/test_changed.py'`
  or, if only specific tests are affected:
  `pytest tests/test_x.py::TestClass::test_method -xvs`.
  Also add import / syntax sanity checks: `python -c "import changed_module"`.
- **jvm (kotlin/java)**: `docker-compose exec app sh -c './gradlew test --tests *ChangedClass*'`
  (works for gradle) or `./mvnw -pl <module> test -Dtest=ChangedClassTest`.
  For a fast smoke check without running the full suite:
  `./gradlew compileKotlin compileTestKotlin --no-daemon`.
- **go**: `docker-compose exec api sh -c 'cd /app && go test ./path/to/changed/...'`.
- **rust**: `docker-compose exec api sh -c 'cd /app && cargo test --test <name>'`.
- **php**: `docker-compose exec app sh -c 'cd /var/www && ./vendor/bin/phpunit tests/ChangedTest.php'`.
- **ruby**: `docker-compose exec app sh -c 'cd /app && bundle exec rspec spec/changed_spec.rb'`.
- **no project runner detected**: at minimum run a smoke check on the changed
  files (e.g. `python -c "import x"`, `node -e "require('./changed')"`,
  `php -l changed.php`) inside the affected container, plus the
  HTTP/process health checks described in Job 3.

The test commands MUST be a single shell string, MUST run inside the relevant
docker-compose service via `docker-compose exec <service> …`, MUST be executable
without any extra setup (no manual install, no interactive prompts), and MUST
target files that actually exist in the changed-files list when possible. If you
cannot derive a real test command, still add the closest smoke check — do NOT
return an empty `testCommands` list when services are affected.

### Job 3: Lightweight health checks (fallback only)
For each affected service, add at most ONE basic health probe
(`curl -sf http://localhost:<port>/health`, `pgrep <proc>`). Do NOT bloat the list
with health checks for every port — they are not a substitute for real tests.

## Rules for ALL commands
- ALWAYS run via `docker-compose exec <service> <cmd>` — never bare `curl`.
- NEVER touch external bind-mounts or production volumes (`/mnt/...`, `/data/...`,
  NFS shares). Only test what is built into the image.
- Each command is a single shell string; shell operators `||`, `&&`, `|` are OK.
  Example: `docker-compose exec app sh -c 'curl -sf http://localhost:8080/health || true'`.
- For PHP syntax checks use `docker-compose exec <svc> php -l <file>` (not `sh -c php -l …`).
- Commands run from the compose file's directory.
- Order matters: list unit/integration test commands FIRST, health checks LAST.

## Startup wait time
Estimate a startup wait time (seconds) after `docker-compose up` before running
tests. Tests that hit a not-yet-ready service need more time; pure
unit-tests inside the container need less.

## Response Format
Return ONLY valid JSON — no prose, no markdown fences:
{
  "affectedServices": ["service-name-1"],
  "projectTypes": ["node"],
  "testStrategy": "unit-tests",
  "testTargets": ["src/changed/file.ts", "tests/test_x.py"],
  "testCommands": [
    "docker-compose exec app sh -c 'cd /app && npx jest src/changed/file.test.ts --runInBand'",
    "docker-compose exec app curl -sf http://localhost:8080/health"
  ],
  "waitSeconds": 25,
  "reasoning": "Changed src/foo.ts: runs jest targeted at its co-located test, then a health probe."
}

Field semantics:
- `affectedServices`: docker-compose service names directly affected by the MR.
- `projectTypes`: subset of the detected types relevant to the MR (e.g. ["node","python"]); empty if none.
- `testStrategy`: one of "unit-tests" | "integration-tests" | "smoke-tests" | "health-checks" | "mixed".
- `testTargets`: file paths (relative to repo root) the test commands actually exercise.
- `testCommands`: ordered list — real tests first, health checks last.
- `waitSeconds`: see above.
- `reasoning`: one short sentence (max 200 chars) explaining the plan; used in logs.

If NO services are clearly affected, return:
{"affectedServices":[],"projectTypes":[],"testStrategy":"none","testTargets":[],
 "testCommands":[],"waitSeconds":15,"reasoning":"No docker-compose services affected."}

## codeqa.summarize
Summarize the following integration test results for merge request "{title}" as concise Markdown.

Services tested: {services}
Tests passed: {passed} / {total}

Test results:
{outputSummary}

Write a summary with:
- Overall status line (✅ all passed / ⚠️ partial / ❌ failed)
- A short bullet list of each test command and its result
- Any notable errors or warnings (1-2 sentences max)
- A brief recommendation (1 sentence)

Keep it short (max 25 lines). {langInstruction}
