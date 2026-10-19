# PHP Coding Guidelines

This file is the authoritative source of coding standards for all PHP code produced in this project.

---

## Git Operations

- **⚠️ NEVER run `git add`, `git commit`, `git push`, or any other git write command.** The pipeline manages all git operations automatically. Running these commands corrupts the simulated pull-request view and pollutes the branch history with tool artefacts (e.g. `.phpunit.cache/`, coverage XML files). Use only the `write`, `read`, and `delete` tools to manage files, and the `run` tool exclusively for build/test commands (make, phpunit, composer, etc.).
- **⚠️ NEVER write test/coverage artifacts to the repository using the `write` tool.** The `write` tool is exclusively for source files (`src/`, `tests/`, config files). The following files must **never** be created or written: `coverage.xml`, `coverageOutput`, `junit_*.xml`, `junit_unit.xml`, `junit_integration.xml`, `junit_system.xml`, `.phpunit.cache`, `*.clover`, `*.cov`, or any other file produced as a side-effect of running tests or coverage. These are build artifacts — they are generated inside Docker containers and must not appear in the repository.
- **⚠️ NEVER write or stage the `vendor/` directory.** The `vendor/` directory is managed exclusively by `composer install` / `composer update` and must never be created, modified, or staged via the `write` tool. Use `composer require` or `composer update` to manage PHP dependencies — the `write` tool must never touch any file under `vendor/`.

---

## General

- **⚠️ ABSOLUTELY NO COMMENTS** — Comments of any kind are **strictly forbidden** in all code files and `.env` files. This means: no inline comments (`//`, `#`), no block comments (`/* … */`), no docblocks (`/** … */`), no TODO/FIXME markers, and no commented-out code. Code must be self-explanatory through clear naming and structure alone. Violating this rule is a hard error and must be fixed immediately before any other change. **Exceptions**: (1) **PHP DI container configurations only** may contain comments to document their purpose. All other configuration files are treated like any other source file and must remain comment-free. (2) Each system test class may contain exactly one use-case-describing comment at the top of the test method (e.g. `// Order ST, with delivery address, with XML, with print data`) — nothing else.

- Target PHP version is **8.1 or higher**. Use PHP 8.1+ features (enums, readonly properties, intersection types, fibers) where appropriate.
- All code must conform to **PSR-12** coding style.
- **⚠️ MANDATORY – MAXIMUM LINE LENGTH IS 120 CHARACTERS**: No line in any PHP file — production code, tests (unit, integration, system), configuration files — may exceed 120 characters. This is enforced by `Generic.Files.LineLength.TooLong` and causes a hard sniffer failure. If a line is too long: break method arguments to one per line, extract long expressions into variables, or wrap chained calls across multiple lines. There are **no exceptions** — not even for long strings or imports. Long URLs in comments should be split across lines using a backslash continuation.
- Every PHP file starts with `<?php` followed immediately by `declare(strict_types=1);` — no exceptions.
- Every method must have an explicit **return type declaration** (including `: void`).
- Every class property must have an explicit **type declaration**.
- All identifiers (class names, variable names, method names, etc.) are in **English**.
- Strings and enums on which logic depends (e.g. process types, product types) are in **English**.

---

## Directory Structure – Hexagonal Architecture

- `src/` is split into `application/`, `domain/`, and `infrastructure/` with appropriate subfolders.
- Namespace nesting follows PSR-4 and mirrors the folder structure.
- Relationships between layers are validated via Deptrac.
- There is **no** general interface folder — interfaces live next to their implementation classes.
  - **Exception:** ports of the hexagonal architecture may have their own folder.
- **⚠️ MANDATORY – NO CLASS-KIND SUFFIX IN NAMES**: Interface names must **never** end with the suffix `Interface` (e.g. `AddonHandlerInterface` is forbidden). Equally, class names must never end with `Abstract` or `Trait`. This is enforced by the `Banana.File.NoClassKindSuffix.InvalidInterfaceName` sniffer rule and causes a hard build failure. Name the interface after what it **represents**, not after what it **is**:
  - ❌ WRONG: `AddonHandlerInterface`, `OrderRepositoryInterface`, `PaymentGatewayInterface`
  - ✅ CORRECT: `AddonHandler`, `OrderRepository`, `PaymentGateway`
- All classes derived from an interface start their class name with the interface name:
  - Interface: `AddonHandler`
  - Implementations: `AddonHandlerMail`, `AddonHandlerBarcode`

---

## Classes

- The **constructor** is the first method after class-level variable declarations.
- The constructor contains **only assignments** — no logic. Exception: `ensure*()` validation methods where sensible.
- If the constructor requires **more than 3 parameters**, the design must be reconsidered and refactored.
- **Aggregates** return class instances from getter methods, never primitive types.
- **Aggregates** are always created with value objects, never with raw arrays.
- Do **not** use `__invoke` — always reference the explicit method name (e.g. `[$this->handler, 'handleFoo']`).
- There is a **blank line before every `return` statement**.
- **⚠️ MANDATORY – TWO BLANK LINES BETWEEN EVERY METHOD/FUNCTION:** There are **always exactly two blank lines between methods and functions**. This rule applies to **ALL PHP code** — production code AND every type of test (unit, integration, system) — without any exception. A single blank line between methods or functions is a hard violation. This rule is not optional and must ALWAYS be followed.

```php
// ❌ WRONG – only one blank line between methods
class MyService
{
    private PDO $pdo;

    protected function setUp(): void
    {
        $this->pdo = $this->getTestPDO();
    }

    protected function tearDown(): void
    {
        $this->cleanupTestData();
    }
}

// ✅ CORRECT – exactly two blank lines between methods
class MyService
{
    private PDO $pdo;


    protected function setUp(): void
    {
        $this->pdo = $this->getTestPDO();
    }


    protected function tearDown(): void
    {
        $this->cleanupTestData();
    }
}
```
- **Single Responsibility Principle**: Every class has exactly **one responsibility**. If a class handles more than one concern (e.g. validation AND persistence AND notification), it must be split into separate classes. A class name must clearly express its single purpose.
- Use `return new self(…)` where applicable; the return type declaration is `: self`.
- For explicit value representation, define dedicated methods: `asString()`, `asInt()`, `asArray()`, `as…WithCapitalLetters()`, etc.
- **Getters** are named with the `get` prefix: `getOrderItemId()`, not `orderItemId()`.
- Dependencies are always **imported** (`use` statements) — no leading backslash (e.g. `RuntimeException`, not `\RuntimeException`).
- Value objects are always injected via the **constructor**.
- **Setter methods** are only used when there is no alternative (e.g. third-party library setters). New objects are always preferred. If setters are used, a corresponding state validation is required.
- A method that returns a value must **never** also be able to return `null`. If no value can be provided, return an appropriate value object or throw an exception.
- The **last `catch` block** always catches `Throwable`.
- Where available, use an existing library class instead of building a custom one.
- Context-dependent objects should carry a corresponding namespace (especially in libraries).
- Library value objects should contain **no business logic**.

---

## Class Types (DDD)

| Type | Description |
|---|---|
| **Entity** | Has unique identity and a lifecycle in the domain. Business logic lives here. Annotated with `#[Entity]`. |
| **AggregateObject** | Contains business rules. Uses an entity as its root identity (e.g. `id = OrderItemId`). |
| **ValueObject** | Wraps a primitive type (`string`, `int`, `bool`, `float`). Immutable. Can self-validate. |
| **Service** | Contains business logic that is too general to belong to a specific entity. |
| **ParameterObject** | Groups thematically related parameters to minimise method parameter counts. |

---

## ValueObjects

- ValueObjects may self-validate and contain at most comparison operations — nothing more.
- When creating a ValueObject, check whether creation/validity criteria exist and add the corresponding validation.
- **Never** compare ValueObjects with `===` on their primitive representation. Use the `equals()` method:

```php
// Wrong
if ($valueObject->asString() === $other->asString())

// Correct
if ($valueObject->equals($other))
```

---

## Logical Operators

- The **ternary operator** is not used — use `if`/`else` instead (better readability and testability).
- As soon as more than **two boolean operators** (`&&`, `||`) are needed in a single `if` condition, extract them into a descriptive method. Also evaluate whether the **Strategy Pattern** can be applied.

---

## Variables

- Static variable values are assigned at **declaration**.
- Classic instance variable values are assigned in the **constructor**.
- Variables are exposed via **getter methods**.
- Variable names are **descriptive** and written out in full — no abbreviations.

---

## json_encode / json_decode

- Every call to `json_encode()` must be wrapped in a **try-catch** block. Use `JSON_THROW_ON_ERROR` to ensure a `JsonException` is thrown on failure:

```php
use JsonException;

// ❌ WRONG – no error handling
$json = json_encode($data);

// ✅ CORRECT – throws on error, caught explicitly
try {
    $json = json_encode($data, JSON_THROW_ON_ERROR);
} catch (JsonException $e) {
    throw new RuntimeException('JSON encoding failed: ' . $e->getMessage());
}
```

- Every call to `json_decode()` must equally use `JSON_THROW_ON_ERROR` and be wrapped in a try-catch.

---

## Exceptions

- **Errors must always be logged**: Every `catch` block that handles an error must log it — at minimum with `$this->logger->error(...)` or the project's equivalent logging mechanism. Silent swallowing of exceptions is forbidden. Log the exception message and, where relevant, the full stack trace.
- Custom exception classes are only created in **rare exceptional cases** (e.g. event processors with special behaviour).
- Exception messages are **English**, start with an **uppercase letter**, and end **without a period**.
- Format:

```php
__CLASS__ . ': This is an exception message: ' . $input
```

- Messages must be **precise**, **unique**, and structured like log messages.
- Each message contains: error class · error reason · order/request identifier · additional context.

---

## Tests – General

- **⚠️ MANDATORY – NO JUNIT XML FILES**: This is a **PHP project**. JUnit is a Java testing framework and has no place here. **Never** create `junit_unit.xml`, `junit_integration.xml`, `junit_system.xml`, or any other JUnit-style XML file. Test suites are configured exclusively inside **`phpunit.xml`** using PHPUnit's `<testsuites>` element.

- **⚠️ MANDATORY – `phpunit.xml` CONFIGURATION IS FROZEN**: The canonical `phpunit.xml` configuration with all three test suites looks exactly like this — do not deviate. The XML attributes on the `<phpunit>` element are **permanently fixed** and must **never** be changed under any circumstances. **The only permitted change to `phpunit.xml`** is adding a new `<testsuite>` entry inside `<testsuites>` when a new test type is introduced (e.g. system tests for the first time).

- **⚠️ MANDATORY – `beStrictAboutOutputDuringTests` MUST REMAIN TRUE**: Within the frozen configuration, `beStrictAboutOutputDuringTests="true"` must always remain `true`. If a test produces output (e.g. a controller writes a JSON response body via `$response->getBody()->write(...)`), the test must declare that output as expected using `$this->expectOutputRegex(...)` or `$this->expectOutputString(...)` — **never** work around it by setting `beStrictAboutOutputDuringTests` to `false`. PHPUnit does not flag a test as risky when its output is explicitly declared with `expectOutputRegex`/`expectOutputString`.

```xml
<?xml version="1.0"?>
<phpunit xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         bootstrap="tests/bootstrap.php"
         xsi:noNamespaceSchemaLocation="https://schema.phpunit.de/10.5/phpunit.xsd"
         colors="true"
         beStrictAboutTestsThatDoNotTestAnything="true"
         stopOnError="false"
         stopOnFailure="false"
         processIsolation="false"
         cacheResult="false"
         beStrictAboutOutputDuringTests="true"
         displayDetailsOnTestsThatTriggerWarnings="true"
         displayDetailsOnTestsThatTriggerDeprecations="true"
         requireCoverageMetadata="true">
    <coverage>
        <report>
            <html outputDirectory="tools/reports/coverage" lowUpperBound="35" highLowerBound="70"/>
        </report>
    </coverage>
    <testsuites>
        <testsuite name="unit">
            <directory>./tests/unit</directory>
        </testsuite>
        <testsuite name="integration">
            <directory>./tests/integration</directory>
        </testsuite>
        <testsuite name="system">
            <directory>./tests/system</directory>
        </testsuite>
    </testsuites>
    <php>
        <ini name="display_errors" value="true"/>
    </php>
    <source>
        <include>
            <directory suffix=".php">src</directory>
        </include>
    </source>
</phpunit>
```

- **⚠️ MANDATORY – TWO BLANK LINES BETWEEN EVERY TEST METHOD** — identical to the rule for production code; applies to all test types (unit, integration, system) without exception. One blank line is a hard violation.
- **100 % unit test coverage** is mandatory.
- Every story/ticket includes at least one **integration test**.
- Test methods are named in camelCase and **must start with `test`** (e.g. `testThrowsWhenReportJsonCannotBeRead`). Do **not** use snake_case (e.g. `it_throws_when_…`) and do **not** use the `#[Test]` attribute as a substitute for the `test` prefix.
- Test data is created **directly in the test class**. For large datasets, use descriptively named files in the `resources` folder.
- Test data must **not** be generated via external infrastructure (e.g. Makefiles).
- Paths are always **relative**.
- Date and time are always controlled via a **mocked Clock** — never `new \DateTime()` or `time()`.

### Folder & File Cleanup

- If a test writes data to a folder, the **entire folder is deleted** in `tearDown()` and recreated with `mkdir()` in `setUp()`.
- **No leftover artefacts** must exist after a test run.
- **No `.gitKeep` folders**.

### Resource Structure

Test resources live in subfolders matching the test type:
```
tests/resources/unit/
tests/resources/integration/
tests/resources/system/
```
Resources for a specific test case live in a subfolder named after the test: `tests/resources/unit/MyTest/`.

- **⚠️ MANDATORY – Test resources are strictly isolated by test type**: Resources under `tests/resources/integration/` must **never** be used in system tests, and resources under `tests/resources/system/` must **never** be used in integration tests. Each test type maintains its own independent set of fixtures, SQL files, JSON payloads, and comparison data. Sharing resources across test types is a hard violation.

---

## Tests – Test Types

| Type | Description | Maintainability |
|---|---|---|
| **Unit Test** | Tests exactly one class; all dependencies are mocked. | Easy to maintain |
| **Integration Test** | Tests multiple classes together with real interfaces (DB, API, files, RabbitMQ, events); rest mocked. | Medium to hard to maintain |
| **System Test** | Tests a complete use case end-to-end without any mocks; all interfaces run in containers. | Hard to maintain |

### Unit Tests
- **⚠️ MANDATORY — EVERY dependency of the class under test MUST be mocked, without exception.** A dependency is every object the class receives via constructor injection, method injection, or static call. This includes — but is not limited to — HTTP clients, API clients, repositories, services, loggers, clocks, and any class whose name ends in `Client`, `Service`, `Repository`, `Gateway`, `Adapter`, `Handler`, `Provider`, or `Manager`. If you are uncertain whether something is a dependency: **mock it**.
- **⚠️ ABSOLUTELY NO REAL NETWORK CALLS, NO REAL EXTERNAL SERVICES IN UNIT TESTS.** A unit test must never cause a real HTTP request, a real database query, a real message queue interaction, or a real call to any external system (e.g. PAL, SSO, InfluxDB, RabbitMQ, S3, SMTP). If your test output contains strings like `PAL error`, `PAL unavailable`, `Connection refused`, `curl error`, or any similar network error, **the test is wrong** — the dependency was not mocked. Fix it immediately: inject a mock for the dependency and define its expected return value.
- Unit tests run entirely in memory. No external process, no Docker service, no container is needed or allowed.

### Integration Tests
- Only classes with external interfaces are tested (databases, APIs, files, RabbitMQ, events).
- Comparison data is **not** in the test class but in files in the `resources` folder.
- Queries are **not** inline in the test; they live in separate files. Exception: very short read queries.

### System Tests
- Test a complete use case from user input to output.
- All interfaces run in **containers**.
- Each system test contains a **use-case-describing comment** (e.g. "Order ST, with delivery address, with XML, with print data").
- Where possible, include an **assertion against a log file**.
- **⚠️ ABSOLUTELY NO MOCKS** — System tests run against real services in Docker containers. No `MockHandler`, no `createMock()`, no stubs. If a service is not available in the test stack, the test stack must be extended — the system test must never be simplified by introducing mocks.

---

## Tests – Mocks

- Mocks are named with the suffix `Mock`: `$this->databaseMock`, `$this->repositoryMock`.
- Mocks verify:
  - **How often** a method is called.
  - **With which parameters** it is called.
  - The **return value**.
  - That a method is **not** called (important for error cases).
- In integration tests, mocking is used **only in exceptional cases**.

---

## Tests – Assertions & Methodology

- Multiple assertions include **descriptive messages**, especially for `assertTrue` / `assertFalse`.
- `assertInstanceOf` is only used for factory or locator tests — and only when it makes sense.
- Value objects and aggregate objects are always tested with **`assertEquals`** or **`assertSame`**.
- Every **unit test** uses a `#[CoversClass(ClassName::class)]` or `#[CoversMethod(ClassName::class, 'methodName')]` attribute.
- **Integration and system tests** are annotated with `#[CoversNothing]`.

---

## Tests – Architecture & Structure

- Large aggregates used in many tests are created via an **`AggregateCreator`** (optionally with Builder Pattern).
- Integration and system tests obtain their dependencies **exclusively via the DI container** — not via constructors or test factories.

---

## Frontend — Twig Templates

- **Twig and JS files must always be saved as UTF-8** (no BOM). This applies to all `.twig`, `.html.twig`, and `.js` files without exception.
- **No HTML string building in JavaScript**: JavaScript must **never** construct HTML markup as string concatenation and inject it via `innerHTML`. Instead:
  1. Prepare named container elements (`<div id="result-count">`, `<div id="error-list">`, etc.) in the Twig template.
  2. In JavaScript, target each container by its `id` and set only its text content via `element.textContent = …` or update a single, minimal `innerHTML` per container — never build an entire HTML structure as a JS string.
  - This keeps markup in Twig (where it belongs), keeps JS readable, and avoids XSS risks from unescaped HTML concatenation.

```js
// ❌ WRONG – builds full HTML in JS
var html = '<p style="color:red;"><strong>' + errorCount + ' fehlgeschlagen</strong></p>';
resultContainer.innerHTML = html;

// ✅ CORRECT – containers defined in Twig, JS sets text only
document.getElementById('error-count').textContent = errorCount + ' fehlgeschlagen';
```

---

## Frontend — CSS

- **No duplicated inline styles**: When the same CSS property/value combination appears on two or more elements, it must be extracted into a **shared CSS class** in the project's stylesheet (or a `<style>` block in the Twig template). Inline `style="…"` attributes are only permitted for truly one-off, dynamic values (e.g. a calculated pixel width set by JS at runtime).
- Duplicated style declarations across files must be detected and consolidated into reusable classes before output is finalised.

---

## InfluxDB

- Classes that **write** data to InfluxDB must be named with the suffix `InfluxdbWriter` (e.g. `MetricsInfluxdbWriter`, `LoadTestInfluxdbWriter`). This naming is mandatory and must be applied consistently across all new and modified classes.

---

## Database Queries

- **⚠️ NO LIMIT unless explicitly specified**: SQL queries (raw PDO/Doctrine/Eloquent) must **never** include a `LIMIT` clause unless the ticket text or the technical refinement comment in the ticket explicitly states a row limit. Inventing a default limit (e.g. `LIMIT 100`, `LIMIT 1000`) is a hard violation. If neither the ticket nor the technical refinement comment mentions a limit, the query must return all matching rows.
- Any `LIMIT` clause in a query without a corresponding limit requirement in the ticket or technical refinement comment is a hard violation and must be flagged during review.

---

## Database Migrations

- Schema changes (new tables, columns, indexes, foreign keys, constraints) live in dedicated migration files under the project's migrations folder (e.g. `database/migrations/*.php`, `migrations/Version*.php`, `db/migrate/*.php`).
- Migrations are **production-code artefacts** and are created in the same change as the entities/repositories that depend on them — never as a separate later step.
- Test fixtures and seeders (`tests/Fixtures/*`, `database/seeders/*`) are not migrations and must not contain `CREATE TABLE` / `ALTER TABLE` statements; they only insert/clean data.
- A schema change without a corresponding migration file is a missing artefact and a hard violation.

---

## Composer Dependencies

This is the canonical procedure for managing Composer dependencies.

1. For every `use` statement that references a third-party vendor namespace (i.e. **not** the project's own `src/` namespace), confirm the package is listed in `composer.json` under `require`.
2. **Missing package** → run `composer require vendor/package` in the repository root — this updates both `composer.json` and `composer.lock`. If `composer` is not available, add the entry manually with the least-restrictive version constraint that includes the needed release.
3. **Unused package** (e.g. after a removal): scan all surviving files in `src/` for any remaining `use` referencing it. If none remain, run `composer remove vendor/package`. Never remove a package still referenced by surviving code (even transitively as a base class or interface).
4. **Autoload sync**: after writing all files, run `composer dump-autoload` to regenerate `vendor/autoload.php`. Skip silently if `composer` is unavailable.
5. Distinguish **application-level packages** (vendor namespaces under `require`) from **PHP extensions** (`ext-*` entries) — both live in `composer.json` but are introduced by different changes.

---

## Deletion-Log Format

When files are deleted, emit exactly one deletion-log block at the end of the output, using this format:

```
### 🗑️ Deleted files
DELETED <path/to/file1>
DELETED <path/to/file2>
```

- Use the heading `### 🗑️ Deleted files` exactly as shown — never add qualifiers ("Deleted test files", "Deleted infrastructure files", etc.).
- One file per `DELETED` line, no leading whitespace, repository-relative path.
- If nothing was deleted, omit the block entirely. Do not output an empty block.
- The format must remain stable so downstream tooling can parse it.
