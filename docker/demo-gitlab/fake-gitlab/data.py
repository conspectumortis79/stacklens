"""
StackLens Demo GitLab Mock — dataset
Projects, file trees, file contents, and merge requests.
"""

# ── Groups ────────────────────────────────────────────────────────────────────

GROUPS = [
    {
        "id": 10,
        "name": "Demo Group",
        "path": "demo-group",
        "full_path": "demo-group",
        "description": "StackLens demo repositories",
        "web_url": "http://localhost:3080/groups/demo-group",
    }
]

# ── Projects ──────────────────────────────────────────────────────────────────

PROJECTS = [
    {
        "id": 1,
        "name": "backend-api",
        "path": "backend-api",
        "path_with_namespace": "demo-group/backend-api",
        "description": "Core backend REST API service (Kotlin + Spring Boot)",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/backend-api",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 2,
        "name": "frontend-app",
        "path": "frontend-app",
        "path_with_namespace": "demo-group/frontend-app",
        "description": "React TypeScript frontend application",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/frontend-app",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 3,
        "name": "auth-service",
        "path": "auth-service",
        "path_with_namespace": "demo-group/auth-service",
        "description": "JWT authentication and authorisation microservice",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/auth-service",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 4,
        "name": "devops-infra",
        "path": "devops-infra",
        "path_with_namespace": "demo-group/devops-infra",
        "description": "Terraform IaC + Helm charts + Kubernetes manifests",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/devops-infra",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 5,
        "name": "data-pipeline",
        "path": "data-pipeline",
        "path_with_namespace": "demo-group/data-pipeline",
        "description": "Python ETL data pipeline with database integration",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/data-pipeline",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 6,
        "name": "user-service",
        "path": "user-service",
        "path_with_namespace": "demo-group/user-service",
        "description": "User management microservice – profiles, avatars, preferences (Spring Boot 3, PostgreSQL)",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/user-service",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 7,
        "name": "ecommerce-api",
        "path": "ecommerce-api",
        "path_with_namespace": "demo-group/ecommerce-api",
        "description": "PHP/Laravel e-commerce REST API – products, orders, cart, auth (load test target)",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/ecommerce-api",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
    {
        "id": 8,
        "name": "pipeline-demo",
        "path": "pipeline-demo",
        "path_with_namespace": "demo-group/pipeline-demo",
        "description": "Reference stack for the StackLens Pipeline Analysis feature – docker-compose stack + multi-CI pipelines (GitLab CI, GitHub Actions, Jenkins, Bitbucket, Azure)",
        "default_branch": "main",
        "web_url": "http://localhost:3080/demo-group/pipeline-demo",
        "namespace": {"id": 10, "name": "Demo Group", "path": "demo-group", "kind": "group"},
    },
] 

# ── Project file contents ─────────────────────────────────────────────────────
# Dict structure: { project_id: { "file/path.ext": "file content" } }

PROJECT_FILES = {

    # ── Project 1: backend-api (Kotlin / Spring Boot) ─────────────────────────
    1: {
        "build.gradle.kts": """\
plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.company"
version = "2.4.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
""",
        "src/main/kotlin/com/company/backendapi/Application.kt": """\
package com.company.backendapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class BackendApiApplication

fun main(args: Array<String>) {
    runApplication<BackendApiApplication>(*args)
}
""",
        "src/main/kotlin/com/company/backendapi/config/SecurityConfig.kt": """\
package com.company.backendapi.config

import com.company.backendapi.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Security configuration:
 * - Stateless JWT-based authentication
 * - BCrypt password encoding
 * - Method-level security via @PreAuthorize
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
""",
        "src/main/kotlin/com/company/backendapi/controller/UserController.kt": """\
package com.company.backendapi.controller

import com.company.backendapi.service.UserService
import com.company.backendapi.dto.UserDto
import com.company.backendapi.dto.UserUpdateRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * REST controller for user management.
 * All endpoints require authentication (JWT Bearer token).
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    /** Get a user by ID. Requires ADMIN role or matching user ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    fun getUser(@PathVariable id: Long): ResponseEntity<UserDto> =
        userService.findById(id)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    /** List all users. Admin only. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<UserDto>> =
        ResponseEntity.ok(userService.findAll(page, size))

    /** Update user profile. Requires matching user ID or ADMIN. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    fun updateUser(
        @PathVariable id: Long,
        @RequestBody request: UserUpdateRequest,
        @AuthenticationPrincipal principal: Any,
    ): ResponseEntity<UserDto> =
        ResponseEntity.ok(userService.update(id, request))

    /** Delete user. Admin only. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
""",
        "src/main/kotlin/com/company/backendapi/controller/OrderController.kt": """\
package com.company.backendapi.controller

import com.company.backendapi.service.OrderService
import com.company.backendapi.service.PaymentService
import com.company.backendapi.dto.CreateOrderRequest
import com.company.backendapi.dto.OrderDto
import com.company.backendapi.dto.PaymentRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Order management REST API.
 * Integrates with payment service for payment processing.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {
    /** Create a new order. Authenticated users only. */
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<OrderDto> {
        val order = orderService.create(request)
        return ResponseEntity.ok(order)
    }

    /** Process payment for an order. */
    @PostMapping("/{id}/pay")
    fun processPayment(
        @PathVariable id: Long,
        @RequestBody payment: PaymentRequest,
    ): ResponseEntity<Void> {
        paymentService.processPayment(id, payment)
        return ResponseEntity.ok().build()
    }

    /** Get recent orders for the authenticated user. */
    @GetMapping
    fun listOrders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<OrderDto>> =
        ResponseEntity.ok(orderService.findForCurrentUser(page, size))

    /** Cancel an order. Only pending orders can be cancelled. */
    @DeleteMapping("/{id}")
    @PreAuthorize("@orderService.isOwner(#id, authentication)")
    fun cancelOrder(@PathVariable id: Long): ResponseEntity<Void> {
        orderService.cancel(id)
        return ResponseEntity.noContent().build()
    }
}
""",
        "src/main/kotlin/com/company/backendapi/service/UserService.kt": """\
package com.company.backendapi.service

import com.company.backendapi.domain.User
import com.company.backendapi.dto.UserDto
import com.company.backendapi.dto.UserUpdateRequest
import com.company.backendapi.repository.UserRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * User domain service.
 * Database access via UserRepository (Spring Data JPA).
 * Redis caching: user profiles cached for 5 minutes.
 */
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
) {
    @Cacheable(cacheNames = ["users"], key = "#id")
    fun findById(id: Long): UserDto? =
        userRepository.findById(id).map { it.toDto() }.orElse(null)

    fun findAll(page: Int, size: Int): List<UserDto> {
        // Database query: paginated user list, excludes soft-deleted records
        return userRepository.findAllActive(page, size).map { it.toDto() }
    }

    @CacheEvict(cacheNames = ["users"], key = "#id")
    fun update(id: Long, request: UserUpdateRequest): UserDto {
        val user = userRepository.findById(id).orElseThrow()
        user.displayName = request.displayName
        user.email = request.email
        return userRepository.save(user).toDto()
    }

    @CacheEvict(cacheNames = ["users"], key = "#id")
    fun delete(id: Long) {
        // Soft-delete: set deletedAt timestamp, do not remove from database
        val user = userRepository.findById(id).orElseThrow()
        user.softDelete()
        userRepository.save(user)
    }

    private fun User.toDto() = UserDto(
        id = this.id,
        email = this.email,
        displayName = this.displayName,
        role = this.role,
        createdAt = this.createdAt,
    )
}
""",
        "src/main/resources/application.yml": """\
spring:
  application:
    name: backend-api
  datasource:
    url: jdbc:postgresql://localhost:5432/backenddb
    username: ${DB_USER:app}
    password: ${DB_PASSWORD:secret}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: ${JWT_SECRET:changeme-in-production}
  expiration-minutes: 15
  refresh-expiration-days: 30

server:
  port: 8080
""",
        ".gitlab-ci.yml": """\
stages:
  - build
  - test
  - security-scan
  - deploy

variables:
  DOCKER_IMAGE: registry.company.com/demo-group/backend-api
  JAVA_VERSION: "21"

build:
  stage: build
  image: gradle:8.5-jdk21
  script:
    - ./gradlew clean build -x test
  artifacts:
    paths:
      - build/libs/*.jar

unit-tests:
  stage: test
  image: gradle:8.5-jdk21
  script:
    - ./gradlew test
  coverage: '/Branch coverage: (\\d+\\.\\d+)%/'

security-scan:
  stage: security-scan
  image: owasp/dependency-check:latest
  script:
    - dependency-check.sh --project backend-api --scan . --format JSON
  allow_failure: true

deploy-staging:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/backend-api backend-api=${DOCKER_IMAGE}:${CI_COMMIT_SHORT_SHA} -n staging
    - kubectl rollout status deployment/backend-api -n staging
  environment:
    name: staging
  only:
    - develop

deploy-production:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/backend-api backend-api=${DOCKER_IMAGE}:${CI_COMMIT_TAG} -n production
    - kubectl rollout status deployment/backend-api -n production
  environment:
    name: production
  only:
    - tags
""",
        "src/main/kotlin/com/company/backendapi/repository/UserRepository.kt": """\
package com.company.backendapi.repository

import com.company.backendapi.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * JPA repository for User entity.
 * PostgreSQL database backend.
 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.createdAt DESC LIMIT :size OFFSET (:page * :size)")
    fun findAllActive(page: Int, size: Int): List<User>

    fun existsByEmail(email: String): Boolean
}
""",
    },

    # ── Project 2: frontend-app (TypeScript / React) ──────────────────────────
    2: {
        "package.json": """\
{
  "name": "frontend-app",
  "version": "5.2.1",
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-router-dom": "^6.22.3",
    "axios": "^1.6.8",
    "zustand": "^4.5.2",
    "@tanstack/react-query": "^5.28.4"
  },
  "devDependencies": {
    "typescript": "^5.4.3",
    "vitest": "^1.4.0",
    "@testing-library/react": "^15.0.2"
  },
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "test": "vitest"
  }
}
""",
        "src/services/authService.ts": """\
import axios from 'axios';

const AUTH_API = '/api/v1/auth';
const TOKEN_KEY = 'access_token';
const REFRESH_KEY = 'refresh_token';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/**
 * Authentication service: handles JWT token login, refresh, and logout.
 * Tokens are stored in localStorage (access) and httpOnly cookie (refresh).
 */
export const authService = {
  async login(credentials: LoginRequest): Promise<AuthTokens> {
    const response = await axios.post<AuthTokens>(`${AUTH_API}/login`, credentials);
    const { accessToken, refreshToken } = response.data;
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
    return response.data;
  },

  async refreshToken(): Promise<string> {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (!refreshToken) throw new Error('No refresh token available');

    const response = await axios.post<AuthTokens>(`${AUTH_API}/refresh`, { refreshToken });
    const { accessToken } = response.data;
    localStorage.setItem(TOKEN_KEY, accessToken);
    return accessToken;
  },

  async logout(): Promise<void> {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      await axios.post(`${AUTH_API}/logout`, {}, {
        headers: { Authorization: `Bearer ${token}` },
      });
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },

  getAccessToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  },
};
""",
        "src/hooks/useAuth.ts": """\
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService, LoginRequest } from '../services/authService';

/**
 * Custom hook for authentication state management.
 * Wraps authService and provides loading/error state.
 */
export function useAuth() {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const login = useCallback(async (credentials: LoginRequest) => {
    setIsLoading(true);
    setError(null);
    try {
      await authService.login(credentials);
      const returnUrl = new URLSearchParams(window.location.search).get('returnUrl') ?? '/dashboard';
      navigate(returnUrl, { replace: true });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Authentication failed';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [navigate]);

  const logout = useCallback(async () => {
    await authService.logout();
    navigate('/login', { replace: true });
  }, [navigate]);

  return {
    isAuthenticated: authService.isAuthenticated(),
    isLoading,
    error,
    login,
    logout,
  };
}
""",
        "src/components/LoginForm.tsx": """\
import React, { useState } from 'react';
import { useAuth } from '../hooks/useAuth';

/**
 * Login form component.
 * Collects email + password, calls authentication service.
 */
export const LoginForm: React.FC = () => {
  const { login, isLoading, error } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await login({ email, password });
  };

  return (
    <form onSubmit={handleSubmit} className="login-form">
      <h2>Sign in</h2>
      {error && <div className="error-banner">{error}</div>}

      <label htmlFor="email">Email</label>
      <input
        id="email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        autoComplete="username"
        required
      />

      <label htmlFor="password">Password</label>
      <input
        id="password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        autoComplete="current-password"
        required
      />

      <button type="submit" disabled={isLoading}>
        {isLoading ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  );
};
""",
        "src/pages/Dashboard.tsx": """\
import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import axios from 'axios';

interface OrderSummary {
  total: number;
  pending: number;
  completed: number;
}

/**
 * Main dashboard page.
 * Displays user summary and recent orders.
 * Auto-refreshes every 60 seconds.
 */
const Dashboard: React.FC = () => {
  const { isAuthenticated, logout } = useAuth();

  const { data: summary, isLoading } = useQuery<OrderSummary>({
    queryKey: ['dashboard-summary'],
    queryFn: () => axios.get('/api/v1/dashboard/summary').then(r => r.data),
    refetchInterval: 60_000,
  });

  if (!isAuthenticated) return <div>Please log in to continue.</div>;
  if (isLoading) return <div className="spinner" />;

  return (
    <main className="dashboard">
      <header>
        <h1>Dashboard</h1>
        <button onClick={logout}>Logout</button>
      </header>
      <section className="metrics">
        <div className="metric-card">
          <span className="label">Total Orders</span>
          <span className="value">{summary?.total ?? 0}</span>
        </div>
        <div className="metric-card">
          <span className="label">Pending</span>
          <span className="value">{summary?.pending ?? 0}</span>
        </div>
        <div className="metric-card">
          <span className="label">Completed</span>
          <span className="value">{summary?.completed ?? 0}</span>
        </div>
      </section>
    </main>
  );
};

export default Dashboard;
""",
        "src/interceptors/authInterceptor.ts": """\
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { authService } from '../services/authService';

let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: string) => void; reject: (e: unknown) => void }> = [];

/**
 * Axios interceptor that:
 * 1. Attaches Bearer JWT token to every outgoing request.
 * 2. On 401 response: attempts token refresh exactly once, then replays failed requests.
 * 3. On refresh failure: redirects to /login.
 */
axios.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = authService.getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

axios.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: (token) => {
            original.headers.Authorization = `Bearer ${token}`;
            resolve(axios(original));
          },
          reject,
        });
      });
    }

    original._retry = true;
    isRefreshing = true;

    try {
      const newToken = await authService.refreshToken();
      failedQueue.forEach(({ resolve }) => resolve(newToken));
      failedQueue = [];
      original.headers.Authorization = `Bearer ${newToken}`;
      return axios(original);
    } catch (refreshError) {
      failedQueue.forEach(({ reject }) => reject(refreshError));
      failedQueue = [];
      authService.logout();
      window.location.href = '/login';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);
""",
        ".gitlab-ci.yml": """\
stages:
  - install
  - test
  - build
  - deploy

default:
  image: node:20-alpine
  cache:
    paths:
      - node_modules/

install-deps:
  stage: install
  script:
    - npm ci

unit-tests:
  stage: test
  script:
    - npm run test -- --reporter=junit --outputFile=junit.xml
  artifacts:
    reports:
      junit: junit.xml

build-production:
  stage: build
  script:
    - npm run build
  artifacts:
    paths:
      - dist/

deploy-staging:
  stage: deploy
  image: alpine:latest
  script:
    - apk add --no-cache rsync openssh-client
    - rsync -avz dist/ user@staging.company.com:/var/www/html/
  environment:
    name: staging
    url: https://staging.company.com
  only:
    - develop

deploy-production:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/frontend-app frontend=${CI_REGISTRY_IMAGE}:${CI_COMMIT_TAG} -n production
    - kubectl rollout status deployment/frontend-app -n production
  only:
    - tags
""",
    },

    # ── Project 3: auth-service (Kotlin – JWT microservice) ───────────────────
    3: {
        "src/main/kotlin/com/company/auth/service/JwtTokenService.kt": """\
package com.company.auth.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT token generation and validation service.
 *
 * - Access tokens: RS256, 15-minute expiry
 * - Refresh tokens: HS256, 30-day expiry, stored in Redis
 * - Token claims: sub (userId), roles, tenantId, iat, exp
 */
@Service
class JwtTokenService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-minutes:15}") private val accessExpiryMinutes: Long,
    @Value("\${jwt.refresh-expiration-days:30}") private val refreshExpiryDays: Long,
) {
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /** Generate a new JWT access token for the given user. */
    fun generateAccessToken(userId: Long, email: String, roles: List<String>, tenantId: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .claim("tenantId", tenantId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessExpiryMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact()
    }

    /** Generate a refresh token (long-lived, stored hash in Redis). */
    fun generateRefreshToken(userId: Long): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(refreshExpiryDays, ChronoUnit.DAYS)))
            .signWith(signingKey)
            .compact()
    }

    /** Validate a JWT token and return its claims. Throws JwtException on failure. */
    fun validateAndParseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /** Extract user ID from a validated token. */
    fun extractUserId(token: String): Long =
        validateAndParseClaims(token).subject.toLong()

    /** Check if a token is expired without throwing. */
    fun isTokenExpired(token: String): Boolean =
        try {
            validateAndParseClaims(token).expiration.before(Date())
        } catch (_: JwtException) {
            true
        }
}
""",
        "src/main/kotlin/com/company/auth/controller/AuthController.kt": """\
package com.company.auth.controller

import com.company.auth.dto.LoginRequest
import com.company.auth.dto.LoginResponse
import com.company.auth.dto.RefreshRequest
import com.company.auth.service.AuthenticationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Authentication REST API.
 * Endpoints: /auth/login, /auth/refresh, /auth/logout
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthenticationService,
) {
    /**
     * Authenticate user with email + password.
     * Returns JWT access token and refresh token on success.
     */
    @PostMapping("/login")
    fun login(@RequestBody @Valid request: LoginRequest): ResponseEntity<LoginResponse> {
        val tokens = authService.authenticate(request.email, request.password)
        return ResponseEntity.ok(tokens)
    }

    /**
     * Refresh an expired access token using a valid refresh token.
     * Refresh tokens are rotated on each use (RTR - Refresh Token Rotation).
     */
    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid request: RefreshRequest): ResponseEntity<LoginResponse> {
        val tokens = authService.refreshTokens(request.refreshToken)
        return ResponseEntity.ok(tokens)
    }

    /**
     * Invalidate the current session (revoke refresh token in Redis).
     */
    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization") authHeader: String): ResponseEntity<Void> {
        val token = authHeader.removePrefix("Bearer ").trim()
        authService.logout(token)
        return ResponseEntity.noContent().build()
    }
}
""",
        "src/main/kotlin/com/company/auth/service/AuthenticationService.kt": """\
package com.company.auth.service

import com.company.auth.dto.LoginResponse
import com.company.auth.exception.AuthenticationException
import com.company.auth.repository.UserRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Core authentication service.
 * Validates credentials, issues JWT tokens, manages Redis refresh token store.
 */
@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    companion object {
        private const val REFRESH_TOKEN_PREFIX = "refresh_token:"
        private val REFRESH_TOKEN_TTL = Duration.ofDays(30)
    }

    fun authenticate(email: String, password: String): LoginResponse {
        val user = userRepository.findByEmail(email)
            ?: throw AuthenticationException("Invalid email or password")

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw AuthenticationException("Invalid email or password")
        }

        val accessToken = jwtTokenService.generateAccessToken(
            userId = user.id,
            email = user.email,
            roles = user.roles,
            tenantId = user.tenantId,
        )
        val refreshToken = jwtTokenService.generateRefreshToken(user.id)

        // Store refresh token hash in Redis with TTL
        val tokenKey = "$REFRESH_TOKEN_PREFIX${user.id}"
        redisTemplate.opsForValue().set(tokenKey, refreshToken.hashCode().toString(), REFRESH_TOKEN_TTL)

        return LoginResponse(accessToken, refreshToken, expiresIn = 900)
    }

    fun refreshTokens(refreshToken: String): LoginResponse {
        val userId = jwtTokenService.extractUserId(refreshToken)
        val storedHash = redisTemplate.opsForValue().get("$REFRESH_TOKEN_PREFIX$userId")
            ?: throw AuthenticationException("Refresh token expired or revoked")

        if (storedHash != refreshToken.hashCode().toString()) {
            throw AuthenticationException("Invalid refresh token")
        }

        val user = userRepository.findById(userId).orElseThrow {
            AuthenticationException("User not found")
        }

        return authenticate(user.email, "")  // Reissue tokens
    }

    fun logout(accessToken: String) {
        val userId = jwtTokenService.extractUserId(accessToken)
        redisTemplate.delete("$REFRESH_TOKEN_PREFIX$userId")
    }
}
""",
        "src/main/kotlin/com/company/auth/security/JwtAuthenticationFilter.kt": """\
package com.company.auth.security

import com.company.auth.service.JwtTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT authentication filter.
 * Extracts Bearer token from Authorization header, validates it,
 * and populates the Spring Security context on success.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization") ?: ""

        if (!authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix("Bearer ").trim()

        try {
            val claims = jwtTokenService.validateAndParseClaims(token)
            val roles = (claims["roles"] as? List<*>)?.map {
                SimpleGrantedAuthority("ROLE_$it")
            } ?: emptyList()

            val authentication = UsernamePasswordAuthenticationToken(
                claims.subject.toLong(),
                null,
                roles,
            )
            SecurityContextHolder.getContext().authentication = authentication
        } catch (_: Exception) {
            // Invalid token → let Spring Security reject the request
        }

        filterChain.doFilter(request, response)
    }
}
""",
        ".gitlab-ci.yml": """\
stages:
  - build
  - test
  - deploy

build-jar:
  stage: build
  image: gradle:8.5-jdk21
  script:
    - ./gradlew clean bootJar -x test
  artifacts:
    paths:
      - build/libs/auth-service-*.jar

integration-tests:
  stage: test
  image: gradle:8.5-jdk21
  services:
    - name: redis:7-alpine
      alias: redis
    - name: postgres:14-alpine
      alias: postgres
      variables:
        POSTGRES_DB: testdb
        POSTGRES_PASSWORD: test
  script:
    - ./gradlew integrationTest
  variables:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/testdb
    SPRING_DATA_REDIS_HOST: redis
    JWT_SECRET: test-secret-not-for-production

deploy-auth-service:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/auth-service auth-service=${CI_REGISTRY_IMAGE}:${CI_COMMIT_SHORT_SHA} -n production
    - kubectl rollout status deployment/auth-service -n production --timeout=120s
  environment:
    name: production
  only:
    - tags
""",
    },

    # ── Project 4: devops-infra (Terraform + Kubernetes + Helm) ──────────────
    4: {
        "terraform/main.tf": """\
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.27"
    }
  }
  backend "s3" {
    bucket         = "company-terraform-state"
    key            = "infrastructure/main.tfstate"
    region         = "eu-central-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}

provider "aws" {
  region = var.aws_region
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────

module "eks" {
  source          = "terraform-aws-modules/eks/aws"
  version         = "~> 20.0"
  cluster_name    = "${var.environment}-cluster"
  cluster_version = "1.29"
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnets

  eks_managed_node_groups = {
    general = {
      instance_types = ["m6i.large"]
      min_size       = 2
      max_size       = 8
      desired_size   = 3
    }
    memory_optimized = {
      instance_types = ["r6i.xlarge"]
      min_size       = 0
      max_size       = 4
      desired_size   = 1
      labels         = { workload = "memory-intensive" }
    }
  }
}

# ── RDS PostgreSQL ─────────────────────────────────────────────────────────────

resource "aws_db_instance" "postgres" {
  identifier        = "${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = "14.11"
  instance_class    = var.db_instance_class
  allocated_storage = 100
  storage_encrypted = true
  kms_key_id        = aws_kms_key.rds.arn
  multi_az          = var.environment == "production"
  db_name           = "appdb"
  username          = "dbadmin"
  password          = random_password.db_password.result

  backup_retention_period = 30
  deletion_protection     = var.environment == "production"
}

# ── ElastiCache Redis ──────────────────────────────────────────────────────────

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.environment}-redis"
  description          = "Redis cluster for session + cache"
  engine_version       = "7.1"
  node_type            = "cache.r7g.large"
  num_cache_clusters   = var.environment == "production" ? 3 : 1
  port                 = 6379
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}
""",
        "terraform/variables.tf": """\
variable "environment" {
  description = "Deployment environment: dev | staging | production"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-central-1"
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class"
  type        = string
  default     = "db.t4g.medium"
}
""",
        "kubernetes/deployment.yaml": """\
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-api
  namespace: production
  labels:
    app: backend-api
    version: "2.4.0"
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend-api
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: backend-api
    spec:
      serviceAccountName: backend-api
      containers:
        - name: backend-api
          image: registry.company.com/demo-group/backend-api:2.4.0
          ports:
            - containerPort: 8080
          env:
            - name: DB_USER
              valueFrom:
                secretKeyRef:
                  name: backend-api-secrets
                  key: db-user
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: backend-api-secrets
                  key: db-password
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: backend-api-secrets
                  key: jwt-secret
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "1000m"
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 30
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-api-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend-api
  minReplicas: 3
  maxReplicas: 12
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
""",
        "helm/values.yaml": """\
# Default values for the company Helm chart.
# Override per environment in values-staging.yaml / values-production.yaml.

replicaCount: 2

image:
  repository: registry.company.com/demo-group
  pullPolicy: IfNotPresent
  tag: ""  # overridden by CI/CD pipeline

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/rate-limit: "100"  # requests per minute per IP
  hosts:
    - host: api.company.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: api-tls
      hosts:
        - api.company.com

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

# PostgreSQL database connection
database:
  host: rds-prod.internal
  port: 5432
  name: appdb
  secretName: backend-api-secrets

# Redis cache
redis:
  host: redis-cluster.internal
  port: 6379
  secretName: redis-auth
""",
        ".gitlab-ci.yml": """\
stages:
  - validate
  - plan
  - apply
  - deploy

variables:
  TF_ROOT: terraform
  TF_STATE_NAME: default

terraform-validate:
  stage: validate
  image: hashicorp/terraform:1.7
  script:
    - cd $TF_ROOT
    - terraform init -backend=false
    - terraform validate
    - terraform fmt -check -recursive

terraform-plan-staging:
  stage: plan
  image: hashicorp/terraform:1.7
  script:
    - cd $TF_ROOT
    - terraform init
    - terraform plan -var="environment=staging" -out=staging.tfplan
  artifacts:
    paths:
      - $TF_ROOT/staging.tfplan
  only:
    - develop

terraform-apply-staging:
  stage: apply
  image: hashicorp/terraform:1.7
  script:
    - cd $TF_ROOT
    - terraform apply staging.tfplan
  environment:
    name: staging
  when: manual
  only:
    - develop

kubernetes-deploy:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - helm upgrade --install backend-api helm/
      --namespace production
      --set image.tag=${CI_COMMIT_TAG}
      --values helm/values-production.yaml
    - kubectl rollout status deployment/backend-api -n production
  environment:
    name: production
  only:
    - tags
""",
        "helm/values-production.yaml": """\
replicaCount: 3

resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "2000m"

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20
  targetCPUUtilizationPercentage: 65

# Production database
database:
  host: rds-prod-primary.internal

# Production Redis cluster
redis:
  host: redis-prod-cluster.internal
""",
    },

    # ── Project 5: data-pipeline (Python ETL) ─────────────────────────────────
    5: {
        "requirements.txt": """\
psycopg2-binary==2.9.9
SQLAlchemy==2.0.29
pandas==2.2.1
pydantic==2.6.4
PyJWT==2.8.0
redis==5.0.4
structlog==24.1.0
""",
        "src/etl/database.py": """\
\"\"\"
Database connection and query utilities.
Supports PostgreSQL via SQLAlchemy with connection pooling.
\"\"\"

import os
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import QueuePool
import structlog

log = structlog.get_logger(__name__)

DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://user:password@localhost:5432/pipeline_db",
)


def create_db_engine():
    \"\"\"
    Create a SQLAlchemy engine with connection pooling.
    Pool size: 10 connections, overflow: 5.
    \"\"\"
    return create_engine(
        DATABASE_URL,
        poolclass=QueuePool,
        pool_size=10,
        max_overflow=5,
        pool_timeout=30,
        pool_recycle=3600,
        echo=False,
    )


engine = create_db_engine()
Session = sessionmaker(bind=engine)


def run_query(sql: str, params: dict | None = None) -> list[dict]:
    \"\"\"Execute a raw SQL query and return results as list of dicts.\"\"\"
    with Session() as session:
        result = session.execute(text(sql), params or {})
        return [dict(row._mapping) for row in result]


def execute_statement(sql: str, params: dict | None = None) -> int:
    \"\"\"Execute a DML statement (INSERT/UPDATE/DELETE). Returns row count.\"\"\"
    with Session() as session:
        result = session.execute(text(sql), params or {})
        session.commit()
        log.info("database.execute", sql=sql[:80], rows_affected=result.rowcount)
        return result.rowcount
""",
        "src/etl/pipeline.py": """\
\"\"\"
Main ETL pipeline orchestrator.
Extracts data from source database, transforms, and loads into data warehouse.
\"\"\"

import structlog
from .database import run_query, execute_statement

log = structlog.get_logger(__name__)

BATCH_SIZE = 1000


def extract_users(since_updated: str | None = None) -> list[dict]:
    \"\"\"Extract user records from the main application database.\"\"\"
    sql = \"\"\"
        SELECT
            id,
            email,
            display_name,
            role,
            tenant_id,
            created_at,
            updated_at,
            last_login_at
        FROM users
        WHERE deleted_at IS NULL
    \"\"\"
    if since_updated:
        sql += " AND updated_at >= :since"
        return run_query(sql, {"since": since_updated})
    return run_query(sql)


def transform_user(user: dict) -> dict:
    \"\"\"Apply business logic transformations to a user record.\"\"\"
    return {
        **user,
        "email_domain": user["email"].split("@")[1] if "@" in user["email"] else None,
        "is_active": user.get("last_login_at") is not None,
        "role_normalized": user["role"].upper() if user.get("role") else "USER",
    }


def load_users_to_warehouse(users: list[dict]) -> int:
    \"\"\"Upsert user records into the data warehouse. Returns loaded count.\"\"\"
    loaded = 0
    for i in range(0, len(users), BATCH_SIZE):
        batch = users[i:i + BATCH_SIZE]
        for user in batch:
            execute_statement(
                \"\"\"
                INSERT INTO dw_users (id, email, email_domain, role, tenant_id, is_active, created_at)
                VALUES (:id, :email, :email_domain, :role_normalized, :tenant_id, :is_active, :created_at)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    email_domain = EXCLUDED.email_domain,
                    role = EXCLUDED.role,
                    is_active = EXCLUDED.is_active
                \"\"\",
                user,
            )
            loaded += 1
    log.info("pipeline.load", entity="users", count=loaded)
    return loaded


def run_user_pipeline(since_updated: str | None = None) -> dict:
    \"\"\"Full ETL pipeline for user data.\"\"\"
    log.info("pipeline.start", pipeline="users")
    raw = extract_users(since_updated)
    transformed = [transform_user(u) for u in raw]
    loaded = load_users_to_warehouse(transformed)
    log.info("pipeline.complete", pipeline="users", extracted=len(raw), loaded=loaded)
    return {"extracted": len(raw), "loaded": loaded}
""",
        "src/auth/token_validator.py": """\
\"\"\"
JWT token validation for the data pipeline API.
Validates incoming tokens against the auth-service public key.
\"\"\"

import os
import jwt
import structlog
from functools import wraps
from flask import request, jsonify

log = structlog.get_logger(__name__)

JWT_SECRET = os.environ.get("JWT_SECRET", "changeme")
JWT_ALGORITHM = "HS256"


def validate_token(token: str) -> dict:
    \"\"\"
    Validate a JWT Bearer token and return its decoded claims.
    Raises jwt.JWTError on invalid or expired tokens.
    \"\"\"
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        log.debug("auth.token_valid", user_id=payload.get("sub"))
        return payload
    except jwt.ExpiredSignatureError:
        log.warning("auth.token_expired")
        raise
    except jwt.InvalidTokenError as e:
        log.warning("auth.token_invalid", error=str(e))
        raise


def require_authentication(f):
    \"\"\"Decorator: validate JWT Bearer token on Flask route.\"\"\"
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return jsonify({"error": "Missing or invalid Authorization header"}), 401

        token = auth_header.removeprefix("Bearer ").strip()
        try:
            claims = validate_token(token)
            request.user_claims = claims
        except jwt.JWTError as e:
            return jsonify({"error": f"Authentication failed: {e}"}), 401

        return f(*args, **kwargs)
    return decorated
""",
        "src/api/routes.py": """\
\"\"\"
REST API routes for the data pipeline management interface.
\"\"\"

from flask import Blueprint, jsonify, request
from ..auth.token_validator import require_authentication
from ..etl.pipeline import run_user_pipeline

pipeline_bp = Blueprint("pipeline", __name__, url_prefix="/api/v1/pipeline")


@pipeline_bp.route("/run/users", methods=["POST"])
@require_authentication
def trigger_user_pipeline():
    \"\"\"
    Trigger the user ETL pipeline manually.
    Requires ADMIN role. Accepts optional 'since' timestamp parameter.
    \"\"\"
    claims = request.user_claims
    if "ADMIN" not in claims.get("roles", []):
        return jsonify({"error": "Insufficient permissions"}), 403

    body = request.get_json(silent=True) or {}
    since = body.get("since")

    result = run_user_pipeline(since_updated=since)
    return jsonify(result), 200


@pipeline_bp.route("/status", methods=["GET"])
@require_authentication
def pipeline_status():
    \"\"\"Get last run status for all pipelines.\"\"\"
    from ..etl.database import run_query
    rows = run_query(
        \"SELECT pipeline_name, last_run_at, records_processed, status FROM pipeline_runs ORDER BY last_run_at DESC LIMIT 10\"
    )
    return jsonify(rows), 200
""",
        ".gitlab-ci.yml": """\
stages:
  - lint
  - test
  - build
  - deploy

default:
  image: python:3.12-slim

lint:
  stage: lint
  script:
    - pip install ruff mypy
    - ruff check src/
    - mypy src/ --ignore-missing-imports

unit-tests:
  stage: test
  script:
    - pip install -r requirements.txt pytest pytest-cov
    - pytest tests/ --cov=src --cov-report=xml
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: coverage.xml

build-docker:
  stage: build
  image: docker:24
  script:
    - docker build -t ${CI_REGISTRY_IMAGE}:${CI_COMMIT_SHORT_SHA} .
    - docker push ${CI_REGISTRY_IMAGE}:${CI_COMMIT_SHORT_SHA}

deploy-pipeline:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image cronjob/data-pipeline data-pipeline=${CI_REGISTRY_IMAGE}:${CI_COMMIT_TAG} -n production
  environment:
    name: production
  only:
    - tags
""",
    },

    # ── Project 6: user-service (Java / Spring Boot 3) ─────────────────────────
    6: {
        "build.gradle.kts": """\
plugins {
    id("java")
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.company"
version = "2.4.1"
java.sourceCompatibility = JavaVersion.VERSION_21

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql")
    implementation("software.amazon.awssdk:s3:2.24.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
""",
        "src/main/java/com/company/userservice/UserServiceApplication.java": """\
package com.company.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
""",
        "src/main/java/com/company/userservice/domain/UserProfile.java": """\
package com.company.userservice.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String displayName;

    // TODO STACKLENS-2: add avatarUrl and bio fields
    // private String avatarUrl;
    // private String bio;

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
""",
        "src/main/java/com/company/userservice/dto/UserProfileResponse.java": """\
package com.company.userservice.dto;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String displayName
    // TODO STACKLENS-2: avatarUrl, bio
) {}
""",
        "src/main/java/com/company/userservice/service/ProfileService.java": """\
package com.company.userservice.service;

import com.company.userservice.domain.UserProfile;
import com.company.userservice.dto.UserProfileResponse;
import com.company.userservice.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ProfileService {

    private final UserProfileRepository userProfileRepository;

    public ProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfileResponse getProfile(UUID userId) {
        UserProfile user = userProfileRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    public UserProfileResponse updateProfile(UUID userId, String displayName) {
        UserProfile user = userProfileRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setDisplayName(displayName);
        userProfileRepository.save(user);
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
""",
        "src/main/java/com/company/userservice/repository/UserProfileRepository.java": """\
package com.company.userservice.repository;

import com.company.userservice.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    // STACKLENS-2: add findByEmail if needed
}
""",
        "src/main/java/com/company/userservice/controller/UserController.java": """\
package com.company.userservice.controller;

import com.company.userservice.dto.UserProfileResponse;
import com.company.userservice.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final ProfileService profileService;

    public UserController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}/profile")
    @PreAuthorize("#userId == authentication.principal.userId or hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    // TODO STACKLENS-2: POST /{userId}/avatar  – multipart upload
    // TODO STACKLENS-2: PUT  /{userId}/profile – update bio
}
""",
        "src/main/resources/application.yml": """\
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://localhost:5432/users_db
    username: ${DB_USER:userservice}
    password: ${DB_PASS:changeme}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

storage:
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: user-avatars

server:
  port: 8082
""",
        "src/main/resources/db/migration/V4__init_users.sql": """\
CREATE TABLE IF NOT EXISTS users (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users(email);
""",
        ".gitlab-ci.yml": """\
stages:
  - build
  - test
  - docker
  - deploy

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"

build:
  stage: build
  image: gradle:8.6-jdk21
  script:
    - ./gradlew classes
  cache:
    key: gradle-cache
    paths:
      - .gradle/

test:
  stage: test
  image: gradle:8.6-jdk21
  script:
    - ./gradlew test
  artifacts:
    reports:
      junit: build/test-results/test/TEST-*.xml

docker:
  stage: docker
  image: docker:24
  script:
    - docker build -t ${CI_REGISTRY_IMAGE}:${CI_COMMIT_SHORT_SHA} .
    - docker push ${CI_REGISTRY_IMAGE}:${CI_COMMIT_SHORT_SHA}
  only:
    - main
    - /^STACKLENS-.*/

deploy-staging:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/user-service user-service=${CI_REGISTRY_IMAGE}:${CI_COMMIT_SHORT_SHA} -n staging
  environment:
    name: staging
  only:
    - main
    - /^STACKLENS-.*/
""",
    },
}

PROJECT_FILES[7] = {
    # ── Project 7: ecommerce-api (PHP/Laravel) ─────────────────────────────────
    # Mirrors the fake-backend running on http://localhost:8085.
    # Contains intentional performance bottlenecks for load test analysis.

    "composer.json": """\
{
    "name": "demo-group/ecommerce-api",
    "description": "PHP e-commerce REST API – products, orders, cart, sessions",
    "require": {
        "php": "^8.2",
        "laravel/framework": "^11.0",
        "laravel/sanctum": "^4.0",
        "doctrine/dbal": "^3.7"
    },
    "require-dev": {
        "phpunit/phpunit": "^11.0"
    }
}
""",

    "routes/api.php": """\
<?php
// E-Commerce REST API Routes
// Base URL: http://localhost:8085/api

use App\\Http\\Controllers\\AuthController;
use App\\Http\\Controllers\\ProductController;
use App\\Http\\Controllers\\OrderController;
use App\\Http\\Controllers\\CartController;
use App\\Http\\Controllers\\StatsController;
use Illuminate\\Support\\Facades\\Route;

// Public routes
Route::post('/auth/login',  [AuthController::class, 'login']);
Route::post('/auth/logout', [AuthController::class, 'logout']);
Route::get('/categories',   [ProductController::class, 'categories']);
Route::get('/products',     [ProductController::class, 'index']);   // WARNING: no pagination by default
Route::get('/products/{id}',[ProductController::class, 'show']);

// Authenticated routes
Route::middleware('auth:sanctum')->group(function () {
    Route::get('/users/me',         [AuthController::class, 'me']);

    Route::get('/orders',           [OrderController::class, 'index']);  // N+1 query pattern
    Route::get('/orders/{id}',      [OrderController::class, 'show']);
    Route::post('/orders',          [OrderController::class, 'store']);

    Route::get('/cart',             [CartController::class, 'index']);
    Route::post('/cart/items',      [CartController::class, 'addItem']);
    Route::delete('/cart',          [CartController::class, 'clear']);

    Route::get('/stats/summary',    [StatsController::class, 'summary']); // heavy aggregation, no cache
});
""",

    "app/Http/Controllers/AuthController.php": """\
<?php
namespace App\\Http\\Controllers;

use App\\Models\\User;
use Illuminate\\Http\\Request;
use Illuminate\\Support\\Facades\\Hash;

/**
 * Handles login / logout / current-user.
 *
 * PERFORMANCE NOTE: Session token lookup uses a linear scan over the
 * personal_access_tokens table – there is no compound index on
 * (tokenable_id, token) for the revoked=false filter. Under load this
 * causes full table scans for every authenticated request.
 */
class AuthController extends Controller
{
    public function login(Request $request)
    {
        $request->validate(['username' => 'required', 'password' => 'required']);

        // BCrypt verification – intentionally not cached
        $user = User::where('username', $request->username)->first();
        if (!$user || !Hash::check($request->password, $user->password)) {
            return response()->json(['error' => 'Invalid credentials'], 401);
        }

        // New token on every login – token table grows unboundedly
        $token = $user->createToken('api-token')->plainTextToken;

        return response()->json([
            'token'      => $token,
            'token_type' => 'Bearer',
            'expires_in' => 3600,
            'user'       => $user->only(['id', 'username', 'email', 'display_name', 'role']),
        ]);
    }

    public function logout(Request $request)
    {
        $request->user()->currentAccessToken()->delete();
        return response()->json(['message' => 'Logged out successfully']);
    }

    public function me(Request $request)
    {
        return response()->json($request->user()->only(['id', 'username', 'email', 'display_name', 'role']));
    }
}
""",

    "app/Http/Controllers/ProductController.php": """\
<?php
namespace App\\Http\\Controllers;

use App\\Models\\Product;
use App\\Models\\Category;
use Illuminate\\Http\\Request;

/**
 * Product catalogue endpoints.
 *
 * BOTTLENECK: The index() method performs no pagination by default.
 * With 180+ active products the ORM hydrates all rows into memory and
 * serialises the full payload on every request. This is the single
 * biggest contributor to p95 latency on the /api/products endpoint.
 *
 * Fix: always require ?page=N&per_page=M, default to per_page=20 when omitted.
 */
class ProductController extends Controller
{
    public function categories()
    {
        return response()->json([
            'data'  => Category::all(),
            'total' => Category::count(),
        ]);
    }

    public function index(Request $request)
    {
        $query = Product::where('is_active', true);

        if ($request->filled('category')) {
            $query->whereHas('category', fn($q) => $q->where('name', $request->category));
        }
        if ($request->filled('q')) {
            $query->where(fn($q) => $q
                ->where('name', 'like', '%'.$request->q.'%')
                ->orWhere('description', 'like', '%'.$request->q.'%'));
        }

        // ⚠ No pagination guard – returns ALL matching rows
        if (!$request->filled('page') || !$request->filled('per_page')) {
            // Full table load – simulates missing LIMIT/OFFSET in legacy code
            $products = $query->get();
            return response()->json([
                'data'      => $products,
                'total'     => $products->count(),
                'paginated' => false,
                'warning'   => 'No pagination applied – this endpoint is slow for large catalogues.',
            ]);
        }

        $perPage  = (int) $request->per_page;
        $paginated = $query->paginate($perPage);
        return response()->json([
            'data'        => $paginated->items(),
            'total'       => $paginated->total(),
            'page'        => $paginated->currentPage(),
            'per_page'    => $perPage,
            'total_pages' => $paginated->lastPage(),
            'paginated'   => true,
        ]);
    }

    public function show($id)
    {
        $product = Product::findOrFail($id);
        return response()->json($product);
    }
}
""",

    "app/Http/Controllers/OrderController.php": """\
<?php
namespace App\\Http\\Controllers;

use App\\Models\\Order;
use App\\Models\\OrderItem;
use App\\Models\\Product;
use Illuminate\\Http\\Request;

/**
 * Order management.
 *
 * BOTTLENECK (N+1 Query Pattern): index() fetches the order list and then
 * calls $order->items for every single order inside the loop. Each access
 * fires a separate SELECT against order_items. With 20 orders per page this
 * results in 21 DB queries (1 list + 20 item lookups) instead of 2.
 *
 * Fix: eager-load with Order::with('items')->paginate(…)
 */
class OrderController extends Controller
{
    public function index(Request $request)
    {
        $perPage = $request->integer('per_page', 20);

        // Fetch order headers only – items are NOT eager-loaded
        $paginated = Order::where('user_id', auth()->id())
            ->orderByDesc('created_at')
            ->paginate($perPage);

        $result = [];
        foreach ($paginated->items() as $order) {
            // ⚠ N+1: each access triggers SELECT * FROM order_items WHERE order_id = ?
            $itemCount = $order->items->count();  // lazy load per order
            $result[] = [
                'id'         => $order->id,
                'status'     => $order->status,
                'item_count' => $itemCount,
                'total'      => $order->total,
                'created_at' => $order->created_at,
            ];
        }

        return response()->json([
            'data'        => $result,
            'total'       => $paginated->total(),
            'page'        => $paginated->currentPage(),
            'per_page'    => $perPage,
            'total_pages' => $paginated->lastPage(),
        ]);
    }

    public function show(Request $request, $id)
    {
        $order = Order::with('items')->findOrFail($id);
        if ($order->user_id !== auth()->id() && !auth()->user()->isAdmin()) {
            return response()->json(['error' => 'Forbidden'], 403);
        }
        return response()->json($order);
    }

    public function store(Request $request)
    {
        $request->validate(['items' => 'required|array|min:1']);

        $total = 0;
        $items = [];
        foreach ($request->items as $item) {
            $product  = Product::findOrFail($item['product_id']);
            $qty      = max(1, (int)($item['quantity'] ?? 1));
            $subtotal = round($product->price * $qty, 2);
            $items[]  = compact('product', 'qty', 'subtotal');
            $total   += $subtotal;
        }

        $order = Order::create([
            'user_id'          => auth()->id(),
            'status'           => 'PENDING',
            'total'            => round($total, 2),
            'shipping_address' => $request->shipping_address ?? [],
        ]);

        foreach ($items as ['product' => $p, 'qty' => $q, 'subtotal' => $s]) {
            $order->items()->create([
                'product_id'   => $p->id,
                'product_name' => $p->name,
                'quantity'     => $q,
                'unit_price'   => $p->price,
                'subtotal'     => $s,
            ]);
        }

        return response()->json($order->load('items'), 201);
    }
}
""",

    "app/Http/Controllers/CartController.php": """\
<?php
namespace App\\Http\\Controllers;

use Illuminate\\Http\\Request;
use Illuminate\\Support\\Facades\\Cache;
use App\\Models\\Product;

/**
 * In-memory cart backed by the Laravel cache (Redis/Memcached).
 * Each cart operation serialises/deserialises the full cart array –
 * no partial updates possible.
 */
class CartController extends Controller
{
    private function cartKey(): string
    {
        return 'cart:' . auth()->id();
    }

    public function index()
    {
        $cart  = Cache::get($this->cartKey(), []);
        $total = array_sum(array_column($cart, 'subtotal'));
        return response()->json([
            'items'      => array_values($cart),
            'item_count' => count($cart),
            'total'      => round($total, 2),
        ]);
    }

    public function addItem(Request $request)
    {
        $request->validate(['product_id' => 'required|integer', 'quantity' => 'integer|min:1']);
        $product = Product::findOrFail($request->product_id);
        $cart    = Cache::get($this->cartKey(), []);

        $pid = $product->id;
        if (isset($cart[$pid])) {
            $cart[$pid]['quantity'] += $request->integer('quantity', 1);
            $cart[$pid]['subtotal']  = round($cart[$pid]['quantity'] * $cart[$pid]['unit_price'], 2);
        } else {
            $qty        = $request->integer('quantity', 1);
            $cart[$pid] = [
                'product_id'   => $pid,
                'product_name' => $product->name,
                'quantity'     => $qty,
                'unit_price'   => $product->price,
                'subtotal'     => round($qty * $product->price, 2),
            ];
        }

        Cache::put($this->cartKey(), $cart, now()->addHours(24));
        return response()->json(['items' => array_values($cart), 'item_count' => count($cart)], 201);
    }

    public function clear()
    {
        Cache::forget($this->cartKey());
        return response()->json(['message' => 'Cart cleared']);
    }
}
""",

    "app/Http/Controllers/StatsController.php": """\
<?php
namespace App\\Http\\Controllers;

use App\\Models\\Order;
use App\\Models\\OrderItem;
use App\\Models\\Product;
use Illuminate\\Http\\Request;

/**
 * Reporting / analytics endpoint.
 *
 * BOTTLENECK (Missing Cache + Heavy Aggregation):
 * summary() runs three expensive aggregate queries on every HTTP request.
 * There is no caching layer – each call performs full table scans on the
 * orders and order_items tables. Under load this saturates the DB connection
 * pool within seconds.
 *
 * Fix: wrap the response in Cache::remember('stats:summary', 300, fn() => …)
 * so the expensive queries run at most once every 5 minutes.
 */
class StatsController extends Controller
{
    public function summary()
    {
        // ⚠ No cache – full aggregation on every request
        $totalOrders  = Order::count();
        $totalRevenue = Order::sum('total');
        $byStatus     = Order::selectRaw('status, COUNT(*) as count')
            ->groupBy('status')
            ->pluck('count', 'status');

        // ⚠ Another full scan: joins order_items to find top products
        $topProducts = OrderItem::selectRaw('product_id, product_name, SUM(quantity) as units_sold')
            ->groupBy('product_id', 'product_name')
            ->orderByDesc('units_sold')
            ->limit(5)
            ->get();

        return response()->json([
            'total_orders'    => $totalOrders,
            'total_revenue'   => round($totalRevenue, 2),
            'orders_by_status'=> $byStatus,
            'top_products'    => $topProducts,
            'active_products' => Product::where('is_active', true)->count(),
            'total_categories'=> \\App\\Models\\Category::count(),
            'cached'          => false,
            'note'            => 'This report runs a full aggregation on every request (no cache).',
        ]);
    }
}
""",

    "app/Models/Product.php": """\
<?php
namespace App\\Models;

use Illuminate\\Database\\Eloquent\\Model;

/**
 * Product model.
 * Table: products
 *
 * Missing index: (is_active, price) – causes slow filtered catalogue queries.
 */
class Product extends Model
{
    protected $fillable = [
        'name', 'sku', 'category_id', 'price', 'stock',
        'is_active', 'rating', 'review_count', 'description', 'image_url',
    ];

    protected $casts = [
        'price'        => 'float',
        'is_active'    => 'boolean',
        'rating'       => 'float',
        'review_count' => 'integer',
        'stock'        => 'integer',
    ];

    public function category()
    {
        return $this->belongsTo(Category::class);
    }
}
""",

    "app/Models/Order.php": """\
<?php
namespace App\\Models;

use Illuminate\\Database\\Eloquent\\Model;

/**
 * Order model.
 * Table: orders
 */
class Order extends Model
{
    protected $fillable = ['user_id', 'status', 'total', 'shipping_address'];

    protected $casts = [
        'total'            => 'float',
        'shipping_address' => 'array',
    ];

    /**
     * Relationship intentionally NOT eager-loaded in OrderController::index().
     * This causes the N+1 query problem when listing orders.
     */
    public function items()
    {
        return $this->hasMany(OrderItem::class);
    }

    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
""",

    "database/migrations/2024_01_01_000001_create_products_table.php": """\
<?php
use Illuminate\\Database\\Migrations\\Migration;
use Illuminate\\Database\\Schema\\Blueprint;
use Illuminate\\Support\\Facades\\Schema;

/**
 * Products table migration.
 *
 * NOTE: No composite index on (is_active, price) – this causes full table
 * scans on the /api/products filtered queries.
 */
return new class extends Migration {
    public function up(): void
    {
        Schema::create('products', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('sku')->unique();
            $table->foreignId('category_id')->constrained();
            $table->decimal('price', 10, 2);
            $table->integer('stock')->default(0);
            $table->boolean('is_active')->default(true);
            $table->decimal('rating', 3, 1)->nullable();
            $table->integer('review_count')->default(0);
            $table->text('description')->nullable();
            $table->string('image_url')->nullable();
            $table->timestamps();

            // Missing: $table->index(['is_active', 'price']);   ← intentional bottleneck
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('products');
    }
};
""",

    "config/api.php": """\
<?php
// API configuration
return [
    // Base URL used in tests and documentation
    'base_url' => env('API_BASE_URL', 'http://localhost:8085'),

    // Pagination defaults (currently not enforced in ProductController)
    'default_per_page' => 20,
    'max_per_page'     => 100,

    // Cache TTLs (currently NOT applied in StatsController)
    'stats_cache_ttl' => 300,   // seconds
    'product_cache_ttl' => 60,
];
""",

    # ── Frontend: Twig templates, JS and CSS (pre-MR state) ──────────────────

    "resources/views/cart/cart.twig": """\
{# resources/views/cart/cart.twig #}
{% extends 'layout.twig' %}

{% block title %}Warenkorb{% endblock %}

{% block content %}
<div class="container cart-container">
  <h1>Dein Warenkorb</h1>

  {% if cart.items is empty %}
    <p class="cart-empty">Dein Warenkorb ist leer.</p>
  {% else %}
    <table class="cart-table">
      <thead>
        <tr>
          <th>Produkt</th>
          <th>Menge</th>
          <th>Einzelpreis</th>
          <th>Gesamt</th>
          <th></th>
        </tr>
      </thead>
      <tbody id="cart-items">
        {% for item in cart.items %}
        <tr data-product-id="{{ item.product_id }}">
          <td>{{ item.product_name }}</td>
          <td>
            <input type="number" class="qty-input" value="{{ item.quantity }}" min="1"
                   data-product-id="{{ item.product_id }}" />
          </td>
          <td>{{ item.unit_price|number_format(2, ',', '.') }} €</td>
          <td class="subtotal">{{ item.subtotal|number_format(2, ',', '.') }} €</td>
          <td>
            <button class="btn-remove" data-product-id="{{ item.product_id }}">Entfernen</button>
          </td>
        </tr>
        {% endfor %}
      </tbody>
    </table>

    <div class="cart-summary">
      <span class="cart-total-label">Gesamtbetrag:</span>
      <span class="cart-total" id="cart-total">{{ cart.total|number_format(2, ',', '.') }} €</span>
    </div>

    <div class="cart-actions">
      <a href="/checkout" class="btn btn-primary">Zur Kasse</a>
      <button id="clear-cart" class="btn btn-secondary">Warenkorb leeren</button>
    </div>
  {% endif %}
</div>
{% endblock %}

{% block scripts %}
<script src="/js/cart.js"></script>
{% endblock %}
""",

    "resources/views/promo/legacy-promo-block.twig": """\
{# resources/views/promo/legacy-promo-block.twig                          #}
{# DEPRECATED – This partial was used by the old promotion campaign page. #}
{# It is no longer embedded anywhere and will be removed in the next MR.  #}
{% block legacy_promo %}
<div class="legacy-promo-banner" style="background:#ffeb3b;padding:16px;text-align:center;">
  <strong>🎉 Sonderaktion!</strong> Nutze Code <code>SUMMER20</code> für 20% Rabatt.
  <br><small>Aktion gültig bis 31.08.2024 – nicht kombinierbar mit anderen Angeboten.</small>
</div>
{% endblock %}
""",

    "resources/js/cart.js": """\
/**
 * cart.js – Warenkorb-Interaktionen
 * Handles quantity updates and cart clearing via fetch API.
 */
document.addEventListener('DOMContentLoaded', function () {
  const API = '/api';

  // Update quantity
  document.querySelectorAll('.qty-input').forEach(function (input) {
    input.addEventListener('change', async function () {
      const productId = this.dataset.productId;
      const quantity  = parseInt(this.value, 10);
      if (quantity < 1) return;
      await fetch(`${API}/cart/items`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ product_id: productId, quantity }),
      });
      location.reload();
    });
  });

  // Remove item
  document.querySelectorAll('.btn-remove').forEach(function (btn) {
    btn.addEventListener('click', async function () {
      const productId = this.dataset.productId;
      await fetch(`${API}/cart/items/${productId}`, { method: 'DELETE' });
      location.reload();
    });
  });

  // Clear cart
  const clearBtn = document.getElementById('clear-cart');
  if (clearBtn) {
    clearBtn.addEventListener('click', async function () {
      await fetch(`${API}/cart`, { method: 'DELETE' });
      location.reload();
    });
  }
});
""",

    "resources/css/cart.css": """\
/* cart.css – Warenkorb-Styles */

.cart-container {
  max-width: 900px;
  margin: 2rem auto;
  padding: 0 1rem;
}

.cart-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1.5rem;
}

.cart-table th,
.cart-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e0e0e0;
  text-align: left;
}

.cart-table th {
  background: #f5f5f5;
  font-weight: 600;
}

.qty-input {
  width: 60px;
  padding: 4px 6px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.btn-remove {
  background: none;
  border: 1px solid #e53935;
  color: #e53935;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
}

.btn-remove:hover {
  background: #e53935;
  color: #fff;
}

.cart-summary {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 1rem;
  font-size: 1.1rem;
  margin-bottom: 1rem;
}

.cart-total {
  font-weight: 700;
  color: #333;
}

.cart-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
}

.cart-empty {
  color: #888;
  text-align: center;
  padding: 3rem 0;
}
""",

    "resources/css/legacy-promo.css": """\
/* legacy-promo.css – Styles for the old promotion banner (DEPRECATED) */

.legacy-promo-banner {
  background-color: #ffeb3b;
  border: 1px solid #f9a825;
  border-radius: 4px;
  padding: 16px 24px;
  text-align: center;
  font-size: 0.95rem;
  color: #333;
  margin-bottom: 1rem;
}

.legacy-promo-banner code {
  background: rgba(0,0,0,0.08);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
}
""",

    # ── Docker compose file (pre-MR state: has mailer-legacy, no redis-queue) ─

    "docker-compose.yml": """\
version: '3.9'

services:
  app:
    build: .
    ports:
      - "8085:8080"
    environment:
      APP_ENV: production
      DB_HOST: mysql
      DB_DATABASE: ecommerce
      DB_USERNAME: shop
      DB_PASSWORD: secret
      REDIS_HOST: redis
      REDIS_PORT: 6379
    depends_on:
      - mysql
      - redis
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootsecret
      MYSQL_DATABASE: ecommerce
      MYSQL_USER: shop
      MYSQL_PASSWORD: secret
    volumes:
      - mysql_data:/var/lib/mysql
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    restart: unless-stopped

  mailer-legacy:
    image: schickling/mailcatcher:latest
    ports:
      - "1080:1080"
      - "1025:1025"
    restart: unless-stopped
    # TODO: replace with modern mail service – this image is unmaintained

volumes:
  mysql_data:
""",

    # ── Models to be added/deleted/modified in the MR ─────────────────────────

    "app/Http/Controllers/LegacyPromoController.php": """\
<?php
namespace App\\Http\\Controllers;

use Illuminate\\Http\\Request;

/**
 * LegacyPromoController – handles old coupon_code query param flow.
 *
 * DEPRECATED: This controller implements the old promo-code flow that
 * relied on GET parameter injection. It is superseded by DiscountController.
 * Scheduled for removal in ECOM-42.
 */
class LegacyPromoController extends Controller
{
    public function apply(Request $request)
    {
        // Security issue: coupon code taken directly from GET param without validation
        $code = $request->query('coupon_code', '');
        if (empty($code)) {
            return response()->json(['error' => 'No code provided'], 400);
        }

        // Hard-coded legacy codes – no DB lookup
        $legacyCodes = ['SUMMER20' => 0.20, 'WINTER10' => 0.10, 'VIP30' => 0.30];
        if (!array_key_exists($code, $legacyCodes)) {
            return response()->json(['error' => 'Invalid code'], 400);
        }

        $discount = $legacyCodes[$code];
        return response()->json([
            'code'          => $code,
            'discount_rate' => $discount,
            'message'       => 'Legacy promo applied (deprecated flow)',
        ]);
    }
}
""",

    "app/Models/LegacyPromo.php": """\
<?php
namespace App\\Models;

use Illuminate\\Database\\Eloquent\\Model;

/**
 * LegacyPromo – DB-backed legacy promo entries (read-only, migration target).
 * DEPRECATED: replaced by the Discount model introduced in ECOM-42.
 */
class LegacyPromo extends Model
{
    protected $table = 'legacy_promos';

    protected $fillable = ['code', 'discount_percent', 'valid_until', 'active'];

    protected $casts = [
        'discount_percent' => 'float',
        'valid_until'      => 'datetime',
        'active'           => 'boolean',
    ];
}
""",
}


# ── Merge Requests ────────────────────────────────────────────────────────────
# Format mirrors GitLab MR API response.

MERGE_REQUESTS = {
    1: [  # backend-api
        {
            "id": 101, "iid": 1,
            "title": "fix: JWT token refresh not triggered on 401 response",
            "description": "Fixes authentication issue where expired JWT tokens caused 500 instead of triggering refresh. Related: BACKEND-1",
            "state": "merged",
            "source_branch": "fix/jwt-refresh-BACKEND-1",
            "target_branch": "develop",
            "web_url": "http://localhost:3080/demo-group/backend-api/-/merge_requests/1",
            "author": {"name": "Anna Bauer"},
            "merged_at": "2025-03-10T14:23:00Z",
            "changes": [
                {"old_path": "src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt",
                 "new_path": "src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt",
                 "diff": "@@ -42,7 +42,12 @@ class JwtAuthenticationFilter {\n-    if (error.response?.status === 401) return;\n+    if (error.response?.status === 401) {\n+      const newToken = await authService.refreshToken();\n+      return retryRequest(original, newToken);\n+    }"}
            ],
        },
        {
            "id": 102, "iid": 2,
            "title": "feat: add Redis caching for user profile endpoint",
            "description": "Implements @Cacheable on GET /api/v1/users/{id} with 5-minute TTL. Reduces database load. Related: BACKEND-8",
            "state": "merged",
            "source_branch": "feature/user-cache-BACKEND-8",
            "target_branch": "develop",
            "web_url": "http://localhost:3080/demo-group/backend-api/-/merge_requests/2",
            "author": {"name": "Anna Bauer"},
            "merged_at": "2025-03-15T09:00:00Z",
            "changes": [],
        },
        {
            "id": 103, "iid": 3,
            "title": "feat: payment service Stripe integration",
            "description": "Integrates Stripe payment gateway for order payment processing. Adds PaymentService and PaymentController.",
            "state": "open",
            "source_branch": "feature/stripe-payment",
            "target_branch": "develop",
            "web_url": "http://localhost:3080/demo-group/backend-api/-/merge_requests/3",
            "author": {"name": "Max Müller"},
            "changes": [],
        },
    ],
    3: [  # auth-service
        {
            "id": 301, "iid": 1,
            "title": "feat: implement Refresh Token Rotation (RTR)",
            "description": "Adds refresh token rotation: each use of a refresh token issues a new one and revokes the old. Prevents token replay attacks. BACKEND-2",
            "state": "merged",
            "source_branch": "feature/rtr-auth",
            "target_branch": "main",
            "web_url": "http://localhost:3080/demo-group/auth-service/-/merge_requests/1",
            "author": {"name": "Max Müller"},
            "merged_at": "2025-02-20T11:00:00Z",
            "changes": [],
        },
        {
            "id": 302, "iid": 2,
            "title": "security: reduce JWT access token expiry from 60min to 15min",
            "description": "Security improvement: shorter-lived access tokens limit the damage window if a token is stolen. Refresh tokens are unaffected.",
            "state": "merged",
            "source_branch": "security/shorter-jwt-expiry",
            "target_branch": "main",
            "web_url": "http://localhost:3080/demo-group/auth-service/-/merge_requests/2",
            "author": {"name": "Felix Weber"},
            "merged_at": "2025-03-01T16:30:00Z",
            "changes": [],
        },
    ],
    4: [  # devops-infra
        {
            "id": 401, "iid": 1,
            "title": "feat: add cert-manager for automatic SSL certificate renewal",
            "description": "Replaces manual Let's Encrypt renewal with cert-manager ClusterIssuer. OPS-5",
            "state": "merged",
            "source_branch": "feature/cert-manager-OPS-5",
            "target_branch": "main",
            "web_url": "http://localhost:3080/demo-group/devops-infra/-/merge_requests/1",
            "author": {"name": "Maria Schulz"},
            "merged_at": "2025-03-18T08:45:00Z",
            "changes": [],
        },
        {
            "id": 402, "iid": 2,
            "title": "infra: update EKS to 1.29 and enable encryption at rest for RDS",
            "description": "Kubernetes and RDS upgrades as part of Q1 compliance work. INFRA-3",
            "state": "open",
            "source_branch": "infra/eks-1.29-rds-encryption",
            "target_branch": "main",
            "web_url": "http://localhost:3080/demo-group/devops-infra/-/merge_requests/2",
            "author": {"name": "Stefan Wolf"},
            "changes": [],
        },
    ],
    7: [  # ecommerce-api – ECOM-42: Wunschliste + Gutschein-System
        {
            "id": 701, "iid": 1,
            "title": "feat: ECOM-42 Wunschliste + Gutschein-System für Checkout",
            "description": (
                "## Zusammenfassung\n\n"
                "Dieses MR implementiert ECOM-42 (Wunschliste & Gutschein-System für Checkout).\n\n"
                "### Änderungen\n\n"
                "**PHP (Backend)**\n"
                "- ➕ `WishlistController` – CRUD für benutzerspezifische Wunschlisten\n"
                "- ➕ `DiscountController` – Gutschein-Validierung und -Einlösung\n"
                "- ➕ `Wishlist`-Model + Migration\n"
                "- ➕ `Discount`-Model + Migration\n"
                "- 🔧 `CartController` – Gutscheincode-Anwendung hinzugefügt\n"
                "- 🔧 `StatsController` – Redis-Cache + Gutschein-Statistik\n"
                "- 🔧 `Order`-Model – Feld `coupon_code` + `discount_amount` ergänzt\n"
                "- ❌ `LegacyPromoController` – veraltet, entfernt\n"
                "- ❌ `LegacyPromo`-Model – veraltet, entfernt\n\n"
                "**Frontend (Twig + JS + CSS)**\n"
                "- ➕ `resources/views/wishlist/wishlist.twig` – neue Wunschlisten-Seite\n"
                "- ➕ `resources/js/wishlist.js` – Wunschlisten-Interaktionen\n"
                "- ➕ `resources/css/wishlist.css` – Styles für Wunschliste\n"
                "- 🔧 `resources/views/cart/cart.twig` – Gutschein-Eingabefeld ergänzt\n"
                "- 🔧 `resources/js/cart.js` – Gutschein-Logik im Frontend\n"
                "- 🔧 `resources/css/cart.css` – Styles für Gutschein-Bereich\n"
                "- ❌ `resources/views/promo/legacy-promo-block.twig` – entfernt\n"
                "- ❌ `resources/css/legacy-promo.css` – entfernt\n\n"
                "**Docker / Infrastruktur**\n"
                "- ➕ `docker-compose.yml`: Service `redis-queue` (async job processing) hinzugefügt\n"
                "- ❌ `docker-compose.yml`: Service `mailer-legacy` entfernt (durch modernes Mailservice ersetzt)\n"
                "- ➕ `docker/redis-queue/Dockerfile` – neuer Redis-Queue-Worker\n\n"
                "Jira: ECOM-42"
            ),
            "state": "open",
            "source_branch": "feature/ECOM-42-wishlist-discounts",
            "target_branch": "main",
            "web_url": "http://localhost:3080/demo-group/ecommerce-api/-/merge_requests/1",
            "author": {"name": "Sophie Wagner"},
            "diff_refs": {
                "base_sha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                "start_sha": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                "head_sha": "f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5",
            },
            "changes": [
                # ── PHP: DELETED LegacyPromoController ───────────────────────
                {
                    "old_path": "app/Http/Controllers/LegacyPromoController.php",
                    "new_path": "app/Http/Controllers/LegacyPromoController.php",
                    "deleted": True,
                    "new_file": False,
                    "diff": (
                        "@@ -1,45 +0,0 @@\n"
                        "-<?php\n"
                        "-namespace App\\Http\\Controllers;\n"
                        "-\n"
                        "-use Illuminate\\Http\\Request;\n"
                        "-\n"
                        "-/**\n"
                        "- * LegacyPromoController – handles old coupon_code query param flow.\n"
                        "- * DEPRECATED: superseded by DiscountController.\n"
                        "- */\n"
                        "-class LegacyPromoController extends Controller\n"
                        "-{\n"
                        "-    public function apply(Request $request)\n"
                        "-    {\n"
                        "-        $code = $request->query('coupon_code', '');\n"
                        "-        if (empty($code)) {\n"
                        "-            return response()->json(['error' => 'No code provided'], 400);\n"
                        "-        }\n"
                        "-        $legacyCodes = ['SUMMER20' => 0.20, 'WINTER10' => 0.10, 'VIP30' => 0.30];\n"
                        "-        if (!array_key_exists($code, $legacyCodes)) {\n"
                        "-            return response()->json(['error' => 'Invalid code'], 400);\n"
                        "-        }\n"
                        "-        $discount = $legacyCodes[$code];\n"
                        "-        return response()->json([\n"
                        "-            'code'          => $code,\n"
                        "-            'discount_rate' => $discount,\n"
                        "-            'message'       => 'Legacy promo applied (deprecated flow)',\n"
                        "-        ]);\n"
                        "-    }\n"
                        "-}\n"
                    ),
                },
                # ── PHP: DELETED LegacyPromo Model ───────────────────────────
                {
                    "old_path": "app/Models/LegacyPromo.php",
                    "new_path": "app/Models/LegacyPromo.php",
                    "deleted": True,
                    "new_file": False,
                    "diff": (
                        "@@ -1,20 +0,0 @@\n"
                        "-<?php\n"
                        "-namespace App\\Models;\n"
                        "-\n"
                        "-use Illuminate\\Database\\Eloquent\\Model;\n"
                        "-\n"
                        "-/**\n"
                        "- * LegacyPromo – DEPRECATED, replaced by Discount model.\n"
                        "- */\n"
                        "-class LegacyPromo extends Model\n"
                        "-{\n"
                        "-    protected $table = 'legacy_promos';\n"
                        "-    protected $fillable = ['code', 'discount_percent', 'valid_until', 'active'];\n"
                        "-    protected $casts = [\n"
                        "-        'discount_percent' => 'float',\n"
                        "-        'valid_until'      => 'datetime',\n"
                        "-        'active'           => 'boolean',\n"
                        "-    ];\n"
                        "-}\n"
                    ),
                },
                # ── PHP: ADDED WishlistController ─────────────────────────────
                {
                    "old_path": "app/Http/Controllers/WishlistController.php",
                    "new_path": "app/Http/Controllers/WishlistController.php",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,68 @@\n"
                        "+<?php\n"
                        "+namespace App\\Http\\Controllers;\n"
                        "+\n"
                        "+use App\\Models\\Wishlist;\n"
                        "+use App\\Models\\Product;\n"
                        "+use Illuminate\\Http\\Request;\n"
                        "+\n"
                        "+/**\n"
                        "+ * WishlistController – manages per-user product wish lists.\n"
                        "+ *\n"
                        "+ * Implements AK1 and AK2 from ECOM-42:\n"
                        "+ * - AK1: Users can add/remove products from their wish list.\n"
                        "+ * - AK2: Wish list is persisted in the database and survives browser restarts.\n"
                        "+ */\n"
                        "+class WishlistController extends Controller\n"
                        "+{\n"
                        "+    public function index(Request $request)\n"
                        "+    {\n"
                        "+        $items = Wishlist::with('product')\n"
                        "+            ->where('user_id', auth()->id())\n"
                        "+            ->orderByDesc('created_at')\n"
                        "+            ->get();\n"
                        "+\n"
                        "+        return response()->json([\n"
                        "+            'data'  => $items,\n"
                        "+            'count' => $items->count(),\n"
                        "+        ]);\n"
                        "+    }\n"
                        "+\n"
                        "+    public function add(Request $request)\n"
                        "+    {\n"
                        "+        $request->validate(['product_id' => 'required|integer|exists:products,id']);\n"
                        "+\n"
                        "+        $entry = Wishlist::firstOrCreate([\n"
                        "+            'user_id'    => auth()->id(),\n"
                        "+            'product_id' => $request->product_id,\n"
                        "+        ]);\n"
                        "+\n"
                        "+        return response()->json($entry, $entry->wasRecentlyCreated ? 201 : 200);\n"
                        "+    }\n"
                        "+\n"
                        "+    public function remove(Request $request, int $productId)\n"
                        "+    {\n"
                        "+        Wishlist::where('user_id', auth()->id())\n"
                        "+               ->where('product_id', $productId)\n"
                        "+               ->delete();\n"
                        "+\n"
                        "+        return response()->json(['message' => 'Removed from wish list']);\n"
                        "+    }\n"
                        "+\n"
                        "+    public function move_to_cart(Request $request, int $productId)\n"
                        "+    {\n"
                        "+        $product = Product::findOrFail($productId);\n"
                        "+        // Delegate to CartController logic\n"
                        "+        app(CartController::class)->addItem(\n"
                        "+            $request->merge(['product_id' => $productId, 'quantity' => 1])\n"
                        "+        );\n"
                        "+        $this->remove($request, $productId);\n"
                        "+        return response()->json(['message' => 'Moved to cart']);\n"
                        "+    }\n"
                        "+}\n"
                    ),
                },
                # ── PHP: ADDED DiscountController ─────────────────────────────
                {
                    "old_path": "app/Http/Controllers/DiscountController.php",
                    "new_path": "app/Http/Controllers/DiscountController.php",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,72 @@\n"
                        "+<?php\n"
                        "+namespace App\\Http\\Controllers;\n"
                        "+\n"
                        "+use App\\Models\\Discount;\n"
                        "+use Illuminate\\Http\\Request;\n"
                        "+use Illuminate\\Support\\Facades\\Cache;\n"
                        "+\n"
                        "+/**\n"
                        "+ * DiscountController – validates and applies coupon codes.\n"
                        "+ *\n"
                        "+ * Implements AK3 and AK4 from ECOM-42:\n"
                        "+ * - AK3: User can enter a coupon code at checkout; valid codes reduce cart total.\n"
                        "+ * - AK4: Invalid/expired codes return a clear error message.\n"
                        "+ */\n"
                        "+class DiscountController extends Controller\n"
                        "+{\n"
                        "+    /**\n"
                        "+     * Validate a coupon code and return discount details.\n"
                        "+     * Does NOT redeem the code – redemption happens during order creation.\n"
                        "+     */\n"
                        "+    public function validate(Request $request)\n"
                        "+    {\n"
                        "+        $request->validate(['code' => 'required|string|max:50']);\n"
                        "+        $code = strtoupper(trim($request->code));\n"
                        "+\n"
                        "+        $discount = Discount::where('code', $code)\n"
                        "+            ->where('active', true)\n"
                        "+            ->where(fn($q) => $q->whereNull('valid_until')\n"
                        "+                                ->orWhere('valid_until', '>=', now()))\n"
                        "+            ->where(fn($q) => $q->whereNull('max_uses')\n"
                        "+                                ->orWhereColumn('used_count', '<', 'max_uses'))\n"
                        "+            ->first();\n"
                        "+\n"
                        "+        if (!$discount) {\n"
                        "+            return response()->json([\n"
                        "+                'valid'   => false,\n"
                        "+                'message' => 'Ungültiger oder abgelaufener Gutscheincode.',\n"
                        "+            ], 422);\n"
                        "+        }\n"
                        "+\n"
                        "+        return response()->json([\n"
                        "+            'valid'         => true,\n"
                        "+            'code'          => $discount->code,\n"
                        "+            'discount_type' => $discount->type,\n"
                        "+            'discount_value'=> $discount->value,\n"
                        "+            'description'   => $discount->description,\n"
                        "+        ]);\n"
                        "+    }\n"
                        "+\n"
                        "+    /** Redeem a code and increment its usage counter. */\n"
                        "+    public function redeem(Request $request)\n"
                        "+    {\n"
                        "+        $request->validate(['code' => 'required|string|max:50', 'order_id' => 'required|integer']);\n"
                        "+        $code = strtoupper(trim($request->code));\n"
                        "+\n"
                        "+        $discount = Discount::where('code', $code)->where('active', true)->firstOrFail();\n"
                        "+        $discount->increment('used_count');\n"
                        "+\n"
                        "+        // Invalidate stats cache so the dashboard reflects the new redemption\n"
                        "+        Cache::forget('stats:summary');\n"
                        "+\n"
                        "+        return response()->json(['message' => 'Code eingelöst', 'discount_id' => $discount->id]);\n"
                        "+    }\n"
                        "+}\n"
                    ),
                },
                # ── PHP: MODIFIED CartController ──────────────────────────────
                {
                    "old_path": "app/Http/Controllers/CartController.php",
                    "new_path": "app/Http/Controllers/CartController.php",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -1,6 +1,7 @@\n"
                        " <?php\n"
                        " namespace App\\Http\\Controllers;\n"
                        " \n"
                        "+use App\\Models\\Discount;\n"
                        " use Illuminate\\Http\\Request;\n"
                        " use Illuminate\\Support\\Facades\\Cache;\n"
                        " use App\\Models\\Product;\n"
                        "@@ -50,4 +51,40 @@\n"
                        "     public function clear()\n"
                        "     {\n"
                        "         Cache::forget($this->cartKey());\n"
                        "         return response()->json(['message' => 'Cart cleared']);\n"
                        "     }\n"
                        "+\n"
                        "+    /**\n"
                        "+     * Apply a coupon code to the current cart.\n"
                        "+     * Implements AK3 and AK5 from ECOM-42.\n"
                        "+     */\n"
                        "+    public function applyCoupon(Request $request)\n"
                        "+    {\n"
                        "+        $request->validate(['code' => 'required|string|max:50']);\n"
                        "+        $code = strtoupper(trim($request->code));\n"
                        "+\n"
                        "+        $discount = Discount::where('code', $code)\n"
                        "+            ->where('active', true)\n"
                        "+            ->where(fn($q) => $q->whereNull('valid_until')\n"
                        "+                                ->orWhere('valid_until', '>=', now()))\n"
                        "+            ->first();\n"
                        "+\n"
                        "+        if (!$discount) {\n"
                        "+            return response()->json(['error' => 'Ungültiger oder abgelaufener Gutscheincode.'], 422);\n"
                        "+        }\n"
                        "+\n"
                        "+        $cart  = Cache::get($this->cartKey(), []);\n"
                        "+        $total = array_sum(array_column($cart, 'subtotal'));\n"
                        "+\n"
                        "+        $discountAmount = $discount->type === 'percent'\n"
                        "+            ? round($total * $discount->value / 100, 2)\n"
                        "+            : min($discount->value, $total);\n"
                        "+\n"
                        "+        $couponKey = $this->cartKey() . ':coupon';\n"
                        "+        Cache::put($couponKey, [\n"
                        "+            'code'            => $code,\n"
                        "+            'discount_amount' => $discountAmount,\n"
                        "+            'discount_type'   => $discount->type,\n"
                        "+            'discount_value'  => $discount->value,\n"
                        "+        ], now()->addHours(24));\n"
                        "+\n"
                        "+        return response()->json([\n"
                        "+            'original_total'  => $total,\n"
                        "+            'discount_amount' => $discountAmount,\n"
                        "+            'final_total'     => round($total - $discountAmount, 2),\n"
                        "+            'code'            => $code,\n"
                        "+        ]);\n"
                        "+    }\n"
                        " }\n"
                    ),
                },
                # ── PHP: MODIFIED StatsController ─────────────────────────────
                {
                    "old_path": "app/Http/Controllers/StatsController.php",
                    "new_path": "app/Http/Controllers/StatsController.php",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -1,6 +1,7 @@\n"
                        " <?php\n"
                        " namespace App\\Http\\Controllers;\n"
                        " \n"
                        "+use App\\Models\\Discount;\n"
                        " use App\\Models\\Order;\n"
                        " use App\\Models\\OrderItem;\n"
                        " use App\\Models\\Product;\n"
                        "+use Illuminate\\Support\\Facades\\Cache;\n"
                        " use Illuminate\\Http\\Request;\n"
                        "@@ -20,23 +21,31 @@\n"
                        " class StatsController extends Controller\n"
                        " {\n"
                        "     public function summary()\n"
                        "     {\n"
                        "-        // ⚠ No cache – full aggregation on every request\n"
                        "-        $totalOrders  = Order::count();\n"
                        "-        $totalRevenue = Order::sum('total');\n"
                        "-        $byStatus     = Order::selectRaw('status, COUNT(*) as count')\n"
                        "-            ->groupBy('status')\n"
                        "-            ->pluck('count', 'status');\n"
                        "-\n"
                        "-        $topProducts = OrderItem::selectRaw('product_id, product_name, SUM(quantity) as units_sold')\n"
                        "-            ->groupBy('product_id', 'product_name')\n"
                        "-            ->orderByDesc('units_sold')\n"
                        "-            ->limit(5)\n"
                        "-            ->get();\n"
                        "+        // AK7: Redis-Cache mit 5-Minuten-TTL (ECOM-42)\n"
                        "+        return Cache::remember('stats:summary', 300, function () {\n"
                        "+            $totalOrders  = Order::count();\n"
                        "+            $totalRevenue = Order::sum('total');\n"
                        "+            $byStatus     = Order::selectRaw('status, COUNT(*) as count')\n"
                        "+                ->groupBy('status')\n"
                        "+                ->pluck('count', 'status');\n"
                        "+\n"
                        "+            $topProducts = OrderItem::selectRaw('product_id, product_name, SUM(quantity) as units_sold')\n"
                        "+                ->groupBy('product_id', 'product_name')\n"
                        "+                ->orderByDesc('units_sold')\n"
                        "+                ->limit(5)\n"
                        "+                ->get();\n"
                        "+\n"
                        "+            // AK6: Gutschein-Einlösungsstatistik (ECOM-42)\n"
                        "+            $redeemedCoupons = Discount::sum('used_count');\n"
                        " \n"
                        "-        return response()->json([\n"
                        "-            'total_orders'    => $totalOrders,\n"
                        "-            'total_revenue'   => round($totalRevenue, 2),\n"
                        "-            'orders_by_status'=> $byStatus,\n"
                        "-            'top_products'    => $topProducts,\n"
                        "-            'active_products' => Product::where('is_active', true)->count(),\n"
                        "-            'total_categories'=> \\App\\Models\\Category::count(),\n"
                        "-            'cached'          => false,\n"
                        "-            'note'            => 'This report runs a full aggregation on every request (no cache).',\n"
                        "-        ]);\n"
                        "+            return [\n"
                        "+                'total_orders'      => $totalOrders,\n"
                        "+                'total_revenue'     => round($totalRevenue, 2),\n"
                        "+                'orders_by_status'  => $byStatus,\n"
                        "+                'top_products'      => $topProducts,\n"
                        "+                'active_products'   => Product::where('is_active', true)->count(),\n"
                        "+                'total_categories'  => \\App\\Models\\Category::count(),\n"
                        "+                'redeemed_coupons'  => $redeemedCoupons,\n"
                        "+                'cached'            => true,\n"
                        "+                'cache_ttl_seconds' => 300,\n"
                        "+            ];\n"
                        "+        })->toArray();\n"
                        "     }\n"
                        " }\n"
                    ),
                },
                # ── PHP: MODIFIED Order Model ──────────────────────────────────
                {
                    "old_path": "app/Models/Order.php",
                    "new_path": "app/Models/Order.php",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -9,7 +9,7 @@\n"
                        " class Order extends Model\n"
                        " {\n"
                        "-    protected $fillable = ['user_id', 'status', 'total', 'shipping_address'];\n"
                        "+    protected $fillable = ['user_id', 'status', 'total', 'shipping_address', 'coupon_code', 'discount_amount'];\n"
                        " \n"
                        "     protected $casts = [\n"
                        "         'total'            => 'float',\n"
                        "+        'discount_amount'  => 'float',\n"
                        "         'shipping_address' => 'array',\n"
                        "     ];\n"
                    ),
                },
                # ── PHP: ADDED Wishlist Model ─────────────────────────────────
                {
                    "old_path": "app/Models/Wishlist.php",
                    "new_path": "app/Models/Wishlist.php",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,28 @@\n"
                        "+<?php\n"
                        "+namespace App\\Models;\n"
                        "+\n"
                        "+use Illuminate\\Database\\Eloquent\\Model;\n"
                        "+\n"
                        "+/**\n"
                        "+ * Wishlist – per-user product wish list entry.\n"
                        "+ * Implements AK1 + AK2 from ECOM-42.\n"
                        "+ */\n"
                        "+class Wishlist extends Model\n"
                        "+{\n"
                        "+    protected $fillable = ['user_id', 'product_id'];\n"
                        "+\n"
                        "+    // Unique constraint enforced at DB level (migration)\n"
                        "+\n"
                        "+    public function product()\n"
                        "+    {\n"
                        "+        return $this->belongsTo(Product::class);\n"
                        "+    }\n"
                        "+\n"
                        "+    public function user()\n"
                        "+    {\n"
                        "+        return $this->belongsTo(User::class);\n"
                        "+    }\n"
                        "+}\n"
                    ),
                },
                # ── PHP: ADDED Discount Model ─────────────────────────────────
                {
                    "old_path": "app/Models/Discount.php",
                    "new_path": "app/Models/Discount.php",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,34 @@\n"
                        "+<?php\n"
                        "+namespace App\\Models;\n"
                        "+\n"
                        "+use Illuminate\\Database\\Eloquent\\Model;\n"
                        "+\n"
                        "+/**\n"
                        "+ * Discount – coupon code model.\n"
                        "+ * Implements AK3 + AK4 from ECOM-42.\n"
                        "+ *\n"
                        "+ * type: 'percent' | 'fixed'\n"
                        "+ * value: percentage (0–100) or fixed EUR amount\n"
                        "+ */\n"
                        "+class Discount extends Model\n"
                        "+{\n"
                        "+    protected $fillable = [\n"
                        "+        'code', 'type', 'value', 'description',\n"
                        "+        'active', 'valid_until', 'max_uses', 'used_count',\n"
                        "+    ];\n"
                        "+\n"
                        "+    protected $casts = [\n"
                        "+        'value'       => 'float',\n"
                        "+        'active'      => 'boolean',\n"
                        "+        'valid_until' => 'datetime',\n"
                        "+        'max_uses'    => 'integer',\n"
                        "+        'used_count'  => 'integer',\n"
                        "+    ];\n"
                        "+}\n"
                    ),
                },
                # ── Twig: DELETED legacy-promo-block.twig ─────────────────────
                {
                    "old_path": "resources/views/promo/legacy-promo-block.twig",
                    "new_path": "resources/views/promo/legacy-promo-block.twig",
                    "deleted": True,
                    "new_file": False,
                    "diff": (
                        "@@ -1,10 +0,0 @@\n"
                        "-{# resources/views/promo/legacy-promo-block.twig – DEPRECATED #}\n"
                        "-{% block legacy_promo %}\n"
                        "-<div class=\"legacy-promo-banner\" style=\"background:#ffeb3b;padding:16px;text-align:center;\">\n"
                        "-  <strong>🎉 Sonderaktion!</strong> Nutze Code <code>SUMMER20</code> für 20% Rabatt.\n"
                        "-  <br><small>Aktion gültig bis 31.08.2024 – nicht kombinierbar mit anderen Angeboten.</small>\n"
                        "-</div>\n"
                        "-{% endblock %}\n"
                    ),
                },
                # ── Twig: ADDED wishlist.twig ──────────────────────────────────
                {
                    "old_path": "resources/views/wishlist/wishlist.twig",
                    "new_path": "resources/views/wishlist/wishlist.twig",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,55 @@\n"
                        "+{# resources/views/wishlist/wishlist.twig #}\n"
                        "+{# Implements AK1 + AK2 (ECOM-42): persistent wish list page #}\n"
                        "+{% extends 'layout.twig' %}\n"
                        "+\n"
                        "+{% block title %}Meine Wunschliste{% endblock %}\n"
                        "+\n"
                        "+{% block content %}\n"
                        "+<div class=\"container wishlist-container\">\n"
                        "+  <h1>Meine Wunschliste</h1>\n"
                        "+\n"
                        "+  {% if items is empty %}\n"
                        "+    <p class=\"wishlist-empty\">Deine Wunschliste ist leer.</p>\n"
                        "+  {% else %}\n"
                        "+    <ul class=\"wishlist-list\" id=\"wishlist-items\">\n"
                        "+      {% for item in items %}\n"
                        "+      <li class=\"wishlist-item\" data-product-id=\"{{ item.product_id }}\">\n"
                        "+        <div class=\"wishlist-thumb\">\n"
                        "+          <img src=\"{{ item.product.image_url }}\" alt=\"{{ item.product.name }}\" />\n"
                        "+        </div>\n"
                        "+        <div class=\"wishlist-info\">\n"
                        "+          <span class=\"wishlist-name\">{{ item.product.name }}</span>\n"
                        "+          <span class=\"wishlist-price\">{{ item.product.price|number_format(2, ',', '.') }} €</span>\n"
                        "+        </div>\n"
                        "+        <div class=\"wishlist-actions\">\n"
                        "+          <button class=\"btn-move-to-cart\" data-product-id=\"{{ item.product_id }}\"\n"
                        "+                  title=\"In den Warenkorb\">\n"
                        "+            <i class=\"icon-cart\"></i> In den Warenkorb\n"
                        "+          </button>\n"
                        "+          <button class=\"btn-remove-wish\" data-product-id=\"{{ item.product_id }}\"\n"
                        "+                  title=\"Aus Wunschliste entfernen\">\n"
                        "+            <i class=\"icon-trash\"></i>\n"
                        "+          </button>\n"
                        "+        </div>\n"
                        "+      </li>\n"
                        "+      {% endfor %}\n"
                        "+    </ul>\n"
                        "+  {% endif %}\n"
                        "+</div>\n"
                        "+{% endblock %}\n"
                        "+\n"
                        "+{% block scripts %}\n"
                        "+<script src=\"/js/wishlist.js\"></script>\n"
                        "+{% endblock %}\n"
                        "+\n"
                        "+{% block styles %}\n"
                        "+<link rel=\"stylesheet\" href=\"/css/wishlist.css\" />\n"
                        "+{% endblock %}\n"
                    ),
                },
                # ── Twig: MODIFIED cart.twig ───────────────────────────────────
                {
                    "old_path": "resources/views/cart/cart.twig",
                    "new_path": "resources/views/cart/cart.twig",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -45,6 +45,28 @@\n"
                        "     <div class=\"cart-summary\">\n"
                        "       <span class=\"cart-total-label\">Gesamtbetrag:</span>\n"
                        "       <span class=\"cart-total\" id=\"cart-total\">{{ cart.total|number_format(2, ',', '.') }} €</span>\n"
                        "     </div>\n"
                        "+\n"
                        "+    {# AK3 + AK5 (ECOM-42): Gutschein-Eingabe + angepasster Preis #}\n"
                        "+    <div class=\"coupon-section\" id=\"coupon-section\">\n"
                        "+      <h3>Gutscheincode einlösen</h3>\n"
                        "+      <div class=\"coupon-input-row\">\n"
                        "+        <input type=\"text\" id=\"coupon-input\" class=\"coupon-input\"\n"
                        "+               placeholder=\"Gutscheincode eingeben…\"\n"
                        "+               autocomplete=\"off\" maxlength=\"50\" />\n"
                        "+        <button id=\"coupon-apply-btn\" class=\"btn btn-secondary\">Einlösen</button>\n"
                        "+      </div>\n"
                        "+      <div id=\"coupon-feedback\" class=\"coupon-feedback\" aria-live=\"polite\"></div>\n"
                        "+      <div class=\"coupon-summary\" id=\"coupon-summary\" style=\"display:none;\">\n"
                        "+        <div class=\"coupon-row\">\n"
                        "+          <span>Zwischensumme:</span>\n"
                        "+          <span id=\"coupon-original-total\"></span>\n"
                        "+        </div>\n"
                        "+        <div class=\"coupon-row coupon-discount-row\">\n"
                        "+          <span>Rabatt (<span id=\"coupon-code-label\"></span>):</span>\n"
                        "+          <span id=\"coupon-discount-amount\"></span>\n"
                        "+        </div>\n"
                        "+        <div class=\"coupon-row coupon-final-row\">\n"
                        "+          <strong>Endbetrag:</strong>\n"
                        "+          <strong id=\"coupon-final-total\"></strong>\n"
                        "+        </div>\n"
                        "+      </div>\n"
                        "+    </div>\n"
                        " \n"
                        "     <div class=\"cart-actions\">\n"
                        "       <a href=\"/checkout\" class=\"btn btn-primary\">Zur Kasse</a>\n"
                    ),
                },
                # ── JS: ADDED wishlist.js ──────────────────────────────────────
                {
                    "old_path": "resources/js/wishlist.js",
                    "new_path": "resources/js/wishlist.js",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,48 @@\n"
                        "+/**\n"
                        "+ * wishlist.js – Wunschlisten-Interaktionen (AK1, ECOM-42)\n"
                        "+ */\n"
                        "+document.addEventListener('DOMContentLoaded', function () {\n"
                        "+  const API = '/api';\n"
                        "+\n"
                        "+  // Move item to cart\n"
                        "+  document.querySelectorAll('.btn-move-to-cart').forEach(function (btn) {\n"
                        "+    btn.addEventListener('click', async function () {\n"
                        "+      const productId = this.dataset.productId;\n"
                        "+      const resp = await fetch(`${API}/wishlist/${productId}/move-to-cart`, { method: 'POST' });\n"
                        "+      if (resp.ok) {\n"
                        "+        this.closest('.wishlist-item').remove();\n"
                        "+        showToast('Produkt in den Warenkorb verschoben.');\n"
                        "+      } else {\n"
                        "+        showToast('Fehler beim Verschieben.', 'error');\n"
                        "+      }\n"
                        "+    });\n"
                        "+  });\n"
                        "+\n"
                        "+  // Remove item from wish list\n"
                        "+  document.querySelectorAll('.btn-remove-wish').forEach(function (btn) {\n"
                        "+    btn.addEventListener('click', async function () {\n"
                        "+      const productId = this.dataset.productId;\n"
                        "+      const resp = await fetch(`${API}/wishlist/${productId}`, { method: 'DELETE' });\n"
                        "+      if (resp.ok) {\n"
                        "+        this.closest('.wishlist-item').remove();\n"
                        "+      }\n"
                        "+    });\n"
                        "+  });\n"
                        "+\n"
                        "+  function showToast(msg, type = 'success') {\n"
                        "+    const el = document.createElement('div');\n"
                        "+    el.className = `toast toast-${type}`;\n"
                        "+    el.textContent = msg;\n"
                        "+    document.body.appendChild(el);\n"
                        "+    setTimeout(() => el.remove(), 3000);\n"
                        "+  }\n"
                        "+});\n"
                    ),
                },
                # ── JS: MODIFIED cart.js ───────────────────────────────────────
                {
                    "old_path": "resources/js/cart.js",
                    "new_path": "resources/js/cart.js",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -34,4 +34,42 @@\n"
                        "   if (clearBtn) {\n"
                        "     clearBtn.addEventListener('click', async function () {\n"
                        "       await fetch(`${API}/cart`, { method: 'DELETE' });\n"
                        "       location.reload();\n"
                        "     });\n"
                        "   }\n"
                        "+\n"
                        "+  // ── Coupon code logic (AK3 + AK5, ECOM-42) ──────────────────\n"
                        "+  const couponInput  = document.getElementById('coupon-input');\n"
                        "+  const couponBtn    = document.getElementById('coupon-apply-btn');\n"
                        "+  const couponFb     = document.getElementById('coupon-feedback');\n"
                        "+  const couponSummary = document.getElementById('coupon-summary');\n"
                        "+\n"
                        "+  function fmt(amount) {\n"
                        "+    return amount.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';\n"
                        "+  }\n"
                        "+\n"
                        "+  if (couponBtn) {\n"
                        "+    couponBtn.addEventListener('click', async function () {\n"
                        "+      const code = couponInput ? couponInput.value.trim() : '';\n"
                        "+      if (!code) {\n"
                        "+        couponFb.textContent = 'Bitte einen Gutscheincode eingeben.';\n"
                        "+        couponFb.className = 'coupon-feedback coupon-error';\n"
                        "+        return;\n"
                        "+      }\n"
                        "+      couponBtn.disabled = true;\n"
                        "+      try {\n"
                        "+        const resp = await fetch(`${API}/cart/coupon`, {\n"
                        "+          method:  'POST',\n"
                        "+          headers: { 'Content-Type': 'application/json' },\n"
                        "+          body:    JSON.stringify({ code }),\n"
                        "+        });\n"
                        "+        const data = await resp.json();\n"
                        "+        if (!resp.ok) {\n"
                        "+          couponFb.textContent = data.error || 'Ungültiger Gutscheincode.';\n"
                        "+          couponFb.className = 'coupon-feedback coupon-error';\n"
                        "+          if (couponSummary) couponSummary.style.display = 'none';\n"
                        "+        } else {\n"
                        "+          couponFb.textContent = 'Gutschein eingelöst!';\n"
                        "+          couponFb.className = 'coupon-feedback coupon-success';\n"
                        "+          document.getElementById('coupon-original-total').textContent = fmt(data.original_total);\n"
                        "+          document.getElementById('coupon-discount-amount').textContent = '−' + fmt(data.discount_amount);\n"
                        "+          document.getElementById('coupon-final-total').textContent = fmt(data.final_total);\n"
                        "+          document.getElementById('coupon-code-label').textContent = data.code;\n"
                        "+          if (couponSummary) couponSummary.style.display = 'block';\n"
                        "+          document.getElementById('cart-total').textContent = fmt(data.final_total);\n"
                        "+        }\n"
                        "+      } catch (e) {\n"
                        "+        couponFb.textContent = 'Netzwerkfehler beim Einlösen.';\n"
                        "+        couponFb.className = 'coupon-feedback coupon-error';\n"
                        "+      } finally {\n"
                        "+        couponBtn.disabled = false;\n"
                        "+      }\n"
                        "+    });\n"
                        "+  }\n"
                        " });\n"
                    ),
                },
                # ── CSS: DELETED legacy-promo.css ──────────────────────────────
                {
                    "old_path": "resources/css/legacy-promo.css",
                    "new_path": "resources/css/legacy-promo.css",
                    "deleted": True,
                    "new_file": False,
                    "diff": (
                        "@@ -1,20 +0,0 @@\n"
                        "-/* legacy-promo.css – DEPRECATED */\n"
                        "-\n"
                        "-.legacy-promo-banner {\n"
                        "-  background-color: #ffeb3b;\n"
                        "-  border: 1px solid #f9a825;\n"
                        "-  border-radius: 4px;\n"
                        "-  padding: 16px 24px;\n"
                        "-  text-align: center;\n"
                        "-  font-size: 0.95rem;\n"
                        "-  color: #333;\n"
                        "-  margin-bottom: 1rem;\n"
                        "-}\n"
                        "-\n"
                        "-.legacy-promo-banner code {\n"
                        "-  background: rgba(0,0,0,0.08);\n"
                        "-  padding: 2px 6px;\n"
                        "-  border-radius: 3px;\n"
                        "-  font-family: monospace;\n"
                        "-}\n"
                    ),
                },
                # ── CSS: ADDED wishlist.css ────────────────────────────────────
                {
                    "old_path": "resources/css/wishlist.css",
                    "new_path": "resources/css/wishlist.css",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,62 @@\n"
                        "+/* wishlist.css – Wunschliste Styles (ECOM-42) */\n"
                        "+\n"
                        "+.wishlist-container {\n"
                        "+  max-width: 900px;\n"
                        "+  margin: 2rem auto;\n"
                        "+  padding: 0 1rem;\n"
                        "+}\n"
                        "+\n"
                        "+.wishlist-list {\n"
                        "+  list-style: none;\n"
                        "+  padding: 0;\n"
                        "+  margin: 0;\n"
                        "+}\n"
                        "+\n"
                        "+.wishlist-item {\n"
                        "+  display: flex;\n"
                        "+  align-items: center;\n"
                        "+  gap: 1rem;\n"
                        "+  padding: 1rem;\n"
                        "+  border-bottom: 1px solid #e0e0e0;\n"
                        "+}\n"
                        "+\n"
                        "+.wishlist-thumb img {\n"
                        "+  width: 64px;\n"
                        "+  height: 64px;\n"
                        "+  object-fit: cover;\n"
                        "+  border-radius: 6px;\n"
                        "+}\n"
                        "+\n"
                        "+.wishlist-info {\n"
                        "+  flex: 1;\n"
                        "+  display: flex;\n"
                        "+  flex-direction: column;\n"
                        "+  gap: 4px;\n"
                        "+}\n"
                        "+\n"
                        "+.wishlist-name { font-weight: 600; }\n"
                        "+.wishlist-price { color: #555; font-size: 0.9rem; }\n"
                        "+\n"
                        "+.wishlist-actions {\n"
                        "+  display: flex;\n"
                        "+  gap: 0.5rem;\n"
                        "+}\n"
                        "+\n"
                        "+.btn-move-to-cart {\n"
                        "+  background: #5b8ef0;\n"
                        "+  color: #fff;\n"
                        "+  border: none;\n"
                        "+  border-radius: 4px;\n"
                        "+  padding: 6px 14px;\n"
                        "+  cursor: pointer;\n"
                        "+  font-size: 0.88rem;\n"
                        "+}\n"
                        "+\n"
                        "+.btn-move-to-cart:hover { background: #3a6fd8; }\n"
                        "+\n"
                        "+.btn-remove-wish {\n"
                        "+  background: none;\n"
                        "+  border: 1px solid #e53935;\n"
                        "+  color: #e53935;\n"
                        "+  border-radius: 4px;\n"
                        "+  padding: 6px 10px;\n"
                        "+  cursor: pointer;\n"
                        "+}\n"
                        "+\n"
                        "+.wishlist-empty { color: #888; text-align: center; padding: 3rem 0; }\n"
                    ),
                },
                # ── CSS: MODIFIED cart.css ─────────────────────────────────────
                {
                    "old_path": "resources/css/cart.css",
                    "new_path": "resources/css/cart.css",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -58,3 +58,44 @@\n"
                        " .cart-empty {\n"
                        "   color: #888;\n"
                        "   text-align: center;\n"
                        "   padding: 3rem 0;\n"
                        " }\n"
                        "+\n"
                        "+/* Coupon section (AK3 + AK5, ECOM-42) */\n"
                        "+.coupon-section {\n"
                        "+  border: 1px solid #e0e0e0;\n"
                        "+  border-radius: 6px;\n"
                        "+  padding: 1rem 1.25rem;\n"
                        "+  margin-bottom: 1.5rem;\n"
                        "+  background: #fafafa;\n"
                        "+}\n"
                        "+\n"
                        "+.coupon-section h3 {\n"
                        "+  margin: 0 0 0.75rem;\n"
                        "+  font-size: 1rem;\n"
                        "+  font-weight: 600;\n"
                        "+}\n"
                        "+\n"
                        "+.coupon-input-row {\n"
                        "+  display: flex;\n"
                        "+  gap: 0.5rem;\n"
                        "+}\n"
                        "+\n"
                        "+.coupon-input {\n"
                        "+  flex: 1;\n"
                        "+  padding: 8px 12px;\n"
                        "+  border: 1px solid #ccc;\n"
                        "+  border-radius: 4px;\n"
                        "+  font-size: 0.95rem;\n"
                        "+  text-transform: uppercase;\n"
                        "+  letter-spacing: 0.05em;\n"
                        "+}\n"
                        "+\n"
                        "+.coupon-feedback { margin-top: 0.5rem; font-size: 0.875rem; min-height: 1.2em; }\n"
                        "+.coupon-success  { color: #2e7d32; }\n"
                        "+.coupon-error    { color: #c62828; }\n"
                        "+\n"
                        "+.coupon-summary  { margin-top: 0.75rem; border-top: 1px solid #e0e0e0; padding-top: 0.75rem; }\n"
                        "+.coupon-row      { display: flex; justify-content: space-between; margin-bottom: 4px; font-size: 0.9rem; }\n"
                        "+.coupon-discount-row { color: #2e7d32; }\n"
                        "+.coupon-final-row    { font-size: 1rem; margin-top: 6px; }\n"
                    ),
                },
                # ── Docker: MODIFIED docker-compose.yml ───────────────────────
                {
                    "old_path": "docker-compose.yml",
                    "new_path": "docker-compose.yml",
                    "deleted": False,
                    "new_file": False,
                    "diff": (
                        "@@ -34,10 +34,16 @@\n"
                        "   redis:\n"
                        "     image: redis:7-alpine\n"
                        "     ports:\n"
                        "       - \"6379:6379\"\n"
                        "     restart: unless-stopped\n"
                        " \n"
                        "-  mailer-legacy:\n"
                        "-    image: schickling/mailcatcher:latest\n"
                        "-    ports:\n"
                        "-      - \"1080:1080\"\n"
                        "-      - \"1025:1025\"\n"
                        "-    restart: unless-stopped\n"
                        "-    # TODO: replace with modern mail service – this image is unmaintained\n"
                        "+  # AK10 (ECOM-42): redis-queue replaces mailer-legacy\n"
                        "+  redis-queue:\n"
                        "+    image: redis:7-alpine\n"
                        "+    command: redis-server --port 6380 --maxmemory 256mb --maxmemory-policy allkeys-lru\n"
                        "+    ports:\n"
                        "+      - \"6380:6380\"\n"
                        "+    restart: unless-stopped\n"
                        "+    healthcheck:\n"
                        "+      test: [\"CMD\", \"redis-cli\", \"-p\", \"6380\", \"ping\"]\n"
                        "+      interval: 10s\n"
                        "+      timeout: 5s\n"
                        "+      retries: 3\n"
                        " \n"
                        "   volumes:\n"
                        "     mysql_data:\n"
                    ),
                },
                # ── Docker: ADDED redis-queue/Dockerfile ──────────────────────
                {
                    "old_path": "docker/redis-queue/Dockerfile",
                    "new_path": "docker/redis-queue/Dockerfile",
                    "deleted": False,
                    "new_file": True,
                    "diff": (
                        "@@ -0,0 +1,15 @@\n"
                        "+# redis-queue/Dockerfile\n"
                        "+# Lightweight Redis instance used as async job queue (AK10, ECOM-42).\n"
                        "+# Separate from the main Redis cache to isolate queue backpressure.\n"
                        "+\n"
                        "+FROM redis:7-alpine\n"
                        "+\n"
                        "+# Custom redis.conf: persistence off (queue, not store), max 256 MB\n"
                        "+COPY redis-queue.conf /usr/local/etc/redis/redis.conf\n"
                        "+\n"
                        "+EXPOSE 6380\n"
                        "+\n"
                        "+HEALTHCHECK --interval=10s --timeout=5s --retries=3 \\\n"
                        "+  CMD redis-cli -p 6380 ping || exit 1\n"
                        "+\n"
                        "+CMD [\"redis-server\", \"/usr/local/etc/redis/redis.conf\"]\n"
                    ),
                },
            ],
        },
    ],
    6: [  # user-service – pre-existing MR for context
        {
            "id": 601, "iid": 1,
            "title": "refactor: split UserService into ProfileService and AvatarService",
            "description": (
                "Prepares the service layer for the upcoming user-profile extensions (STACKLENS-2). "
                "ProfileService handles CRUD for user data; AvatarService will own upload logic."
            ),
            "state": "merged",
            "source_branch": "refactor/split-user-service",
            "target_branch": "main",
            "web_url": "http://localhost:3080/demo-group/user-service/-/merge_requests/1",
            "author": {"name": "Lisa Weber"},
            "merged_at": "2026-03-10T10:00:00Z",
            "changes": [
                {
                    "old_path": "src/main/java/com/company/userservice/service/UserService.java",
                    "new_path": "src/main/java/com/company/userservice/service/ProfileService.java",
                    "diff": (
                        "@@ -1,40 +1,35 @@\n"
                        "-// UserService.java – monolithic user logic\n"
                        "+// ProfileService.java – handles user profile CRUD only\n"
                        " package com.company.userservice.service;\n"
                        "+// AvatarService extracted to separate class\n"
                    ),
                },
            ],
        },
    ],
}


# ──────────────────────────────────────────────────────────────────────────────
# Project 8: pipeline-demo
# Reference stack for the StackLens Pipeline Analysis feature.
# Contains:
#  - docker-compose.yml              (production stack)
#  - docker-compose.override.yml    (local dev overrides)
#  - Dockerfile                       (multi-stage Node.js app)
#  - .gitlab-ci.yml                   (GitLab CI test → build → deploy)
#  - .github/workflows/ci.yml         (GitHub Actions equivalent)
#  - Jenkinsfile                       (declarative Jenkins)
#  - bitbucket-pipelines.yml          (Bitbucket)
#  - azure-pipelines.yml              (Azure DevOps)
#  - .travis.yml                       (Travis CI)
# The files are deliberately close to real-world setups (a few common smells
# like :latest tags and un-pinned actions are intentional so the AI has
# something to find when the Pipeline Analysis feature is exercised).
# ──────────────────────────────────────────────────────────────────────────────

PROJECT_FILES[8] = {

    "README.md": """\
# pipeline-demo

A reference stack used to demonstrate the **StackLens Pipeline Analysis**
feature. This single repository bundles a small but realistic Docker Compose
stack alongside parallel CI/CD pipelines for every major provider, so that
`/api/pipeline-analysis/start-gitlab` against the demo GitLab returns a
non-trivial analysis with findings, per-file key=value descriptions,
optimization suggestions and a Mermaid diagram.

## Layout

    .
    ├── docker-compose.yml            # production stack (postgres + redis + app + nginx)
    ├── docker-compose.override.yml  # local-dev overrides
    ├── Dockerfile                     # multi-stage Node.js 22 build
    ├── .gitlab-ci.yml                 # GitLab CI: test → build → deploy
    ├── .github/workflows/ci.yml       # GitHub Actions: same pipeline
    ├── Jenkinsfile                    # declarative Jenkins pipeline
    ├── bitbucket-pipelines.yml        # Bitbucket Pipelines
    ├── azure-pipelines.yml            # Azure DevOps Pipelines
    └── .travis.yml                    # Travis CI

## Run locally

    docker compose up -d

## Run in CI

Pick a provider and push to the matching branch. Every pipeline defined here
boots the same compose stack in an ephemeral CI runner, runs `npm test` and
deploys on success.
""",

    "docker-compose.yml": """\
# Production stack for the pipeline-demo app.
# Reference for the StackLens Pipeline Analysis demo.
version: "3.9"

services:
  postgres:
    image: postgres:16-alpine
    container_name: pipeline-demo-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: app
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: app
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d app"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: pipeline-demo-redis
    restart: unless-stopped
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime
    container_name: pipeline-demo-app
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      NODE_ENV: production
      DATABASE_URL: postgres://app:${POSTGRES_PASSWORD}@postgres:5432/app
      REDIS_URL: redis://redis:6379
    ports:
      - "3000:3000"

  nginx:
    image: nginx:1.27-alpine
    container_name: pipeline-demo-nginx
    restart: unless-stopped
    depends_on:
      - app
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro

volumes:
  postgres-data:
  redis-data:
""",

    "docker-compose.override.yml": """\
# Local development overrides – loaded automatically by `docker compose up`.
# Keeps production docker-compose.yml unchanged.
services:
  app:
    build:
      target: dev
    command: ["npm", "run", "dev"]
    volumes:
      - ./src:/app/src
      - ./package.json:/app/package.json
    environment:
      NODE_ENV: development
    ports:
      - "3000:3000"

  postgres:
    ports:
      - "5432:5432"
""",

    "Dockerfile": """\
# Multi-stage build for the pipeline-demo Node.js app.
# Stage 1: install dependencies
# Stage 2: build TypeScript bundles
# Stage 3: production runtime
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-alpine AS dev
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
CMD ["npm", "run", "dev"]

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=deps /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY package.json ./
USER node
EXPOSE 3000
CMD ["node", "dist/server.js"]
""",

    ".gitlab-ci.yml": """\
# GitLab CI pipeline for the pipeline-demo app.
# Stages: validate → test → build → deploy

stages:
  - validate
  - test
  - build
  - deploy

variables:
  DOCKER_IMAGE: registry.example.com/pipeline-demo/app
  DOCKER_TAG: $CI_COMMIT_SHORT_SHA

# Cache node_modules + Docker layers between pipelines.
cache:
  key: ${CI_COMMIT_REF_SLUG}
  paths:
    - node_modules/
    - .docker-cache/

lint:
  stage: validate
  image: node:22-alpine
  script:
    - npm ci --no-audit --no-fund
    - npm run lint
    - npm run format:check
  artifacts:
    when: on_failure
    paths:
      - lint-report.txt

unit-tests:
  stage: test
  image: node:22-alpine
  services:
    - name: postgres:16-alpine
      alias: postgres
    - name: redis:7-alpine
      alias: redis
  variables:
    POSTGRES_DB: app_test
    POSTGRES_USER: app
    POSTGRES_PASSWORD: test
    REDIS_URL: redis://redis:6379
  script:
    - npm ci --no-audit --no-fund
    - npm test -- --coverage
  coverage: '/Lines\\s*:\\s*(\\d+\\.\\d+)%/'
  artifacts:
    reports:
      junit: junit.xml

docker-build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  script:
    - docker build --pull -t $DOCKER_IMAGE:$DOCKER_TAG .
    - docker push $DOCKER_IMAGE:$DOCKER_TAG
  only:
    - main
    - develop

deploy-production:
  stage: deploy
  image: alpine:3.19
  script:
    - apk add --no-cache curl
    - curl -fsSL "$DEPLOY_WEBHOOK" -d "image=$DOCKER_IMAGE:$DOCKER_TAG"
  environment:
    name: production
    url: https://pipeline-demo.example.com
  when: manual
  only:
    - main
""",

    ".github/workflows/ci.yml": """\
# GitHub Actions pipeline mirroring the GitLab CI definition above.
name: ci

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
      - run: npm ci
      - run: npm run lint

  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: app_test
          POSTGRES_USER: app
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U app -d app_test"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    env:
      DATABASE_URL: postgres://app:test@postgres:5432/app_test
      REDIS_URL: redis://redis:6379
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
      - run: npm ci
      - run: npm test -- --coverage

  build:
    needs: [validate, test]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop'
    steps:
      - uses: actions/checkout@v4
      - name: Build & push Docker image
        run: |
          docker build -t registry.example.com/pipeline-demo/app:${{ github.sha }} .
          docker push registry.example.com/pipeline-demo/app:${{ github.sha }}

  deploy:
    needs: [build]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment:
      name: production
      url: https://pipeline-demo.example.com
    steps:
      - name: Trigger deploy
        run: curl -fsSL "$DEPLOY_WEBHOOK" -d "image=${{ github.sha }}"
""",

    "Jenkinsfile": """\
// Declarative Jenkins pipeline for pipeline-demo.
pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        DOCKER_IMAGE = 'registry.example.com/pipeline-demo/app'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Install') {
            steps {
                sh 'npm ci --no-audit --no-fund'
            }
        }

        stage('Lint') {
            steps {
                sh 'npm run lint'
            }
        }

        stage('Test') {
            steps {
                sh 'npm test -- --coverage'
            }
            post {
                always {
                    junit 'junit.xml'
                }
            }
        }

        stage('Build') {
            when {
                branch 'main'
            }
            steps {
                sh 'docker build -t ${DOCKER_IMAGE}:${BUILD_NUMBER} .'
                sh 'docker push ${DOCKER_IMAGE}:${BUILD_NUMBER}'
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                sh 'curl -fsSL "$DEPLOY_WEBHOOK" -d "image=${BUILD_NUMBER}"'
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
""",

    "bitbucket-pipelines.yml": """\
# Bitbucket Pipelines configuration for pipeline-demo.
image: node:22-alpine

pipelines:
  default:
    - step:
        name: Install + Lint
        caches:
          - node
        script:
          - npm ci
          - npm run lint
    - step:
        name: Test
        services:
          - postgres
          - redis
        script:
          - npm test
        after-script:
          - pipe: atlassian/junit-reporter:0.4.0
            variables:
              REPORT_PATH: 'junit.xml'
  branches:
    main:
      - step:
          name: Build & push
          services:
            - docker
          script:
            - docker build -t $DOCKER_IMAGE:$BITBUCKET_COMMIT .
            - docker push $DOCKER_IMAGE:$BITBUCKET_COMMIT
      - step:
          name: Deploy
          deployment: production
          trigger: manual
          script:
            - curl -fsSL "$DEPLOY_WEBHOOK" -d "image=$BITBUCKET_COMMIT"

definitions:
  services:
    postgres:
      image: postgres:16-alpine
      environment:
        POSTGRES_DB: app_test
        POSTGRES_USER: app
        POSTGRES_PASSWORD: test
    redis:
      image: redis:7-alpine
    docker:
      image: docker:24
""",

    "azure-pipelines.yml": """\
# Azure DevOps pipeline for pipeline-demo.
trigger:
  branches:
    include:
      - main
      - develop

pool:
  vmImage: ubuntu-latest

variables:
  DOCKER_IMAGE: registry.example.com/pipeline-demo/app
  NODE_VERSION: '22.x'

stages:
  - stage: Validate
    displayName: 'Lint & type-check'
    jobs:
      - job: LintJob
        steps:
          - task: NodeTool@0
            inputs:
              versionSpec: $(NODE_VERSION)
          - task: Cache@2
            inputs:
              key: 'npm | "$(Agent.OS)" | package-lock.json'
              restoreKeys: |
                npm | "$(Agent.OS)"
              path: $(Pipeline.Workspace)/.npm
          - script: npm ci
          - script: npm run lint

  - stage: Test
    displayName: 'Unit tests'
    dependsOn: Validate
    jobs:
      - job: TestJob
        services:
          postgres16:
            image: postgres:16-alpine
            env:
              POSTGRES_DB: app_test
              POSTGRES_USER: app
              POSTGRES_PASSWORD: test
            ports:
              - 5432:5432
          redis7:
            image: redis:7-alpine
            ports:
              - 6379:6379
        steps:
          - task: NodeTool@0
            inputs:
              versionSpec: $(NODE_VERSION)
          - task: Cache@2
            inputs:
              key: 'npm | "$(Agent.OS)" | package-lock.json'
              restoreKeys: |
                npm | "$(Agent.OS)"
              path: $(Pipeline.Workspace)/.npm
          - script: npm ci
          - script: npm test
          - task: PublishTestResults@2
            inputs:
              testResultsFiles: 'junit.xml'

  - stage: Build
    displayName: 'Docker image'
    dependsOn: Test
    condition: and(succeeded(), in(variables['Build.SourceBranch'], 'refs/heads/main', 'refs/heads/develop'))
    jobs:
      - job: DockerBuild
        steps:
          - script: |
              docker build -t $(DOCKER_IMAGE):$(Build.SourceVersion) .
              docker push $(DOCKER_IMAGE):$(Build.SourceVersion)

  - stage: Deploy
    displayName: 'Deploy to production'
    dependsOn: Build
    condition: and(succeeded(), in(variables['Build.SourceBranch'], 'refs/heads/main'))
    jobs:
      - deployment: Production
        environment: production
        strategy:
          runOnce:
            deploy:
              steps:
                - script: curl -fsSL "$(DEPLOY_WEBHOOK)" -d "image=$(Build.SourceVersion)"
""",

    ".travis.yml": """\
# Travis CI configuration for pipeline-demo.
language: node_js
node_js:
  - '22'

services:
  - postgresql
  - redis

addons:
  postgresql: '16'

env:
  global:
    - DATABASE_URL=postgres://postgres@localhost:5432/travis_ci_test
    - REDIS_URL=redis://localhost:6379

cache:
  directories:
    - node_modules

install:
  - npm ci --no-audit --no-fund

script:
  - npm run lint
  - npm test

jobs:
  include:
    - stage: deploy
      if: branch = main AND type = push
      script:
        - docker build -t registry.example.com/pipeline-demo/app:$TRAVIS_COMMIT .
        - docker push registry.example.com/pipeline-demo/app:$TRAVIS_COMMIT
        - curl -fsSL "$DEPLOY_WEBHOOK" -d "image=$TRAVIS_COMMIT"

stages:
  - name: test
  - name: deploy
""",

    "nginx.conf": """\
# Minimal reverse-proxy config used by the nginx service in docker-compose.yml.
worker_processes auto;

events {
    worker_connections 1024;
}

http {
    upstream app {
        server app:3000;
    }

    server {
        listen 80;
        server_name _;

        location / {
            proxy_pass http://app;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        location /health {
            access_log off;
            return 200 'ok\\n';
        }
    }
}
""",

    "package.json": """\
{
  "name": "pipeline-demo",
  "version": "1.0.0",
  "description": "Reference stack for the StackLens Pipeline Analysis feature",
  "main": "dist/server.js",
  "scripts": {
    "start": "node dist/server.js",
    "dev": "nodemon --watch src src/server.ts",
    "build": "tsc -p .",
    "lint": "eslint .",
    "format:check": "prettier --check .",
    "test": "jest --runInBand"
  },
  "engines": {
    "node": ">=22.0.0"
  },
  "dependencies": {
    "express": "^4.19.0",
    "pg": "^8.11.5",
    "ioredis": "^5.4.1"
  },
  "devDependencies": {
    "@types/express": "^4.17.21",
    "@types/node": "^22.0.0",
    "@types/pg": "^8.11.5",
    "eslint": "^9.0.0",
    "jest": "^29.7.0",
    "nodemon": "^3.1.0",
    "prettier": "^3.2.5",
    "ts-jest": "^29.1.5",
    "typescript": "^5.4.0"
  }
}
""",
}
