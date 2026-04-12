# Java Relational DB - Portfolio Readiness Checklist

## Current Estimated Completion

Estimated portfolio readiness: **22%**

Rationale for this estimate:
- Core architecture direction is good (parser/visitor/executor split is started).
- Build currently compiles.
- Significant command parsing and execution logic is still stubbed.
- Robust persistence, validation, error handling, and test depth are not yet complete.
- Documentation and developer experience are not yet portfolio-level.

## Milestone 1 - Functional Core (Coursework Baseline)

### Parser completeness
- [ ] Implement full BNF parsing for `USE`.
- [ ] Implement full BNF parsing for `CREATE DATABASE`.
- [ ] Implement full BNF parsing for `CREATE TABLE` with attribute list validation.
- [ ] Implement full BNF parsing for `DROP DATABASE`.
- [ ] Implement full BNF parsing for `DROP TABLE`.
- [ ] Implement full BNF parsing for `ALTER TABLE ... ADD|DROP ...`.
- [ ] Implement full BNF parsing for `INSERT INTO ... VALUES (...)` with quoted strings.
- [ ] Implement full BNF parsing for `SELECT ... FROM ... [WHERE ...]`.
- [ ] Implement full BNF parsing for `UPDATE ... SET ... WHERE ...`.
- [ ] Implement full BNF parsing for `DELETE FROM ... WHERE ...`.
- [ ] Implement full BNF parsing for `JOIN ... AND ... ON ... AND ...`.
- [ ] Enforce semicolon requirement and robust whitespace handling across all commands.
- [ ] Reject reserved keyword misuse as identifiers.

### Executor command coverage
- [ ] Implement `visit(UseCommand)` to select active database in context.
- [ ] Implement `visit(CreateDatabaseCommand)` to create directory and return `[OK]` or `[ERROR]`.
- [ ] Implement `visit(CreateTableCommand)` to create `.tab` with required header including `id`.
- [ ] Implement `visit(DropDatabaseCommand)` recursive delete with proper safety checks.
- [ ] Implement `visit(DropTableCommand)` to delete `.tab` in selected DB.
- [ ] Implement `visit(AlterCommand)` add/drop column behavior with constraints.
- [ ] Implement `visit(InsertCommand)` load-modify-save workflow.
- [ ] Implement `visit(SelectCommand)` projection + filtering + output formatting.
- [ ] Implement `visit(UpdateCommand)` predicate match + mutation + save.
- [ ] Implement `visit(DeleteCommand)` predicate match + removal + save.
- [ ] Implement `visit(JoinCommand)` inner join with required output schema.

### Execution helpers and persistence flow
- [ ] Finish `resolveTableFile` helper implementation.
- [ ] Finish `withTableRead` helper implementation.
- [ ] Finish `withTableWrite` helper implementation.
- [ ] Finish `requireCurrentDatabase` helper implementation.
- [ ] Ensure every mutating command persists to disk.
- [ ] Ensure every read command does not persist unless required.

## Milestone 2 - Correctness and Robustness

### Error handling and protocol
- [ ] Ensure every response begins with `[OK]` or `[ERROR]`.
- [ ] Replace placeholder runtime exceptions with controlled user-facing errors.
- [ ] Catch and map IO failures to clear `[ERROR]` messages.
- [ ] Validate malformed commands do not crash server loop.
- [ ] Validate illegal operations return deterministic errors.

### Data integrity
- [ ] Guarantee stable monotonic `id` behavior (no id reuse after deletes).
- [ ] Enforce duplicate column name prevention.
- [ ] Prevent dropping `id` column.
- [ ] Prevent updating `id` values.
- [ ] Handle missing table/database references safely.
- [ ] Revisit and fix foreign key validation logic in table insert flow.

### Query semantics
- [ ] Implement `WHERE` comparators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `LIKE`.
- [ ] Implement boolean condition composition: `AND`, `OR`, parenthesized expressions.
- [ ] Ensure `SELECT *` returns columns in stored order.
- [ ] Ensure explicit `SELECT colA, colB` respects requested projection order.
- [ ] Ensure JOIN output columns follow brief naming/order requirements.

## Milestone 3 - Tests (Portfolio Standard)

### Unit tests
- [ ] Add parser unit tests for each command variant and malformed syntax.
- [ ] Add tests for identifier validation and reserved keywords.
- [ ] Add tests for condition parser and comparator behavior.

### Integration tests
- [ ] Add end-to-end tests for all command types.
- [ ] Add persistence tests across server restart for create/insert/update/delete.
- [ ] Add filesystem failure simulation tests where feasible.
- [ ] Add edge cases (empty tables, no-match queries, invalid references).

### Regression and quality
- [ ] Add regression tests for every bug fixed during development.
- [ ] Add high-level transcript-style tests matching expected user workflows.

## Milestone 4 - Architecture and Code Quality

### Design quality
- [ ] Remove duplicate path-building logic by centralizing in helpers.
- [ ] Keep parser pure (no execution side effects).
- [ ] Keep executor responsible for runtime state and IO only.
- [ ] Keep `Table` as in-memory domain logic only.
- [ ] Remove remaining placeholder comments by replacing with concrete behavior.

### Readability and maintainability
- [ ] Standardize naming conventions and capitalization across classes/methods.
- [ ] Remove dead code and debug-only println statements.
- [ ] Add concise JavaDoc on public methods and key helpers.
- [ ] Ensure consistent response message style.

## Milestone 5 - Portfolio Polish

### Developer experience
- [ ] Add a high-quality README with architecture diagram and quickstart.
- [ ] Add command examples and expected outputs.
- [ ] Add explicit assumptions and known limitations section.
- [ ] Add sample datasets and reproducible demo script.

### Engineering hygiene
- [ ] Add static analysis and formatting checks in Maven workflow.
- [ ] Add CI pipeline for compile + tests.
- [ ] Ensure clean repository state and meaningful commit history.

### Showcase artifacts
- [ ] Add architecture notes explaining parser + visitor dispatch decisions.
- [ ] Add complexity/performance notes for key operations.
- [ ] Add "lessons learned" section for design trade-offs.
- [ ] Record a short terminal demo showing create/use/table lifecycle.

## Recommended Implementation Order

1. Complete parser+executor for `USE`, `CREATE DATABASE`, `CREATE TABLE`, `INSERT`, `SELECT`.
2. Stabilize persistence and error handling for these commands.
3. Add tests for these commands before expanding feature set.
4. Implement `DELETE`, `UPDATE`, `ALTER`, `DROP`, then `JOIN`.
5. Finish polish and documentation after behavioral completeness.
