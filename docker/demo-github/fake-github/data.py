"""
StackLens Demo GitHub Mock — dataset.
Mirrors the demo-org used in the fake-gitlab service so that
code-analysis, MR-review, and search features have correlated data.
"""

OWNER = "demo-org"

REPOS = [
    {
        "id": 1,
        "name": "backend-api",
        "full_name": "demo-org/backend-api",
        "description": "Core backend REST API service (Kotlin + Spring Boot)",
        "private": False,
        "default_branch": "main",
        "language": "Kotlin",
        "html_url": "http://localhost:8082/demo-org/backend-api",
        "clone_url": "http://localhost:8082/demo-org/backend-api.git",
        "stargazers_count": 42,
        "forks_count": 7,
        "open_issues_count": 5,
        "topics": ["kotlin", "spring-boot", "rest-api", "microservices"],
        "owner": {"login": "demo-org", "type": "Organization"},
    },
    {
        "id": 2,
        "name": "frontend-app",
        "full_name": "demo-org/frontend-app",
        "description": "React TypeScript frontend application",
        "private": False,
        "default_branch": "main",
        "language": "TypeScript",
        "html_url": "http://localhost:8082/demo-org/frontend-app",
        "clone_url": "http://localhost:8082/demo-org/frontend-app.git",
        "stargazers_count": 28,
        "forks_count": 3,
        "open_issues_count": 8,
        "topics": ["typescript", "react", "frontend"],
        "owner": {"login": "demo-org", "type": "Organization"},
    },
    {
        "id": 3,
        "name": "auth-service",
        "full_name": "demo-org/auth-service",
        "description": "JWT authentication and authorisation microservice",
        "private": False,
        "default_branch": "main",
        "language": "Kotlin",
        "html_url": "http://localhost:8082/demo-org/auth-service",
        "clone_url": "http://localhost:8082/demo-org/auth-service.git",
        "stargazers_count": 15,
        "forks_count": 2,
        "open_issues_count": 3,
        "topics": ["kotlin", "jwt", "authentication", "security"],
        "owner": {"login": "demo-org", "type": "Organization"},
    },
    {
        "id": 4,
        "name": "devops-infra",
        "full_name": "demo-org/devops-infra",
        "description": "Terraform IaC + Helm charts + Kubernetes manifests",
        "private": False,
        "default_branch": "main",
        "language": "HCL",
        "html_url": "http://localhost:8082/demo-org/devops-infra",
        "clone_url": "http://localhost:8082/demo-org/devops-infra.git",
        "stargazers_count": 11,
        "forks_count": 1,
        "open_issues_count": 2,
        "topics": ["terraform", "kubernetes", "helm", "infrastructure"],
        "owner": {"login": "demo-org", "type": "Organization"},
    },
]

# ── Pull Requests ──────────────────────────────────────────────────────────────

PULL_REQUESTS = {
    "backend-api": [
        {
            "number": 42,
            "title": "feat: add payment-service REST client with retry logic",
            "state": "open",
            "body": "Implements a resilient HTTP client for the payment microservice. "
                    "Uses exponential back-off with jitter. Resolves BACKEND-5.",
            "user": {"login": "alice"},
            "head": {"ref": "feature/payment-client", "sha": "abc1234def5678"},
            "base": {"ref": "main", "sha": "000feedbeef000"},
            "html_url": "http://localhost:8082/demo-org/backend-api/pull/42",
            "created_at": "2026-05-20T09:00:00Z",
            "updated_at": "2026-05-24T14:30:00Z",
            "draft": False,
            "labels": [{"name": "feature"}, {"name": "ready-for-review"}],
        },
        {
            "number": 41,
            "title": "fix: correct null-pointer in UserRepository.findByEmail()",
            "state": "open",
            "body": "Fixes NPE when email contains trailing whitespace. Resolves BACKEND-7.",
            "user": {"login": "bob"},
            "head": {"ref": "fix/user-repo-npe", "sha": "def5678abc1234"},
            "base": {"ref": "main", "sha": "000feedbeef000"},
            "html_url": "http://localhost:8082/demo-org/backend-api/pull/41",
            "created_at": "2026-05-22T11:15:00Z",
            "updated_at": "2026-05-23T16:00:00Z",
            "draft": False,
            "labels": [{"name": "bug"}],
        },
        {
            "number": 38,
            "title": "chore: upgrade Spring Boot 3.4 → 3.5",
            "state": "closed",
            "body": "Upgrades all Spring Boot managed dependencies to 3.5.x.",
            "user": {"login": "alice"},
            "head": {"ref": "chore/spring-boot-3-5", "sha": "aaaaabbbbccccc"},
            "base": {"ref": "main", "sha": "000feedbeef000"},
            "html_url": "http://localhost:8082/demo-org/backend-api/pull/38",
            "created_at": "2026-05-10T08:00:00Z",
            "updated_at": "2026-05-12T10:00:00Z",
            "draft": False,
            "labels": [{"name": "chore"}],
            "merged_at": "2026-05-12T10:00:00Z",
        },
    ],
    "frontend-app": [
        {
            "number": 17,
            "title": "feat: dark mode toggle for dashboard",
            "state": "open",
            "body": "Adds a persistent dark/light mode switch to the global header.",
            "user": {"login": "carol"},
            "head": {"ref": "feature/dark-mode", "sha": "f1f2f3f4f5f6f7"},
            "base": {"ref": "main", "sha": "000feedbeef000"},
            "html_url": "http://localhost:8082/demo-org/frontend-app/pull/17",
            "created_at": "2026-05-21T13:00:00Z",
            "updated_at": "2026-05-24T09:00:00Z",
            "draft": False,
            "labels": [{"name": "feature"}],
        },
    ],
    "auth-service": [
        {
            "number": 9,
            "title": "sec: rotate JWT signing key and enforce PKCE for OAuth2 flow",
            "state": "open",
            "body": "Security hardening: rotates the static HS256 signing key to RS256 "
                    "and adds PKCE (RFC 7636) to all OAuth2 authorisation requests.",
            "user": {"login": "bob"},
            "head": {"ref": "sec/jwt-rs256-pkce", "sha": "sec1sec2sec3sec"},
            "base": {"ref": "main", "sha": "000feedbeef000"},
            "html_url": "http://localhost:8082/demo-org/auth-service/pull/9",
            "created_at": "2026-05-23T10:00:00Z",
            "updated_at": "2026-05-24T17:00:00Z",
            "draft": False,
            "labels": [{"name": "security"}, {"name": "ready-for-review"}],
        },
    ],
    "devops-infra": [],
}

# ── PR Files (diff stubs) ─────────────────────────────────────────────────────

PR_FILES = {
    ("backend-api", 42): [
        {
            "filename": "src/main/kotlin/com/demo/payment/PaymentClient.kt",
            "status": "added",
            "additions": 120,
            "deletions": 0,
            "patch": "@@ -0,0 +1,120 @@\n+package com.demo.payment\n+\n+import org.springframework.stereotype.Component\n+import org.springframework.web.reactive.function.client.WebClient\n+\n+@Component\n+class PaymentClient(private val webClient: WebClient) {\n+    fun charge(amount: Long, currency: String): PaymentResult { ... }\n+}",
        },
        {
            "filename": "src/test/kotlin/com/demo/payment/PaymentClientTest.kt",
            "status": "added",
            "additions": 65,
            "deletions": 0,
            "patch": "@@ -0,0 +1,65 @@\n+package com.demo.payment\n+\n+import org.junit.jupiter.api.Test\n+class PaymentClientTest { ... }",
        },
    ],
    ("backend-api", 41): [
        {
            "filename": "src/main/kotlin/com/demo/user/UserRepository.kt",
            "status": "modified",
            "additions": 5,
            "deletions": 2,
            "patch": "@@ -42,7 +42,10 @@\n-    fun findByEmail(email: String): User?\n+    fun findByEmail(email: String): User? {\n+        val trimmed = email.trim()\n+        if (trimmed.isEmpty()) return null\n+        return query(trimmed)\n+    }",
        },
    ],
    ("auth-service", 9): [
        {
            "filename": "src/main/kotlin/com/demo/auth/JwtTokenProvider.kt",
            "status": "modified",
            "additions": 40,
            "deletions": 15,
            "patch": "@@ -10,15 +10,40 @@\n-private val signingKey = \"static-secret-key\"\n+// Use RS256 key pair loaded from environment\n+private val keyPair = loadKeyPairFromEnv()",
        },
        {
            "filename": "src/main/kotlin/com/demo/auth/OAuth2Controller.kt",
            "status": "modified",
            "additions": 25,
            "deletions": 8,
            "patch": "@@ -50,8 +50,25 @@\n+// PKCE: generate code_verifier + code_challenge\n+val verifier = generateCodeVerifier()\n+val challenge = sha256(verifier)",
        },
    ],
}

# ── Repo file trees (for content endpoint) ────────────────────────────────────

REPO_FILES = {
    "backend-api": {
        "README.md": "# backend-api\n\nCore backend REST API built with Kotlin and Spring Boot 3.\n\n## Architecture\n\nHexagonal (ports & adapters) architecture.\nDomain layer is framework-free; adapters in `adapter/`.\n\n## Running locally\n\n```bash\n./gradlew bootRun\n```\n",
        "build.gradle.kts": 'plugins {\n    kotlin("jvm") version "2.1.0"\n    kotlin("plugin.spring") version "2.1.0"\n    id("org.springframework.boot") version "3.5.0"\n    id("io.spring.dependency-management") version "1.1.7"\n}\n\ndependencies {\n    implementation("org.springframework.boot:spring-boot-starter-web")\n    implementation("org.springframework.boot:spring-boot-starter-data-jpa")\n    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")\n    runtimeOnly("org.postgresql:postgresql")\n    testImplementation("org.springframework.boot:spring-boot-starter-test")\n}\n',
        "src/main/kotlin/com/demo/BackendApiApplication.kt": "package com.demo\n\nimport org.springframework.boot.autoconfigure.SpringBootApplication\nimport org.springframework.boot.runApplication\n\n@SpringBootApplication\nclass BackendApiApplication\n\nfun main(args: Array<String>) {\n    runApplication<BackendApiApplication>(*args)\n}\n",
        "src/main/kotlin/com/demo/user/UserRepository.kt": "package com.demo.user\n\nimport org.springframework.data.jpa.repository.JpaRepository\nimport org.springframework.stereotype.Repository\n\n@Repository\ninterface UserRepository : JpaRepository<User, Long> {\n    fun findByEmail(email: String): User?\n    fun findByUsername(username: String): User?\n    fun existsByEmail(email: String): Boolean\n}\n",
        "src/main/kotlin/com/demo/payment/PaymentController.kt": "package com.demo.payment\n\nimport org.springframework.web.bind.annotation.*\n\n@RestController\n@RequestMapping(\"/api/payments\")\nclass PaymentController(private val paymentService: PaymentService) {\n\n    @PostMapping\n    fun charge(@RequestBody req: PaymentRequest): PaymentResponse {\n        return paymentService.process(req)\n    }\n\n    @GetMapping(\"/{id}\")\n    fun getPayment(@PathVariable id: Long): PaymentResponse {\n        return paymentService.find(id)\n    }\n}\n",
    },
    "frontend-app": {
        "README.md": "# frontend-app\n\nReact TypeScript frontend for the demo platform.\n\n## Tech Stack\n\n- React 18, TypeScript, Vite\n- TailwindCSS for styling\n- React Query for data fetching\n\n## Development\n\n```bash\nnpm install\nnpm run dev\n```\n",
        "package.json": '{\n  "name": "frontend-app",\n  "version": "1.0.0",\n  "scripts": {\n    "dev": "vite",\n    "build": "tsc && vite build",\n    "test": "vitest"\n  },\n  "dependencies": {\n    "react": "^18.3.0",\n    "react-dom": "^18.3.0",\n    "@tanstack/react-query": "^5.0.0"\n  },\n  "devDependencies": {\n    "typescript": "^5.4.0",\n    "vite": "^5.2.0"\n  }\n}\n',
        "src/App.tsx": "import React from 'react';\nimport { Dashboard } from './components/Dashboard';\nimport { AuthProvider } from './context/AuthContext';\n\nexport function App() {\n  return (\n    <AuthProvider>\n      <Dashboard />\n    </AuthProvider>\n  );\n}\n",
    },
    "auth-service": {
        "README.md": "# auth-service\n\nJWT-based authentication microservice.\n\n## Features\n\n- RS256 JWT signing\n- OAuth2 with PKCE\n- Refresh token rotation\n- Rate limiting\n\n## Configuration\n\nSet environment variables:\n- `JWT_PRIVATE_KEY_PATH` – path to RSA private key PEM\n- `JWT_PUBLIC_KEY_PATH` – path to RSA public key PEM\n",
        "src/main/kotlin/com/demo/auth/JwtTokenProvider.kt": "package com.demo.auth\n\nimport io.jsonwebtoken.Jwts\nimport io.jsonwebtoken.SignatureAlgorithm\nimport org.springframework.stereotype.Component\n\n@Component\nclass JwtTokenProvider(\n    private val keyPair: KeyPairConfig,\n) {\n    fun generate(subject: String, roles: List<String>): String {\n        return Jwts.builder()\n            .setSubject(subject)\n            .claim(\"roles\", roles)\n            .signWith(keyPair.privateKey, SignatureAlgorithm.RS256)\n            .compact()\n    }\n\n    fun validate(token: String): Boolean {\n        return try {\n            Jwts.parserBuilder()\n                .setSigningKey(keyPair.publicKey)\n                .build()\n                .parseClaimsJws(token)\n            true\n        } catch (e: Exception) { false }\n    }\n}\n",
    },
}

# ── Code search index ─────────────────────────────────────────────────────────

CODE_SEARCH_INDEX = [
    # backend-api
    {"repo": "backend-api", "path": "src/main/kotlin/com/demo/user/UserRepository.kt",
     "content": "UserRepository JpaRepository findByEmail findByUsername authentication database"},
    {"repo": "backend-api", "path": "src/main/kotlin/com/demo/payment/PaymentController.kt",
     "content": "PaymentController PaymentService charge payment REST API"},
    {"repo": "backend-api", "path": "build.gradle.kts",
     "content": "spring-boot postgresql kotlin dependencies build gradle"},
    # auth-service
    {"repo": "auth-service", "path": "src/main/kotlin/com/demo/auth/JwtTokenProvider.kt",
     "content": "JwtTokenProvider JWT RS256 authentication token signing validation"},
    # frontend-app
    {"repo": "frontend-app", "path": "src/App.tsx",
     "content": "React App Dashboard AuthProvider TypeScript frontend"},
    {"repo": "frontend-app", "path": "package.json",
     "content": "react typescript vite tanstack query dependencies"},
]

# ── Issue/PR search index ─────────────────────────────────────────────────────

ISSUE_SEARCH_INDEX = [
    {
        "number": 42, "repo": "backend-api", "type": "pull_request",
        "title": "feat: add payment-service REST client with retry logic",
        "body": "payment REST client retry exponential back-off BACKEND-5",
        "state": "open", "user": {"login": "alice"},
        "html_url": "http://localhost:8082/demo-org/backend-api/pull/42",
        "created_at": "2026-05-20T09:00:00Z",
    },
    {
        "number": 41, "repo": "backend-api", "type": "pull_request",
        "title": "fix: correct null-pointer in UserRepository.findByEmail()",
        "body": "NPE null pointer UserRepository email fix bug",
        "state": "open", "user": {"login": "bob"},
        "html_url": "http://localhost:8082/demo-org/backend-api/pull/41",
        "created_at": "2026-05-22T11:15:00Z",
    },
    {
        "number": 9, "repo": "auth-service", "type": "pull_request",
        "title": "sec: rotate JWT signing key and enforce PKCE for OAuth2 flow",
        "body": "JWT RS256 PKCE OAuth2 security authentication token",
        "state": "open", "user": {"login": "bob"},
        "html_url": "http://localhost:8082/demo-org/auth-service/pull/9",
        "created_at": "2026-05-23T10:00:00Z",
    },
    {
        "number": 17, "repo": "frontend-app", "type": "pull_request",
        "title": "feat: dark mode toggle for dashboard",
        "body": "dark mode toggle UI frontend dashboard react",
        "state": "open", "user": {"login": "carol"},
        "html_url": "http://localhost:8082/demo-org/frontend-app/pull/17",
        "created_at": "2026-05-21T13:00:00Z",
    },
]
