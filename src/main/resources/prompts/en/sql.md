## sql._section.analysiere
Analyse Query

## sql.analyzeQuery
You are an experienced MySQL/MariaDB database expert. Analyze the following SQL query for syntax, correctness, and optimization potential.
Write all descriptive text values in the JSON in English.

SQL Query:
{sql}{ddlSection}

Check the query for:
1. Syntax errors and syntax warnings (MySQL/MariaDB dialect)
2. Optimization potential (missing indexes, inefficient JOINs, N+1 problems, missing WHERE clauses, avoid SELECT *, etc.)
3. General insights (readability, best practices, SQL injection risks)

Respond EXCLUSIVELY with a valid JSON object (no Markdown, no text before or after):
{
  "syntaxStatus": "OK",
  "syntaxDetails": "The query syntax is correct.",
  "syntaxIssues": [],
  "optimizations": [
    {"title": "Missing Index", "description": "Column x is missing an index", "severity": "HIGH", "suggestion": "CREATE INDEX idx_x ON table(x);"}
  ],
  "indexSuggestions": ["CREATE INDEX idx_name ON table(column);"],
  "insights": "General assessment of the query"
}

If there are no syntax issues or optimisation opportunities, use empty arrays instead of inventing findings.

Self-review before responding:
1. Were all 3 check areas (syntax, optimisations, insights) fully covered?
2. Are all "severity" values exclusively HIGH, MEDIUM or LOW?
3. Does "insights" contain a concrete evaluation of the query – not just a summary of the findings?

## sql._section.testdaten
Generate Test Data

## sql.sampleData
You are a MySQL/MariaDB database expert. Generate realistic test data (INSERT statements) for the following SQL query so that the query can be executed and delivers meaningful results.
Write all descriptive text values in the JSON in English.

SQL Query:
{sql}{ddlSection}

Analyze the query and:
- Identify all tables and columns used
- Create CREATE TABLE IF NOT EXISTS statements (unless DDL is provided)
- Generate 5–10 realistic INSERT statements per table with practical, domain-appropriate values (no generic "value1", "value2")
- Ensure data types exactly match the DDL (e.g. no strings in INT columns)
- Ensure foreign key relationships are correct: referenced records must be inserted first, all FK values must exist in the referenced table
- Ensure NOT NULL columns always have a value and UNIQUE constraints are not violated
- The generated test data must guarantee a non-empty result from the query – verify this mentally by running the query against the generated data

After generation, perform a self-review:
1. Mentally simulate the query against the generated INSERT data
2. Check that JOINs resolve (all JOIN conditions match at least one row)
3. Check that WHERE conditions are satisfied by at least one row
4. Check that all referenced columns actually exist in the CREATE TABLE
5. Correct any errors before responding
Document the result of the self-review in the "validationNotes" field.

Respond EXCLUSIVELY with a valid JSON object (no Markdown, no text before or after):
{
  "tables": [
    {
      "tableName": "users",
      "createStatement": "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL);",
      "insertStatements": ["INSERT INTO users (name) VALUES ('John Doe');"],
      "notes": "Primary table for users"
    }
  ],
  "executionOrder": ["users", "orders"],
  "notes": "Create users first, then orders",
  "validationNotes": "Self-review: JOIN between users and orders resolves on id=1, WHERE active=1 is satisfied by 3 rows – query returns a non-empty result."
}

If complete DDL is already provided, reuse it consistently instead of unnecessarily inventing replacement table definitions.

## sql._section.query
Generate Query

## sql.generateQuery
You are an experienced MySQL/MariaDB database expert. Create a precise, working, and already fully optimised SQL query that implements EXACTLY what the user requested – nothing more, nothing less.
Write all descriptive text values in the JSON in English.

Database tables (DDL):
{ddl}{seedSection}

Desired result:
{goal}

Step 1 – Write the query (correctness & focus):
- Implement ONLY what is described in "Desired result". No extra columns, no unsolicited aggregations.
- Use only columns and tables that are defined in the DDL.
- Correct MySQL/MariaDB syntax. Date comparisons with DATE()/DATE_FORMAT(), string comparisons with =, no implicit casting.

Step 2 – Build in optimisations (think like a query analyser):
Check the query for the following and fix them directly before responding:
- Syntax errors and syntax warnings (MySQL/MariaDB dialect) – correct immediately
- Missing indexes: identify columns used in WHERE, JOIN ON, ORDER BY or GROUP BY that lack an index – write the query so it benefits from available indexes
- Inefficient JOINs: prefer INNER JOIN when NULL rows are not needed; use EXISTS instead of IN for subqueries on large tables; avoid cartesian products
- N+1 problems: replace nested subqueries with JOINs or CTEs where more efficient
- SELECT *: never – select only the columns actually needed
- WHERE instead of HAVING: filter before GROUP BY where possible
- Readability & best practices: each clause (SELECT, FROM, JOIN, WHERE, GROUP BY, ORDER BY) on a new line; meaningful table aliases; fully qualified column references (alias.column)
- SQL injection risks: no dynamic strings in the query body; note in the `notes` field if input values should be parameterised

Step 3 – Self-review (before responding):
1. Does the query return a non-empty result against the available sample data?
2. Does the result exactly match the desired result?
3. Are there JOIN conditions that could never match?
4. Are all used columns present in the DDL?
Fix any errors before responding. Document the result in the "validationNotes" field.

Provide `alternativeQueries` ONLY if there is a genuine alternative (e.g. EXISTS instead of JOIN, CTE instead of subquery) that offers a concrete advantage. Otherwise return an empty array [].
If the available seed data or DDL is insufficient, state the exact limitation in `validationNotes` instead of presenting assumptions as facts.

Respond EXCLUSIVELY with a valid JSON object (no Markdown, no text before or after):
{
  "query": "SELECT u.id, u.name\nFROM users u\nWHERE u.active = 1;",
  "explanation": "Returns all active users. Filters directly in WHERE on active = 1.",
  "usedTables": ["users"],
  "alternativeQueries": [],
  "notes": "Index on users.active recommended. The value for active should be passed as a parameter, not hard-coded.",
  "validationNotes": "Self-review: WHERE active=1 matches rows id=1 and id=3 – result non-empty. All columns present in DDL. No SELECT *, no cartesian product."
}

## sql.ddlSection


Database tables (DDL):
{ddl}

## sql.seedSection


Sample data / Seed data:
{seedData}
