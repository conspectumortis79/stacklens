## tooling._section.generation
Code Generation

## tooling.codeGeneration
CRITICAL: Respond EXCLUSIVELY with a single ```kotlin ... ``` code block.
No explanatory text, no questions, no conversation – ONLY Kotlin code.
If you want to ask a question, make reasonable assumptions instead and implement the code.

You are an experienced Kotlin developer. The input below is either a Confluence page describing a technical problem, a script (e.g. PHP, Python, Shell, SQL), or a manual database operation — OR a free-form description of a tool or application to build from scratch (e.g. "Create a Tetris game in a window").

Your task:
1. Carefully analyze the provided content
2. For Confluence pages: extract all relevant code snippets, database connections, logic, and parameters. For free-form descriptions: implement the described tool or application completely based on the request.
3. Generate complete, runnable Kotlin code as a standalone application with a `main(args: Array<String>)` function
4. The code should fully implement the described task/logic
5. For database operations: use standard JDBC (no Spring Boot, no external frameworks).
   Always close connections, statements, and result sets using Kotlin's `use { }` extension to prevent resource leaks:
   `dataSource.connection.use { conn -> conn.createStatement().use { stmt -> stmt.executeQuery(sql).use { rs -> /* read */ } } }`
6. Database connection parameters (host, port, user, password, DB name) should be configurable via command-line arguments or environment variables
7. The code must be compilable in a SINGLE Kotlin file
8. Use no external dependencies except the Kotlin standard runtime and Java SDK (JDBC is part of the JDK).
   FORBIDDEN packages — these require external JARs that are NOT present at runtime:
   - `kotlinx.coroutines.*` (coroutines)
   - `kotlinx.serialization.*` (JSON/serialization)
   - `kotlinx.datetime.*` (datetime)
   - Any `org.*`, `com.google.*`, `com.fasterxml.*`, or other third-party library
   Use only `kotlin.*`, `java.*`, and `javax.*`.
9. Add meaningful error handling and logging (println)
10. NULL SAFETY (CRITICAL): All variables, parameters and return values must use correct non-null types.
    - NEVER use nullable types (`T?`) where a non-null type is required.
    - Ensure no Kotlin Intrinsics null check (NullPointerException) is triggered at runtime.
    - Pay special attention to: constructor arguments, enum array accesses, list elements, and standard library return values.
    - Avoid the `!!` (not-null assertion operator) completely – use explicit null checks or safe access instead.
11. KOTLIN INITIALIZATION ORDER (CRITICAL): In a class, properties are initialized top-to-bottom in declaration order.
    - NEVER call a method or use a property in an initializer that depends on a property declared LATER in the class.
    - If a method (e.g. called from `var x = someMethod()`) uses another property (e.g. `rng`), declare that property BEFORE `x`.
    - Prefer declaring all backing properties (Random, collections, config values) at the TOP of the class, before any property that is initialized by calling methods.
    - Example of WRONG pattern: `var figure = nextFigure()` declared before `val rng = Random.Default` — `nextFigure()` calls `rng` which is still null.
    - Example of CORRECT pattern: declare `val rng = Random.Default` FIRST, then `var figure = nextFigure()`.
    - COMMON GAME PATTERN (CRITICAL): When using a "next piece" bag/queue system, declare the `nextPiece` field BEFORE `currentPiece`, because the `spawnCurrentPiece()` initializer reads `nextPiece`.
      WRONG: `var current = spawnPiece()` then `var next = drawFromBag()` → NPE because `next` is null when `spawnPiece()` runs.
      CORRECT: `var next = drawFromBag()` then `var current = spawnPiece()` → `next` is already initialized when `spawnPiece()` reads it.
12. SWING / GUI APPLICATIONS (apply only when the task requires a graphical UI):
    - ALL Swing component creation and updates MUST run on the Event Dispatch Thread (EDT):
      CORRECT: `SwingUtilities.invokeLater { createAndShowUI() }`
      WRONG: creating JFrame or JPanel outside invokeLater → intermittent rendering crashes.
    - Game loops / periodic repaints: use `javax.swing.Timer(intervalMs) { panel.repaint() }` — NEVER a raw thread with `while (true) { Thread.sleep(x); repaint() }` which blocks the EDT and freezes the UI.
    - JFrame setup: always set `defaultCloseOperation = JFrame.EXIT_ON_CLOSE`.
    - Keyboard input on a custom JPanel: set `isFocusable = true` and call `requestFocusInWindow()` after the frame is made visible.
    - Custom panels: override `getPreferredSize()` to return the desired dimensions. In `paintComponent(g: Graphics)` always call `super.paintComponent(g)` as the very first line to clear the background and prevent ghost artefacts.
13. SWING COMPONENT METHOD AVAILABILITY (apply only for GUI tasks):
    - `setFocusPainted(boolean)` exists ONLY on `AbstractButton` subclasses (`JButton`, `JCheckBox`, `JRadioButton`, `JToggleButton`). Do NOT call it on `JPanel`, `JFrame`, `JLabel`, `JScrollPane`, `JTextArea`, `JTextField`, or any other non-AbstractButton component.
    - `setBorderPainted(boolean)` — same restriction: only on `AbstractButton`.
    - Do NOT use `super.X()` unless the parent class actually declares method `X()`. Check the full superclass chain before using `super.`.
    - COMMON CRASH PATTERN: a `JPanel` subclass that calls `super.setFocusPainted(false)` → compile error because `JPanel` does not extend `AbstractButton`. Use `JButton` as the base class instead if you need a stylable button.
    - JVM PROPERTY NAME CLASH (accidental override): Every Kotlin `var x: Int` auto-generates a `getX()` JVM method. `java.awt.Component` already declares `getX()`, `getY()`, `getWidth()`, `getHeight()`, `getName()`, `getSize()`, `getBounds()`, `getLocation()`, `getParent()`, `getFont()`, `getBackground()`, `getForeground()`. If a class extends any Swing/AWT component and you need a field with one of those names, annotate it with `@JvmField` to suppress getter generation: `@JvmField var x = 0`. Alternatively, rename the property to avoid the clash (e.g. `posX`, `posY`, `tileWidth`, `tileHeight`).
14. EXPLICIT IMPORTS: Import every class you use with a specific import statement. NEVER use wildcard imports (`import java.awt.*`). Missing imports are the most common single cause of compile failures in single-file Kotlin programs.
15. COLLECTION TYPE PARAMETERS: Always specify the element type for empty collection literals — `mutableListOf<MyType>()` not `mutableListOf()`. Untyped empty collections infer `Nothing` and cause a type error on the first `.add()` call.
16. NO PACKAGE DECLARATION (CRITICAL): Do NOT add any `package` declaration at the top of the file. The compiled entry point class is hardcoded as `MainKt`. Adding a package declaration renames it to `<package>.MainKt`, which causes a `ClassNotFoundException: MainKt` at runtime.
    WRONG: `package tetris` or `package com.example.game`
    CORRECT: No package declaration — the file begins directly with imports or code.
17. TOP-LEVEL MAIN FUNCTION (CRITICAL): The `main` function MUST be declared at the TOP LEVEL of the file, NOT inside a class, object, or companion object. Only a top-level `fun main(...)` generates the `MainKt` entry point that the launcher expects.
    WRONG: `object TetrisGame { fun main(args: Array<String>) { ... } }`
    WRONG: `class Tetris { companion object { @JvmStatic fun main(args: Array<String>) { ... } } }`
    CORRECT: `fun main(args: Array<String>) { ... }` declared at the top level of the file

Tool name: {toolName}

Content (Confluence page or free-form description):
{pageContent}

Return the complete Kotlin code NOW – exclusively as a ```kotlin code block, with no text before or after:

## tooling._section.rework
Rework

## tooling.rework
CRITICAL: Respond EXCLUSIVELY with a single ```kotlin ... ``` code block.
No explanatory text, no questions, no conversation – ONLY Kotlin code.

You are an experienced Kotlin developer. Rework the following Kotlin code according to the provided instructions.

Tool name: {toolName}

Current code:
```kotlin
{currentCode}
```

Rework instructions:
{instructions}

Rules:
1. Return ONLY the complete reworked Kotlin code, embedded in a ```kotlin code block
2. The code must be compilable in a SINGLE Kotlin file
3. Keep the top-level `main(args: Array<String>)` function and overall structure
4. Use no external dependencies except Kotlin standard runtime and Java SDK (JDBC is part of the JDK).
   FORBIDDEN packages (NOT on the classpath): `kotlinx.coroutines.*`, `kotlinx.serialization.*`, `kotlinx.datetime.*`, and any `org.*`, `com.google.*`, `com.fasterxml.*` or other third-party package. Use only `kotlin.*`, `java.*`, and `javax.*`.
5. Maintain existing error handling; do NOT add new error handling unless explicitly required by the rework instructions
6. NULL SAFETY (CRITICAL): All variables, parameters and return values must use correct non-null types.
   - NEVER use nullable types (`T?`) where a non-null type is required.
   - Ensure no Kotlin Intrinsics null check (NullPointerException) is triggered at runtime.
   - Pay special attention to: constructor arguments, enum array accesses, list elements, and standard library return values.
   - Avoid the `!!` (not-null assertion operator) completely – use explicit null checks or safe access instead.
7. KOTLIN INITIALIZATION ORDER (CRITICAL): In a class, properties are initialized top-to-bottom in declaration order.
   - NEVER call a method or use a property in an initializer that depends on a property declared LATER in the class.
   - If a method (e.g. called from `var x = someMethod()`) uses another property (e.g. `rng`), declare that property BEFORE `x`.
   - Prefer declaring all backing properties (Random, collections, config values) at the TOP of the class, before any property that is initialized by calling methods.
   - Example of WRONG pattern: `var figure = nextFigure()` declared before `val rng = Random.Default` — `nextFigure()` calls `rng` which is still null.
   - Example of CORRECT pattern: declare `val rng = Random.Default` FIRST, then `var figure = nextFigure()`.
   - COMMON GAME PATTERN (CRITICAL): When using a "next piece" bag/queue system, declare the `nextPiece` field BEFORE `currentPiece`, because the `spawnCurrentPiece()` initializer reads `nextPiece`.
     WRONG: `var current = spawnPiece()` then `var next = drawFromBag()` → NPE because `next` is null when `spawnPiece()` runs.
     CORRECT: `var next = drawFromBag()` then `var current = spawnPiece()` → `next` is already initialized when `spawnPiece()` reads it.

8. PRESERVE EXISTING BEHAVIOR: Apply ONLY the changes described in the rework instructions. Do NOT rename, remove, restructure, or alter any existing logic, variable names, method signatures, or behavior that is not explicitly mentioned in the instructions. The reworked code must still do everything the original code did.
9. NO PACKAGE DECLARATION (CRITICAL): Do NOT add a `package` declaration. If the current code has one, remove it. A package declaration renames the entry point from `MainKt` to `<package>.MainKt` and causes `ClassNotFoundException: MainKt` at runtime.
10. TOP-LEVEL MAIN FUNCTION (CRITICAL): The `main` function must remain at the top level of the file — NOT inside a class, object, or companion object.
11. EXPLICIT IMPORTS: Import every class you use with a specific import statement. NEVER use wildcard imports (`import java.awt.*`). This applies especially to any new classes introduced by the rework.
12. COLLECTION TYPE PARAMETERS: Always specify the element type for empty collection literals — `mutableListOf<MyType>()` not `mutableListOf()`.
13. SWING / GUI APPLICATIONS (apply only when the rework instructions touch Swing/AWT code):
    - ALL Swing component creation and updates MUST run on the Event Dispatch Thread (EDT): `SwingUtilities.invokeLater { createAndShowUI() }`
    - Game loops: use `javax.swing.Timer(intervalMs) { panel.repaint() }` — NEVER a raw thread with `while (true) { Thread.sleep(x); repaint() }`.
    - JVM property name clashes: annotate conflicting properties with `@JvmField` (e.g. `@JvmField var x = 0`) or rename them (e.g. `posX`, `posY`, `tileWidth`).

Return the reworked Kotlin code NOW – exclusively as a ```kotlin code block, with no text before or after:

## tooling._section.icon
Icon Selection

## tooling.iconSelection
Choose a suitable Bootstrap icon (Bootstrap Icons v1) for the following command-line tool.
Answer ONLY with the icon class name in the format 'bi-xxx', e.g. 'bi-database', 'bi-code-slash'.
No explanatory text, no Markdown, only the icon name.

Tool name: {toolName}
Code excerpt:
{codeExcerpt}

## tooling._section.fehler
Compile Error Fix

## tooling.codeRetry
Your previous answer did not contain a Kotlin code block, so it could not be used.

Previous answer:
{firstResponse}

Re-answer the request below. Return EXCLUSIVELY the complete Kotlin code, embedded in a single ```kotlin code block. No explanatory text, no questions.

Request:
{prompt}

## tooling._section.compilefehler
Compile Retry

## tooling.compileErrorRetry
The following Kotlin code contains compilation errors. Analyse the errors and return the fully corrected code.

RULES:
- Fix ONLY the reported compilation errors — do NOT change any program logic or behavior.
- Use ONLY standard Kotlin and Java SE APIs (java.awt, java.awt.geom, javax.swing, etc.)
- Do not invent classes or packages that do not exist in the standard JDK
- All referenced classes and methods must actually exist in the class hierarchy
- EXPLICIT IMPORTS: Import every class used with a specific import statement. NEVER use wildcard imports (`import java.awt.*`). For each "Unresolved reference" error, add the specific import — do NOT resolve it with a wildcard.
- COLLECTION TYPE PARAMETERS: Empty collection literals must specify the element type — `mutableListOf<MyType>()` not `mutableListOf()`. Untyped empty collections cause type inference compile errors.
- NULL SAFETY: Fix all nullable/non-null conflicts – no `!!`, no null literals as non-null arguments
- UNRESOLVED REFERENCE: The method or property does not exist on that class. Check the superclass chain.
  Examples: `setFocusPainted(boolean)` is ONLY on `AbstractButton` (`JButton`, `JCheckBox`, etc.), NOT on `JPanel` or `JFrame`. `setBorderPainted(boolean)` — same restriction.
  Fix: either move the call to an `AbstractButton` subclass, or remove it if it does not apply to this component type.
- ACCIDENTAL OVERRIDE (same JVM signature): A Kotlin `var x` / `var y` property auto-generates `getX()` / `getY()` JVM methods, which clash with the identically-named methods inherited from `java.awt.Component`. Fix: annotate the conflicting property with `@JvmField` to suppress getter/setter generation (`@JvmField var x = 0`, `@JvmField var y = 0`), OR rename the property to avoid the clash (`posX`, `posY`, `tileWidth`, etc.). The same clash can occur for `width`, `height`, `name`, `size`, `bounds`, `location`, `parent`, `font`, `background`, `foreground` — apply `@JvmField` or rename for any of these as well.
- NO PACKAGE DECLARATION: Do NOT add a `package` declaration. If the code has one, remove it. A package declaration renames the entry point from `MainKt` to `<package>.MainKt`, causing `ClassNotFoundException: MainKt` at runtime.
- TOP-LEVEL MAIN FUNCTION: The `main` function must be at the top level of the file — NOT inside a class, object, or companion object.

CODE:
```kotlin
{code}
```

COMPILATION ERRORS:
{errors}

Return EXCLUSIVELY the corrected Kotlin code as a ```kotlin code block. No explanatory text.

## tooling._section.runtimeerror
Runtime Error Retry

## tooling.runtimeErrorRetry
The following Kotlin program compiled successfully but crashes immediately on startup.
Analyse the runtime error and return the fully corrected code.

RULES:
- Fix ONLY the reported runtime error — do NOT refactor, rename, restructure, or change any logic that is unrelated to the crash.
- Use ONLY standard Kotlin and Java SE APIs
- SWING / EDT THREADING (CRITICAL for GUI crashes): If the crash involves Swing initialization or rendering:
  * Ensure ALL Swing component creation happens inside `SwingUtilities.invokeLater { }`. Creating Swing components outside the EDT causes intermittent crashes and `IllegalStateException`.
  * Game loops must use `javax.swing.Timer` — NOT a raw thread with `while (true) { Thread.sleep(x); repaint() }`.
  * Set `isFocusable = true` and call `requestFocusInWindow()` after `frame.isVisible = true`.
- NULL SAFETY (CRITICAL): This is the most common cause of runtime crashes.
  * No `!!` (not-null assertion) – use safe access or explicit initialisation instead
  * No null literals as arguments to non-null parameters
  * Arrays and lists must not contain null elements if the element type is non-null
  * Enum array access: `Enum.values()[index]` can never be null – but ensure the index is valid
- KOTLIN INITIALIZATION ORDER (CRITICAL): Properties in a class are initialized top-to-bottom.
  * If a property initializer calls a method (e.g. `var figure = nextFigure()`), all properties that method reads must be declared BEFORE it.
  * Fix: move backing fields (Random, collections, config values) to the TOP of the class, above any property initialized by a method call.
  * Example crash pattern: `var x = buildX()` declared before `val rng = Random.Default` where `buildX()` uses `rng`.
  * COMMON GAME PATTERN: When a class has both a "current piece" and a "next piece", the `next` field MUST be declared before `current`, because the `spawnCurrent()` initializer reads `next`. WRONG: `var current = spawnPiece()` then `var next = drawFromBag()`. CORRECT: `var next = drawFromBag()` then `var current = spawnPiece()`.
- All referenced classes and methods must exist
- Import all used classes explicitly
- NO PACKAGE DECLARATION: Do NOT add a `package` declaration. If the code has one, remove it. A package declaration renames the entry point from `MainKt` to `<package>.MainKt`, causing `ClassNotFoundException: MainKt` at runtime.
- TOP-LEVEL MAIN FUNCTION: The `main` function must be at the top level of the file — NOT inside a class, object, or companion object.

CODE:
```kotlin
{code}
```

RUNTIME ERROR:
{errors}

Return EXCLUSIVELY the corrected Kotlin code as a ```kotlin code block. No explanatory text.
