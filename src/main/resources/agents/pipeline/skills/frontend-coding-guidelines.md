# Frontend Coding Guidelines

This file is the authoritative source of coding standards for all JavaScript (and general frontend) code produced in this pipeline.
It is embedded in the system prompt of **Faye** (frontend implementation).

---

## General Principles

- **⚠️ ABSOLUTELY NO COMMENTS** — Comments of any kind are **strictly forbidden** in all frontend code files. This includes inline comments (`//`), block comments (`/* … */`), and HTML comments (`<!-- -->`). Code must be self-explanatory through clear naming and structure alone. Violating this rule is a hard error.
- All identifiers (variable names, function names, CSS class names in JS) are written in **English**.
- Every piece of code must be easy for a human to read at a glance — no clever one-liners that require mental decoding.
- Think through every implementation step before writing code. Write code that makes the intent obvious.

---

## JavaScript

### Naming

- Use clear, descriptive names that state **what** the variable holds or **what** the function does.
- Prefer longer, unambiguous names over short cryptic abbreviations.
- Function names describe an **action**: `loadDirectoryContents`, `showFilePreview`, `handleConfirmClick`.
- Boolean variables and functions start with `is`, `has`, or `can`: `isModalOpen`, `hasValidInput`, `canSubmitForm`.
- Event handler functions start with `handle` or `on`: `handleFileSelected`, `onUploadSuccess`.
- Do not use generic names like `data`, `result`, `obj`, `temp`, `val`, `el`, `e` (except `event` for event objects).

### Structure and Readability

- **No complex nesting.** Maximum two levels of nesting inside a function body (e.g. one `if` inside a loop). If you need more depth, extract a named function.
- Each function does **one thing only**. If a function needs an "and" in its description, split it.
- Keep functions short — aim for functions that fit on one screen without scrolling.
- Use **early returns** to handle edge cases and guard clauses at the top of a function. This avoids deeply nested `if/else` blocks.

```js
// ❌ WRONG — deep nesting, hard to follow
function handleFileSelection(input) {
  if (input) {
    if (input.files && input.files[0]) {
      var file = input.files[0];
      if (isFileTypeAllowed(file)) {
        if (file.size <= MAX_FILE_SIZE_BYTES) {
          loadFile(file);
        } else {
          alert('File too large');
        }
      } else {
        alert('Invalid type');
      }
    }
  }
}

// ✅ CORRECT — flat, readable, early returns
function handleFileSelection(input) {
  var file = input && input.files && input.files[0];
  if (!file) return;

  if (!isFileTypeAllowed(file)) {
    alert('Invalid file type.');
    return;
  }

  if (file.size > MAX_FILE_SIZE_BYTES) {
    alert('File is too large.');
    return;
  }

  loadFile(file);
}
```

### Variables and State

- Declare each variable on its own line. Never combine declarations with a comma chain.
- Assign variables as close as possible to where they are first used.
- Name DOM element variables with a descriptive suffix: `submitButton`, `errorMessageEl`, `fileNameLabel`.

```js
// ❌ WRONG — unclear names, combined declaration
var btn, lbl, el;

// ✅ CORRECT — one per line, descriptive
var submitButton = document.getElementById('submitBtn');
var errorMessageEl = document.getElementById('errorMessage');
var fileNameLabel = document.getElementById('fileName');
```

### Promises and Async

- Do **not** chain more than one `.then()` without extracting intermediate steps into named functions.
- Name the argument of every `.then()` callback to reflect what it contains, never use single-letter names.

```js
// ❌ WRONG — chained then with anonymous functions and unclear names
fetch(url).then(function(r) { return r.json(); }).then(function(d) { process(d); }).catch(function(e) { log(e); });

// ✅ CORRECT — each step is clear and on its own line
fetch(url)
  .then(function(response) { return response.json(); })
  .then(function(fileList) { renderFileList(fileList); })
  .catch(function(error) { showLoadError(error.message); });
```

### DOM Manipulation

- Always check whether a DOM element exists before accessing its properties.
- Use one descriptive variable per element — do not chain property accesses on query results.
- Prefer separate `if`-guards per element over combined short-circuit assignments in a single line.
- **No HTML string building**: Never construct HTML markup as string concatenation and inject it via `innerHTML`. Prepare named container elements in the Twig template and set only their text content via `element.textContent = …` or update a single, minimal `innerHTML` per container. This keeps markup in Twig, keeps JS readable, and avoids XSS risks.

```js
// ❌ WRONG — builds full HTML in JS
var html = '<p style="color:red;"><strong>' + errorCount + ' fehlgeschlagen</strong></p>';
resultContainer.innerHTML = html;

// ✅ CORRECT — containers defined in Twig, JS sets text only
document.getElementById('error-count').textContent = errorCount + ' fehlgeschlagen';
```

```js
// ❌ WRONG — silent failures, unclear what is being modified
if (a && b) { a.textContent = x; b.style.display = 'none'; }

// ✅ CORRECT — each element guarded and modified separately
if (errorMessageEl) errorMessageEl.textContent = message;
if (loadingSpinner) loadingSpinner.style.display = 'none';
```

### Event Handling

- Always use `addEventListener`. Never use inline `onclick` attributes.
- The handler function must be a named function — not an anonymous inline function assigned directly.

### Conditionals

- Avoid complex ternary expressions. Use a plain `if/else` when the result requires more than a simple value swap.
- Never nest ternary operators.

```js
// ❌ WRONG — unreadable nested ternary
var label = isRunning ? 'Running…' : isDone ? 'Done' : isError ? 'Error' : 'Pending';

// ✅ CORRECT — easy to read and extend
var label = 'Pending';
if (isRunning) label = 'Running…';
if (isDone)    label = 'Done';
if (isError)   label = 'Error';
```

### Loops

- Prefer `forEach` over index-based `for` loops when the index is not needed.
- Extract the loop body into a named function when it contains more than 3 lines.

---

## SCSS / CSS

- Follow BEM naming for new class names: `.block__element--modifier`.
- Use existing design tokens, CSS custom properties, or SCSS variables from the project — do not hardcode colours, font sizes, or spacing values.
- Add only what is strictly necessary for this ticket.
- Avoid `!important` unless overriding a third-party library.
- **No duplicated inline styles**: When the same CSS property/value combination appears on two or more elements, extract it into a **shared CSS class** in the project's stylesheet (or a `<style>` block in the Twig template). Inline `style="…"` attributes are only permitted for truly one-off, dynamic values (e.g. a calculated pixel width set by JS at runtime). Check every file produced for duplicated style declarations and consolidate them before finalising.

---

## Twig

- **Twig and JS files must always be saved as UTF-8** (no BOM). This applies to all `.twig`, `.html.twig`, and `.js` files without exception.
- Use `{% extends %}` / `{% block %}` template inheritance. Never duplicate layout markup.
- Keep logic minimal — no PHP-style calculations in templates. Use Twig filters and functions already available in the project.
- Variable names must match exactly what the PHP controller or view-model passes to the template context.
- Escape user-supplied output: `{{ variable|escape }}` (or `{{ variable|e }}`).

---

## Vue.js

- Use the **Composition API** with `<script setup>` syntax (Vue 3).
- Use TypeScript (`<script setup lang="ts">`) if the project already uses TypeScript.
- Prefer `defineProps` / `defineEmits`; avoid the Options API unless the project already uses it consistently.
- Do not introduce new npm packages unless Cole's plan explicitly requires them.
