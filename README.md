# Java Relational Database

A Java client-server relational database prototype focused on core systems design decisions.

## Project Overview
This project explores how object-oriented design can be used to build a small relational database engine with a networked interface. Instead of relying on large frameworks, the implementation uses core Java features to model data, process commands, persist state, and coordinate communication between a server and client.

The codebase is intentionally compact, but it is structured to reflect real software engineering concerns: separation of responsibilities, clarity of object boundaries, and extensibility of behavior over time.

## OOP Design Focus

### 1) Encapsulation of Data and Behavior
Core domain concepts are represented as objects (`Database`, `Table`, `Column`) that own both data and behavior. Rather than passing raw structures everywhere, each object is responsible for maintaining its own invariants (for example, table-level column/value expectations), which reduces accidental coupling and makes bugs easier to isolate.

### 2) Single Responsibility by Class Role
The architecture follows a responsibility-driven split:
- `DBServer` handles socket lifecycle and request/response flow.
- `DBClient` handles user interaction and command submission.
- `CommandHandler` coordinates command interpretation and dispatch.
- `TableStorage` handles persistence concerns for table files.
- Domain classes (`Database`, `Table`, `Column`) model relational concepts.

Each class has a clear reason to change, which keeps implementation changes localized and supports safe iteration.

### 3) Abstraction Between Layers
Networking, command parsing/execution, and file storage are treated as separate layers. This abstraction boundary means command logic can evolve without rewriting connection handling, and persistence details can be changed without rewriting client/server interaction.

### 4) Composition Over Monolithic Logic
Behavior is composed through collaborating objects instead of one large controller class. For example, server components delegate command work to handler/storage/domain objects. This keeps methods smaller, avoids long procedural chains, and makes it easier to add features like additional commands.

### 5) Extensibility Through Domain Modeling
By modeling relational concepts directly as classes, the system is prepared for incremental growth. New command types or richer table features can be added by extending object behavior rather than introducing large rewrites.

### 6) Testability and Maintainability
The project includes JUnit tests and sample table data to support iterative development. OOP boundaries help test individual concerns (e.g., file behavior vs command behavior) and reduce the blast radius of future refactors.

## Design Tradeoffs
- **Readable over over-engineered:** The implementation prioritizes understandable code paths over advanced abstractions.
- **File-backed persistence over DB engine complexity:** Using `.tab` files keeps persistence transparent and inspectable.
- **Simple protocol over protocol richness:** A plain text socket protocol keeps communication easy to reason about during development.
- **Explicit object boundaries over convenience shortcuts:** More classes, but better long-term maintainability and ownership of logic.

## Why This Matters for OOP
This project demonstrates practical OOP beyond syntax: identifying domain entities, assigning responsibilities, minimizing coupling, and creating collaboration patterns that scale as requirements evolve.

## Tech Stack
- Java 17
- Maven
- JUnit 5

## Quick Run (Brief)
From the project root, run server and client in separate terminals:

```bash
./mvnw exec:java@server
./mvnw exec:java@client
```

## Run Tests
```bash
./mvnw test
```

## Project Structure
- `src/main/java/edu/uob` → application source code
- `src/test/java/edu/uob` → test files and sample data
- `databases/` → persisted table data

## Notes
This is a portfolio project designed to highlight object-oriented analysis and design decisions in a backend Java system.
