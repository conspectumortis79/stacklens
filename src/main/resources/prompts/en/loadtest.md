## loadtest._section.analyse
Analysis Prompt

## loadtest.analysisPrompt
You are an experienced performance engineer.
Respond exclusively in English in Markdown.
Identify the tech stack from the file contents and analyse the following GitLab project **{projectName}** ({projectPath}) for load-test opportunities and performance bottlenecks.

Focus especially on:
- **Slow database queries**: missing indices, N+1 queries, SELECT *, no pagination
- **Synchronous process chains**: HTTP calls in the request path, no queue processing
- **Missing caches**: repeated DB lookups without caching, no HTTP cache headers
- **Application bottlenecks**: endpoints with high processing time, blocking I/O operations, missing connection pools
- **Resource locking**: file locks, overly long DB transactions, mutex bottlenecks
- **Missing async patterns**: emails, reports or exports generated synchronously

## Project structure
```
{projectTree}
```

## File contents (excerpt)
{fileContents}

---
## Required output format

Respond ONLY in the following format with no explanatory text outside the blocks.

SUMMARY:
(2-4 sentences overall assessment of performance risks in English)

FINDINGS:
```json
[
  {
    "id": "lt1",
    "file": "app/Http/Controllers/OrderController.php",
    "type": "SLOW_QUERY",
    "severity": "HIGH",
    "endpoint": "GET /orders",
    "description": "The index() method loads all orders without pagination (SELECT * FROM orders). With >10,000 entries PHP-FPM crashes with memory limit or timeout.",
    "recommendation": "Introduce LIMIT/OFFSET or cursor pagination. Add index on orders.created_at.",
    "k6Hint": "Test endpoint with increasing data volume: 10, 100, 1000 concurrent users."
  }
]
```

If no well-supported findings can be identified, return `[]` instead of inventing risks.

## loadtest._section.env
Environment Detection Prompt

## loadtest.envDetectPrompt
Analyse the following Docker, configuration and environment files from the project **{projectName}** to detect all distinct deployment environments.

## Docker / configuration files
{dockerFiles}

## Route / configuration files
{routeFiles}

Detect all distinct deployment environments (e.g. Local Dev, Staging, Production) from docker-compose files,
.env files, Kubernetes configs, Spring profiles, nginx/apache vhosts, or any other config files that define hosts and ports.

Rules:
- If only one environment can be detected, return a single-element array.
- If no environment can be determined at all, return an empty array [].
- "name" must be a short, human-readable label (e.g. "Local Dev", "Staging", "Production").
- "host" is the server hostname or IP (e.g. "localhost", "staging.example.com").
- "port" is the numeric port as a string (e.g. "8080", "443"). Use "80" for HTTP and "443" for HTTPS if not explicit.
- "scheme" is either "http" or "https".

Respond ONLY with a raw JSON array (no code fences, no explanatory text, nothing else):
[{"name":"Local Dev","host":"localhost","port":"8080","scheme":"http"}]

## loadtest._section.k6
k6 Script Prompt

## loadtest.k6Prompt
You are a k6 performance-testing expert for the project **{projectName}**.
Generate a complete, immediately runnable k6 v0.50+ script.
Analyse ALL provided files to find every API endpoint: controllers, routes, decorators,
nginx/kubernetes configs, Spring mappings, express routers, etc.

## BASE_URL — derive it yourself, do NOT leave the placeholder

`BASE_URL` MUST be derived from the project's actual HTTP port, NOT the template default.
Steps, in this exact order:
1. Scan the `Docker / infrastructure files` section for a `docker-compose.yml`,
   `docker-compose.yaml`, or `compose.yaml`. Identify the **primary service**
   (the one whose build context / image matches the project you are testing —
   `services: <name>: { build: { context: . } }` or the only service without
   a `profiles: [...]` restriction in a single-service project). Use the
   **HOST-side** port of its `ports:` mapping (left of `host:container`,
   the first number in `1234:5678`).
2. If no docker-compose is present, scan `.env`, `application.yml/properties`,
   `nginx.conf`, `apache vhosts`, Kubernetes manifests, Spring profile
   configs, or anything else that names a port for the HTTP listener.
3. If no port can be derived at all, emit a single-element `[{"name":"Local
   Dev","host":"localhost","port":"<best-guess>","scheme":"http"}]`
   environment and use that port.

**Do NOT** leave the `http://invalid:0` placeholder. **Do NOT** keep the
template's `http://localhost:8085` — that port belongs to the demo
`fake-backend` and will return 404 for this project's endpoints.

## Docker / infrastructure files
{dockerFiles}

## Route / application code files
{routeFiles}

## Identified bottlenecks
```json
{findingsJson}
```

## EXACT REQUIRED SCRIPT SKELETON — follow without exceptions

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

// StackLens patches BASE_URL at runtime — replace the placeholder with the
// project's actual port (see BASE_URL derivation rules above).
const BASE_URL = __ENV.BASE_URL || 'http://invalid:0';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '60s', target: 20 },
    { duration: '10s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed:   ['rate<0.05'],
    // One pair per endpoint — {name:…} MUST equal tags.name exactly, character-for-character
    'http_req_duration{name:GET /api/example}': ['p(95)<2000'],
    'http_req_failed{name:GET /api/example}':   ['rate<0.1'],
  },
};

export default function () {
  const token = __ENV.JWT_TOKEN || '';
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token,
  };

  // GET /api/example — load-test example endpoint
  const res1 = http.get(BASE_URL + '/api/example', {
    headers,
    tags: { name: 'GET /api/example' },
  });
  check(res1, {
    'GET /api/example → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /api/example → 5xx': (r) => r.status >= 500,
  });

  sleep(1);
}
```

## RULES — enforced by the StackLens dashboard parser

### stages
- `duration` MUST be a quoted string: `'30s'`, `'2m'`, `'1h'` — NEVER a bare number.
- `target` MUST be an unquoted integer. Choose realistic VUs (10–100 for web APIs).

### thresholds — critical for per-endpoint stats table
- For EVERY endpoint you call, add BOTH lines inside `options.thresholds`:
  `'http_req_duration{name:METHOD /path}': ['p(95)<2000'],`
  `'http_req_failed{name:METHOD /path}':   ['rate<0.1'],`
- The `{name:…}` value MUST be the identical string as `tags.name` in each http call.
- Without these entries k6 emits no per-endpoint metrics and the StackLens table stays empty.

### HTTP calls — tagging (critical for per-endpoint stats)
- Every `http.get / http.post / http.put / http.del / http.patch` call MUST use:
  `http.METHOD(BASE_URL + '/path', { headers, tags: { name: 'METHOD /path' } })`
- `METHOD` inside `tags.name` is UPPERCASE (GET, POST, PUT, DELETE, PATCH).
- Path starts with `/` — never relative, no query strings, no trailing slash.
- `tags.name` MUST match the threshold `{name:…}` key character-for-character, case-sensitively.

### check() — status-code naming (required for StackLens status column)
- After EVERY http call add a check with this exact naming pattern:
  ```js
  check(res, {
    'METHOD /path → 2xx': (r) => r.status >= 200 && r.status < 300,
    'METHOD /path → 5xx': (r) => r.status >= 500,
  });
  ```
- Use the actual METHOD and /path (e.g. `'GET /api/users → 2xx'`).
- Use `→` (Unicode U+2192) as separator — StackLens parses these names for the status column.

### Coverage
- Cover every distinct API path in route/config files, especially bottleneck endpoints.
- Use the correct HTTP method; include a minimal realistic JSON body for POST/PUT/PATCH.
- Add `sleep(1)` between request groups.

### Destructive endpoints — must NOT appear in `default function()`
A destructive endpoint mutates state beyond a single record, has side-effects
that affect subsequent iterations, or wipes persistent data. These endpoints
are **forbidden** inside `export default function ()` because k6 calls the
iteration function hundreds to thousands of times per test run (e.g. 20 VUs
× 100 s stages ≈ 2000 iterations), and a destructive call per iteration will:
- destroy the data the other endpoints in the iteration are supposed to read,
- cause race conditions under parallel VUs (`StaleObjectStateException`,
  duplicate-key violations, lost updates),
- pollute the load-test metrics with destruction overhead instead of
  realistic user traffic.

Place destructive endpoints in **exactly one** of these locations instead:
- `export function setup()` — runs **once** before all VUs start. Use for
  clean-state tests (reset the DB to a known fixture before measuring).
- `export function teardown()` — runs **once** after all VUs finish. Use for
  cleanup so the next dev session starts clean.
- A **dedicated scenario** with its own `vus` and `stages` — only when the
  explicit goal is to stress-test the destructive endpoint itself.

Concrete destructive endpoints that MUST stay out of `default function()`:
- `POST /api/admin/factory-reset-db` — wipes the entire database
- `POST /api/knowledge/reset` — wipes all knowledge bases, sources, documents, chunks, vectors
- `POST /api/knowledge/reindex-all` — rebuilds the index (mutates state observed by other endpoints; belongs in `setup()` or its own scenario)
- Any `DELETE /api/.../{id}` that targets more than one record in a single call
- Any endpoint whose URL or handler name contains: `purge`, `wipe`, `clear-all`, `reset-all`, `factory-reset`, `truncate`, `drop`

When the user is benchmarking a destructive endpoint, emit the iteration as a
**separate `scenarios:` entry** with its own `exec` function (e.g. `exec: 'destructiveBench'`)
and its own `vus`/`stages` block. Do NOT inline it in the main scenario.

## Scenarios — k6 v0.50+ option reference (StackLens targets `k6 v2.x`)

Per-scenario keys StackLens accepts today. Do NOT add keys that are not in this
list — k6 v2.2.x returns `json: unknown field "<key>"` and aborts with exit
code 104 (the script then never produces any stdout/stderr, making the failure
silent in the dashboard).

```
{
  executor: 'ramping-vus' | 'constant-vus' | 'constant-arrival-rate' | ...   (REQUIRED)
  exec: '<exported function name>',                                          (optional)
  startVUs: <int>,                                                           (ramping-vus only)
  stages: [ { duration: '30s', target: 20 }, ... ],                          (ramping-vus only)
  vus: <int>,                                                               (constant-vus only)
  duration: '30s',                                                          (constant-vus only)
  startTime: '120s',                                                        (cross-scenario start delay)
  gracefulStop: '30s',                                                      (per-scenario teardown window)
  tags: { ... },                                                            (per-scenario metric tags)
  env: { ... },                                                             (per-scenario env vars)
  exec: '<function name>',                                                  (function to run per iteration)
}
```

`gracefulRampDown` and `gracefulStop` are **TOP-LEVEL** `options` keys only
(do NOT place them inside an individual scenario). The exception is
`gracefulStop`, which may be set per-scenario in k6 ≥ v1.0. **Never** put
`gracefulRampDown` on a scenario — it was removed in k6 v2.0 and aborts
the run with exit 104.

```
export const options = {
  // Top-level — applies to the whole test:
  gracefulRampDown: '30s',
  gracefulStop: '30s',
  scenarios: {
    main: { executor: 'ramping-vus', stages: [...], exec: 'main' },
  },
};
```

## Self-check (silent — apply before outputting)
- [ ] All `duration` values are quoted strings, all `target` values are bare integers
- [ ] Every endpoint has BOTH `http_req_duration` and `http_req_failed` threshold entries
- [ ] Every threshold `{name:…}` matches the corresponding `tags.name` exactly
- [ ] Every `http.*` call has `tags: { name: 'METHOD /path' }` with uppercase METHOD
- [ ] Every `http.*` call is followed by `check()` with `→ 2xx` and `→ 5xx` entries
- [ ] `options` block is syntactically valid JavaScript for **k6 v2.x** (no
  per-scenario `gracefulRampDown` — that key is top-level only)
- [ ] No hard-coded credentials; all paths start with `/`
- [ ] Only endpoints that can be derived from the provided files are used
- [ ] No destructive endpoint (factory-reset-db, knowledge/reset, reindex-all, bulk-delete, purge, wipe, truncate, drop) is called inside `export default function ()` — destructive endpoints live in `setup()`, `teardown()`, or a dedicated `scenarios` entry

Output ONLY the k6 script in a ```javascript code block. No other text.
