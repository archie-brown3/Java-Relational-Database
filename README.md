# Java Relational Database

A relational database engine built from scratch in Java 17. Parses a custom SQL dialect, executes queries against an in-memory table model, and persists data to disk as tab-separated files. Client-server architecture over TCP sockets.

## Architecture

```
┌──────────────┐     TCP (port 8888)     ┌──────────────┐
│   DBClient   │ ◄──────────────────────► │   DBServer   │
└──────────────┘                          └──────┬───────┘
                                                 │
                                    ┌────────────┴───────────┐
                                    │    handleCommand()     │
                                    └────────────┬───────────┘
                                                 │
                              ┌──────────────────┴──────────────────┐
                              │                                     │
                    ┌─────────┴─────────┐                 ┌─────────┴─────────┐
                    │   QueryParser     │                 │  QueryExecutor    │
                    │   (no side fx)    │    Command      │  (Visitor impl)   │
                    │                   │ ──────────────► │                   │
                    │  parse(sql) → Cmd │                 │  visit(Cmd) → str │
                    └───────────────────┘                 └─────────┬─────────┘
                                                                   │
                                                    ┌──────────────┴──────────────┐
                                                    │      ExecutionContext       │
                                                    │  storageFolderPath          │
                                                    │  currentDatabase            │
                                                    └─────────────────────────────┘
                                                                   │
                                          ┌────────────────────────┼────────────────────────┐
                                          │                        │                        │
                                   ┌──────┴──────┐         ┌──────┴──────┐         ┌──────┴──────┐
                                   │    Table    │         │     Row     │         │   Column    │
                                   │  in-memory  │         │  id + data  │         │  name, type │
                                   └─────────────┘         └─────────────┘         └─────────────┘
```

## Design Philosophy

**Separation of concerns** drives the entire architecture. The `QueryParser` is a pure function: it accepts a SQL string and returns an immutable command object. It has no knowledge of files, sockets, or runtime state. This keeps the parser testable in isolation and makes the grammar easy to extend without touching execution logic.

**The Visitor pattern** decouples command definitions from command execution. Each SQL command is a sealed record implementing `Command`. The `QueryExecutor` implements `CommandVisitor<String>`, providing a `visit()` method for each command type. There are no `instanceof` chains, no switch-on-type, and adding a new command requires only adding a record and a visitor method — the compiler enforces exhaustive handling through the sealed type hierarchy. This promotes loose coupling between the parser layer and the execution layer.

**Single responsibility** is enforced at the class level. `Table` manages an in-memory collection of rows and columns with no I/O. `Row` owns a single record's key-value data. `Column` is a value object carrying name and type metadata. `QueryExecutor` owns all file I/O and delegates to these domain objects for in-memory operations. No class mixes persistence with business logic.

**Immutability where possible.** Parsed commands are Java records — shallowly immutable, trivially thread-safe, and safe to pass across layers. The `ExecutionContext` is the only mutable object in the system, carrying the current database selection and storage root path. All other state changes flow through explicit save operations.

**Single-user, file-based persistence.** Each database is a directory. Each table is a `.tab` file — human-readable TSV with an auto-incrementing `id` column as the first field. This design prioritises debuggability over throughput: you can inspect the database state with `cat`, diff it with `git`, and recover from corruption by hand. The engine loads an entire table into memory on first access and writes it back on mutation, which is simple and predictable for the target workload.

## Supported Commands

| Command | Syntax |
|---------|--------|
| `USE` | `USE <database>;` |
| `CREATE DATABASE` | `CREATE DATABASE <name>;` |
| `DROP DATABASE` | `DROP DATABASE <name>;` |
| `CREATE TABLE` | `CREATE TABLE <name> (<col1>, <col2>, ...);` |
| `DROP TABLE` | `DROP TABLE <name>;` |
| `INSERT` | `INSERT INTO <table> VALUES (<v1>, <v2>, ...);` |
| `SELECT` | `SELECT [DISTINCT] <*\|cols> FROM <table> [WHERE <cond>] [GROUP BY <cols>] [ORDER BY <cols> [ASC\|DESC]] [LIMIT <n> [OFFSET <m>]];` |
| `UPDATE` | `UPDATE <table> SET col=val, ... WHERE <cond>;` |
| `DELETE` | `DELETE FROM <table> WHERE <cond>;` |
| `ALTER` | `ALTER TABLE <table> ADD\|DROP <col>;` |
| `JOIN` | `JOIN <t1> AND <t2> ON <c1> AND <c2>;` |
| `LEFT JOIN` | `LEFT JOIN <t1> AND <t2> ON <c1> AND <c2>;` |

**SELECT features:**
- `DISTINCT` — deduplicate result rows
- `WHERE` — standard comparison operators (`==`, `!=`, `>`, `<`, `>=`, `<=`, `LIKE`), combined with `AND`/`OR`, full parenthesization support
- `ORDER BY` — single or multi-column, `ASC`/`DESC` per column, numeric-aware sorting
- `GROUP BY` — single or multi-column, with `COUNT(*)`, `SUM(col)`, `AVG(col)` aggregates
- `LIMIT n [OFFSET m]` — pagination
- `NULL` — full SQL semantics (`IS NULL`, `IS NOT NULL`; `col == NULL` returns false)

**WHERE conditions** support `==`, `!=`, `>`, `<`, `>=`, `<=`, `LIKE` (with `%` wildcards), `IS NULL`, `IS NOT NULL`, combined with `AND`/`OR` and arbitrary parenthesization. Numeric and string comparison with automatic type detection.

All commands return either `[OK]` or `[ERROR]` on the first line.

### Why This SQL Dialect

This engine uses a custom SQL-like syntax, not ANSI SQL. The design choices are intentional:

- **`==` for equality** rather than `=` — avoids ambiguity with assignment in the SET clause
- **`USE <database>;`** as an explicit connection state mechanism — simpler than connection strings for a single-user engine
- **File-based storage** in `.tab` files — each table is one TSV file, making data trivially inspectable with any text editor
- **`[OK]`/`[ERROR]` protocol** — a predictable, parseable response format that any TCP client can consume without a stateful protocol parser

The parser handles standard SQL-isms like semicolons, quoted values, and case-insensitive keywords, so the surface feels familiar even where the underlying syntax diverges.

## Quickstart

### Prerequisites
- Java 17+
- Maven (wrapper included: `./mvnw`)

### Build and Test
```bash
./mvnw compile
./mvnw test        # 66 tests, all passing
```

### Run the Server
```bash
./mvnw exec:java@server
# Server starts on port 8888
```

### Connect with the Client
```bash
./mvnw exec:java@client
SQL:>
```

### Example Session
```
SQL:> CREATE DATABASE school;
[OK]
SQL:> USE school;
[OK]
SQL:> CREATE TABLE marks (name, score, grade);
[OK]
SQL:> INSERT INTO marks VALUES ('Alice', 92, 'A');
[OK]
SQL:> INSERT INTO marks VALUES ('Bob', 74, 'B');
[OK]
SQL:> INSERT INTO marks VALUES ('Charlie', 55, 'C');
[OK]
SQL:> INSERT INTO marks VALUES ('Diana', 74, 'B');
[OK]
SQL:> INSERT INTO marks VALUES ('Eve', NULL, 'F');
[OK]
SQL:> SELECT * FROM marks;
[OK]
id      name    score   grade
1       Alice   92      A
2       Bob     74      B
3       Charlie 55      C
4       Diana   74      B
5       Eve             F
SQL:> SELECT name, score FROM marks WHERE score >= 70 ORDER BY score DESC, name ASC;
[OK]
name    score
Alice   92
Bob     74
Diana   74
SQL:> SELECT grade, COUNT(*) FROM marks GROUP BY grade ORDER BY grade ASC;
[OK]
grade   COUNT(*)
A       1
B       2
C       1
F       1
SQL:> SELECT DISTINCT score FROM marks WHERE score IS NOT NULL ORDER BY score ASC LIMIT 2;
[OK]
score
55
74
SQL:> SELECT name FROM marks WHERE (grade == 'A' OR grade == 'B') AND score >= 80;
[OK]
name
Alice
```

### Example with JOINs
```
SQL:> CREATE TABLE depts (code, dept_name);
[OK]
SQL:> INSERT INTO depts VALUES ('CS', 'Computer Science');
[OK]
SQL:> INSERT INTO depts VALUES ('MATH', 'Mathematics');
[OK]
SQL:> CREATE TABLE students (name, dept_code);
[OK]
SQL:> INSERT INTO students VALUES ('Alice', 'CS');
[OK]
SQL:> INSERT INTO students VALUES ('Bob', 'MATH');
[OK]
SQL:> INSERT INTO students VALUES ('Charlie', 'PHYSICS');
[OK]
SQL:> JOIN students AND depts ON dept_code AND code;
[OK]
id      students.name   students.dept_code      depts.dept_name
1       Alice   CS      Computer Science
2       Bob     MATH    Mathematics
SQL:> LEFT JOIN students AND depts ON dept_code AND code;
[OK]
id      students.name   students.dept_code      depts.dept_name
1       Alice   CS      Computer Science
2       Bob     MATH    Mathematics
3       Charlie PHYSICS
```

## Project Structure

```
src/main/java/db/engine/
├── DBServer.java       # TCP server, entry point
├── DBClient.java       # TCP client, REPL interface
├── QueryParser.java    # BNF-based SQL parser -> Command records
├── QueryExecutor.java  # Visitor pattern execution engine
├── Table.java          # In-memory table representation
├── Row.java            # Row (id + column-value map)
├── Column.java         # Column metadata (name, type)
└── grammar.md          # BNF grammar specification

src/test/java/db/engine/
├── ExampleDBTests.java       # Integration tests
├── ComprehensiveDBTests.java # Full command coverage (62 tests)
├── people.tab                # Test fixture
└── sheds.tab                 # Test fixture (JOIN data)

.github/workflows/
└── ci.yml              # GitHub Actions: compile + test on push
```

## Response Protocol

Every command returns either `[OK]` or `[ERROR]` on the first line. Query results follow `[OK]` as tab-separated rows, with column headers on the first data line. This makes the protocol predictable for any client implementation.

## Known Limitations

This is a deliberately minimal database engine. The following are intentionally out of scope:

- Subqueries and nested SELECT statements
- RIGHT JOIN and FULL OUTER JOIN (INNER and LEFT JOIN are supported)
- HAVING clause (WHERE can filter before grouping)
- Index structures — all queries are O(n) table scans
- Multi-user concurrency — single synchronous connection at a time
- Transaction support (BEGIN/COMMIT/ROLLBACK)

**Design tradeoffs:**
- Integer IDs are monotonic with no reuse after deletes
- File-based persistence prioritises human readability over I/O throughput
- Column types are tracked (STRING, INTEGER, FLOAT, BOOLEAN) but not enforced at insert time — all values are stored as strings and coerced at comparison time
