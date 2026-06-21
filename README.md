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

**The Visitor pattern** decouples command definitions from command execution. Each of the 11 SQL commands is a sealed record implementing `Command`. The `QueryExecutor` implements `CommandVisitor<String>`, providing a `visit()` method for each command type. There are no `instanceof` chains, no switch-on-type, and adding a new command requires only adding a record and a visitor method — the compiler enforces exhaustive handling through the sealed type hierarchy. This promotes loose coupling between the parser layer and the execution layer.

**Single responsibility** is enforced at the class level. `Table` manages an in-memory collection of rows and columns with no I/O. `Row` owns a single record's key-value data. `Column` is a value object carrying name, type, and foreign key metadata. `QueryExecutor` owns all file I/O and delegates to these domain objects for in-memory operations. No class mixes persistence with business logic.

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
| `SELECT` | `SELECT <*|cols> FROM <table> [WHERE <cond>];` |
| `UPDATE` | Done | `UPDATE <table> SET col=val, ... WHERE <cond>;` |
| `DELETE` | Done | `DELETE FROM <table> WHERE <cond>;` |
| `ALTER` | Done | `ALTER TABLE <table> ADD\|DROP <col>;` |
| `JOIN` | Done | `JOIN <t1> AND <t2> ON <c1> AND <c2>;` |
| `ORDER BY` | Done | `SELECT ... FROM ... ORDER BY col [ASC\|DESC];` |
| `GROUP BY` | Done | `SELECT col, AGG(col) FROM ... GROUP BY col;` |
| Aggregates | Done | `COUNT(*)`, `SUM(col)`, `AVG(col)` |

All 11 commands plus ORDER BY and GROUP BY aggregations are fully implemented. Every response begins with `[OK]` or `[ERROR]`.

### Why This SQL Dialect

This engine uses a custom SQL-like syntax, not ANSI SQL. The design choices are intentional:

- **`==` for equality** rather than `=` — avoids ambiguity with assignment in the SET clause
- **`USE <database>;`** as an explicit connection state mechanism — simpler than connection strings for a single-user engine
- **File-based storage** in `.tab` files — each table is one TSV file, making data trivially inspectable with any text editor
- **`[OK]`/`[ERROR]` protocol** — a predictable, parseable response format that any TCP client can consume without a stateful protocol parser

The parser handles standard SQL-isms like semicolons, quoted values, and case-insensitive keywords, so the surface feels familiar even where the underlying syntax diverges.

### WHERE Conditions

Supports `==`, `!=`, `>`, `<`, `>=`, `<=`, `LIKE` (with `%` wildcards), combined with `AND`/`OR`. Numeric and string comparison with automatic type detection.

```sql
SELECT * FROM marks WHERE mark >= 60 AND pass == TRUE;
SELECT name FROM users WHERE name LIKE 'S%';
```

## Quickstart

### Prerequisites
- Java 17+
- Maven (wrapper included: `./mvnw`)

### Build and Test
```bash
./mvnw compile
./mvnw test
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
SQL:> SELECT * FROM marks;
[OK]
id      name    score   grade
1       Alice   92      A
2       Bob     74      B
3       Charlie 55      C
SQL:> SELECT name, score FROM marks WHERE score >= 70;
[OK]
name    score
Alice   92
Bob     74
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
├── ComprehensiveDBTests.java # Full command coverage (35 tests)
├── people.tab                # Test fixture
└── sheds.tab                 # Test fixture (JOIN data)
```

## Response Protocol

Every command returns either `[OK]` or `[ERROR]` on the first line. Query results follow `[OK]` as tab-separated rows, with column headers on the first data line. This makes the protocol predictable for any client implementation.

## Known Limitations

This is a deliberately minimal database engine focusing on core relational operations and SQL parsing. The following features standard in production SQL engines are intentionally out of scope:

**Missing SQL features**
- Subqueries and nested SELECT statements
- Multiple JOIN types (LEFT, RIGHT, OUTER) — only INNER JOIN
- DISTINCT, LIMIT/OFFSET, HAVING
- NULL-aware operations (NULL is treated as an empty string)
- Multi-column ORDER BY and GROUP BY
- Index structures — queries are O(n) table scans
- Multi-user: single synchronous connection at a time
- Transaction support (BEGIN/COMMIT/ROLLBACK)

**Design tradeoffs**
- Integer IDs are monotonic with no reuse after deletes
- No nested WHERE parenthesization (AND/OR are left-associative)
- File-based persistence prioritises human readability over I/O throughput
- Column types are tracked but not enforced at insert time (all values stored as strings)
