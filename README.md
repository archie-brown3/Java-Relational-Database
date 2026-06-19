# Java Relational Database

A relational database engine built from scratch in Java 17 with a custom SQL parser, query executor, and file-based persistence layer. Communicates over TCP sockets with a client-server architecture.

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

**Key design decisions:**
- **Visitor pattern** — Parser produces immutable `Command` records. Executor visits them. Parser has zero side effects, executor owns all I/O and state.
- **Sealed type hierarchy** — All 11 command types are sealed records implementing `Command`, with exhaustive visitor dispatch. No `instanceof` chains.
- **File-based storage** — Each database is a directory, each table is a `.tab` (TSV) file with an `id` column. Survives server restarts.
- **TCP client-server** — Server listens on port 8888, handles multiple sequential connections.

## Supported Commands

| Command | Status | Syntax |
|---------|--------|--------|
| `USE` | ✅ | `USE <database>;` |
| `CREATE DATABASE` | ✅ | `CREATE DATABASE <name>;` |
| `DROP DATABASE` | ✅ | `DROP DATABASE <name>;` |
| `CREATE TABLE` | ✅ | `CREATE TABLE <name> (<col1>, <col2>, ...);` |
| `DROP TABLE` | ✅ | `DROP TABLE <name>;` |
| `INSERT` | ✅ | `INSERT INTO <table> VALUES (<v1>, <v2>, ...);` |
| `SELECT` | ✅ | `SELECT <*\|cols> FROM <table> [WHERE <cond>];` |
| `UPDATE` | ✅ | `UPDATE <table> SET <col>=<val> WHERE <cond>;` |
| `DELETE` | ✅ | `DELETE FROM <table> WHERE <cond>;` |
| `JOIN` | ✅ | `JOIN <t1> AND <t2> ON <attr1> AND <attr2>;` |
| `ALTER` | ✅ | `ALTER TABLE <name> ADD\|DROP <attribute>;` |

### WHERE Conditions (SELECT)

Supports `==`, `!=`, `>`, `<`, `>=`, `<=`, `LIKE` (with `%` wildcards), combined with `AND`/`OR`. Numeric and string comparison with automatic type detection.

```sql
SELECT * FROM marks WHERE mark >= 60 AND pass == TRUE;
SELECT name FROM users WHERE name LIKE 'S%';
```

## Quickstart

### Prerequisites
- Java 17+
- Maven (wrapper included: `./mvnw`)

### Build & Test
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
id	name	score	grade
1	Alice	92	A
2	Bob	74	B
3	Charlie	55	C
SQL:> SELECT name, score FROM marks WHERE score >= 70;
[OK]
name	score
Alice	92
Bob	74
```

## Project Structure

```
src/main/java/db/engine/
├── DBServer.java       # TCP server, entry point
├── DBClient.java       # TCP client, REPL interface
├── QueryParser.java    # BNF-based SQL parser → Command records
├── QueryExecutor.java  # Visitor pattern execution engine
├── Table.java          # In-memory table representation
├── Row.java            # Row (id + column→value map)
├── Column.java         # Column metadata (name, type, FK refs)
└── grammar.md          # BNF grammar specification

src/test/java/db/engine/
├── ExampleDBTests.java       # Integration tests
├── ComprehensiveDBTests.java # Full command coverage
├── people.tab                # Test fixture
└── sheds.tab                 # Test fixture (JOIN data)
```

## Response Protocol

Every command returns either `[OK]` or `[ERROR]` on the first line. Query results follow `[OK]` as tab-separated rows. This makes the protocol predictable for any client.

## Known Limitations

- No index structures — queries are O(n) table scans
- Single-user: one connection at a time
- Integer IDs are monotonic (no reuse after deletes)
- No nested WHERE parenthesization (AND/OR are left-associative)
- JOIN only supports single-column inner joins

## Lessons Learned

- **Separate parse from execute early.** Adding the visitor pattern mid-development was the right call — it eliminated parser-executor coupling and made testing trivial.
- **Sealed types > enums for commands.** Each command carries different fields (table name, values list, condition string). Sealed records give type safety without forcing everything into a single shape.
- **File-based persistence is surprisingly robust for single-user workloads.** TSV is human-readable, diffable, and trivial to debug. The trade-off is write amplification on every mutation.
