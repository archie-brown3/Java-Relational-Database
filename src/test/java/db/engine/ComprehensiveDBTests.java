package db.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

public class ComprehensiveDBTests {

    private DBServer server;

    @BeforeEach
    public void setup() {
        server = new DBServer();
    }

    private String sendCommandToServer(String command) {
        return assertTimeoutPreemptively(Duration.ofMillis(2000), () -> {
            return server.handleCommand(command);
        }, "Server took too long to respond");
    }

    private String randomName() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append((char) (97 + (Math.random() * 25.0)));
        return sb.toString();
    }

    // ─── CREATE and USE ───────────────────────────────────────────────

    @Test
    public void testCreateDatabase() {
        String db = randomName();
        String r = sendCommandToServer("CREATE DATABASE " + db + ";");
        assertTrue(r.startsWith("[OK]"), "CREATE DATABASE should return [OK]: " + r);
    }

    @Test
    public void testCreateDuplicateDatabase() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        String r = sendCommandToServer("CREATE DATABASE " + db + ";");
        assertTrue(r.startsWith("[ERROR]"), "Duplicate database should error: " + r);
    }

    @Test
    public void testUseNonexistentDatabase() {
        String r = sendCommandToServer("USE nonexistentdb123;");
        assertTrue(r.startsWith("[ERROR]"), "Using nonexistent DB should error: " + r);
    }

    // ─── CREATE TABLE ──────────────────────────────────────────────────

    @Test
    public void testCreateTable() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        String r = sendCommandToServer("CREATE TABLE t1 (colA, colB);");
        assertTrue(r.startsWith("[OK]"), "CREATE TABLE should return [OK]: " + r);
    }

    @Test
    public void testCreateTableNoDatabase() {
        String r = sendCommandToServer("CREATE TABLE t1 (colA);");
        assertTrue(r.startsWith("[ERROR]"), "CREATE TABLE without USE should error: " + r);
    }

    // ─── INSERT ────────────────────────────────────────────────────────

    @Test
    public void testInsertAndSelect() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE test (name, score);");
        sendCommandToServer("INSERT INTO test VALUES ('Alpha', 100);");
        String r = sendCommandToServer("SELECT * FROM test;");
        assertTrue(r.contains("Alpha"), "SELECT should return inserted row");
        assertTrue(r.contains("100"), "SELECT should return inserted score");
    }

    @Test
    public void testInsertIntoNonexistentTable() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        String r = sendCommandToServer("INSERT INTO ghosts VALUES ('Boo');");
        assertTrue(r.startsWith("[ERROR]"), "INSERT into missing table should error: " + r);
    }

    @Test
    public void testInsertWrongValueCount() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t2 (a, b, c);");
        String r = sendCommandToServer("INSERT INTO t2 VALUES ('x', 'y');");
        assertTrue(r.startsWith("[ERROR]"), "Wrong value count should error: " + r);
    }

    @Test
    public void testInsertWithQuotesAndSpaces() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t3 (city, population);");
        String r = sendCommandToServer("INSERT INTO t3 VALUES ('New York', 8500000);");
        assertTrue(r.startsWith("[OK]"), "INSERT with space in quoted value should work: " + r);
        r = sendCommandToServer("SELECT * FROM t3;");
        assertTrue(r.contains("New York"), "SELECT should return value with space: " + r);
    }

    // ─── SELECT with WHERE ─────────────────────────────────────────────

    @Test
    public void testSelectWhereEquals() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE people (name, age);");
        sendCommandToServer("INSERT INTO people VALUES ('Alice', 30);");
        sendCommandToServer("INSERT INTO people VALUES ('Bob', 25);");
        String r = sendCommandToServer("SELECT * FROM people WHERE age == 30;");
        assertTrue(r.contains("Alice"), "WHERE == should match Alice");
        assertFalse(r.contains("Bob"), "WHERE == should not match Bob");
    }

    @Test
    public void testSelectWhereGreaterThan() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE scores (player, points);");
        sendCommandToServer("INSERT INTO scores VALUES ('P1', 50);");
        sendCommandToServer("INSERT INTO scores VALUES ('P2', 80);");
        String r = sendCommandToServer("SELECT * FROM scores WHERE points > 60;");
        assertTrue(r.contains("P2"), "WHERE > should match P2");
        assertFalse(r.contains("P1"), "WHERE > should not match P1");
    }

    @Test
    public void testSelectWhereLike() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE items (name);");
        sendCommandToServer("INSERT INTO items VALUES ('Apple');");
        sendCommandToServer("INSERT INTO items VALUES ('Apricot');");
        sendCommandToServer("INSERT INTO items VALUES ('Banana');");
        String r = sendCommandToServer("SELECT * FROM items WHERE name LIKE 'Ap%';");
        assertTrue(r.contains("Apple"), "LIKE Ap% should match Apple");
        assertTrue(r.contains("Apricot"), "LIKE Ap% should match Apricot");
        assertFalse(r.contains("Banana"), "LIKE Ap% should not match Banana");
    }

    @Test
    public void testSelectWhereAndOr() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE data (x, y);");
        sendCommandToServer("INSERT INTO data VALUES ('a', 10);");
        sendCommandToServer("INSERT INTO data VALUES ('a', 20);");
        sendCommandToServer("INSERT INTO data VALUES ('b', 10);");
        String r = sendCommandToServer("SELECT * FROM data WHERE x == 'a' AND y == 20;");
        assertTrue(r.contains("a"), "AND condition should match");
        assertFalse(r.contains("b"), "AND condition should exclude b");
    }

    // ─── UPDATE ────────────────────────────────────────────────────────

    @Test
    public void testUpdateSingleRow() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE upd (val);");
        sendCommandToServer("INSERT INTO upd VALUES ('old');");
        String r = sendCommandToServer("UPDATE upd SET val='new' WHERE val == 'old';");
        assertTrue(r.startsWith("[OK]"), "UPDATE should return [OK]: " + r);
        r = sendCommandToServer("SELECT * FROM upd;");
        assertTrue(r.contains("new"), "UPDATE should change value");
        assertFalse(r.contains("old"), "Old value should be gone");
    }

    @Test
    public void testUpdateMultipleRows() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE multi (status);");
        sendCommandToServer("INSERT INTO multi VALUES ('pending');");
        sendCommandToServer("INSERT INTO multi VALUES ('pending');");
        sendCommandToServer("INSERT INTO multi VALUES ('done');");
        String r = sendCommandToServer("UPDATE multi SET status='approved' WHERE status == 'pending';");
        assertTrue(r.contains("2 row(s)"), "Should update 2 rows: " + r);
    }

    @Test
    public void testUpdateIdRejected() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE noid (x);");
        sendCommandToServer("INSERT INTO noid VALUES ('test');");
        String r = sendCommandToServer("UPDATE noid SET id=99 WHERE x == 'test';");
        assertTrue(r.startsWith("[ERROR]"), "Updating id should be rejected: " + r);
    }

    // ─── DELETE ────────────────────────────────────────────────────────

    @Test
    public void testDeleteSingleRow() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE del (name);");
        sendCommandToServer("INSERT INTO del VALUES ('keep');");
        sendCommandToServer("INSERT INTO del VALUES ('remove');");
        String r = sendCommandToServer("DELETE FROM del WHERE name == 'remove';");
        assertTrue(r.contains("1 row(s)"), "Should delete 1 row: " + r);
        r = sendCommandToServer("SELECT * FROM del;");
        assertTrue(r.contains("keep"), "Should keep matching row");
        assertFalse(r.contains("remove"), "Should remove matching row");
    }

    @Test
    public void testDeleteNoMatch() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE nomatch (x);");
        sendCommandToServer("INSERT INTO nomatch VALUES ('a');");
        String r = sendCommandToServer("DELETE FROM nomatch WHERE x == 'z';");
        assertTrue(r.contains("0 row(s)"), "Should delete 0 rows: " + r);
    }

    @Test
    public void testDeleteWithoutWhereRejected() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        // Parse should reject DELETE without WHERE
        String r = sendCommandToServer("DELETE FROM whatever;");
        assertTrue(r.startsWith("[ERROR]"), "DELETE without WHERE should error: " + r);
    }

    // ─── ALTER ─────────────────────────────────────────────────────────

    @Test
    public void testAlterAddColumn() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE altadd (a);");
        sendCommandToServer("INSERT INTO altadd VALUES ('x');");
        String r = sendCommandToServer("ALTER TABLE altadd ADD b;");
        assertTrue(r.startsWith("[OK]"), "ALTER ADD should return [OK]: " + r);
        r = sendCommandToServer("SELECT * FROM altadd;");
        assertTrue(r.contains("b"), "New column should appear in header");
    }

    @Test
    public void testAlterDropColumn() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE altdrop (keep, remove_me);");
        sendCommandToServer("INSERT INTO altdrop VALUES ('val1', 'val2');");
        String r = sendCommandToServer("ALTER TABLE altdrop DROP remove_me;");
        assertTrue(r.startsWith("[OK]"), "ALTER DROP should return [OK]: " + r);
        r = sendCommandToServer("SELECT * FROM altdrop;");
        assertTrue(r.contains("keep"), "Keep column should remain");
        assertFalse(r.contains("remove_me"), "Dropped column should be gone");
    }

    @Test
    public void testAlterDropIdRejected() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE noiddrop (x);");
        String r = sendCommandToServer("ALTER TABLE noiddrop DROP id;");
        assertTrue(r.startsWith("[ERROR]"), "Dropping id should be rejected: " + r);
    }

    // ─── JOIN ──────────────────────────────────────────────────────────

    @Test
    public void testJoin() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE employees (name, dept_id);");
        sendCommandToServer("INSERT INTO employees VALUES ('Alice', 1);");
        sendCommandToServer("INSERT INTO employees VALUES ('Bob', 2);");
        sendCommandToServer("CREATE TABLE departments (dept_name);");
        sendCommandToServer("INSERT INTO departments VALUES ('Engineering');");
        sendCommandToServer("INSERT INTO departments VALUES ('Sales');");
        String r = sendCommandToServer("JOIN employees AND departments ON dept_id AND id;");
        assertTrue(r.startsWith("[OK]"), "JOIN should return [OK]: " + r);
        assertTrue(r.contains("employees.name"), "Header should prefix columns");
        assertTrue(r.contains("departments.dept_name"), "Header should prefix right table columns");
        assertTrue(r.contains("Alice"), "Alice should appear in join result");
        assertTrue(r.contains("Engineering"), "Engineering should appear in join result");
    }

    @Test
    public void testJoinNoMatch() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t1 (x);");
        sendCommandToServer("INSERT INTO t1 VALUES ('a');");
        sendCommandToServer("CREATE TABLE t2 (y);");
        sendCommandToServer("INSERT INTO t2 VALUES ('b');");
        String r = sendCommandToServer("JOIN t1 AND t2 ON x AND y;");
        assertTrue(r.startsWith("[OK]"), "JOIN with no match should still be [OK]");
        assertFalse(r.contains("a"), "No matching rows expected when x != y");
    }

    // ─── DROP ──────────────────────────────────────────────────────────

    @Test
    public void testDropTable() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE todrop (x);");
        String r = sendCommandToServer("DROP TABLE todrop;");
        assertTrue(r.startsWith("[OK]"), "DROP TABLE should return [OK]: " + r);
        r = sendCommandToServer("SELECT * FROM todrop;");
        assertTrue(r.startsWith("[ERROR]"), "Selecting dropped table should error: " + r);
    }

    @Test
    public void testDropDatabase() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE dummy (x);");
        // Switch away so we can drop it
        String db2 = randomName();
        sendCommandToServer("CREATE DATABASE " + db2 + ";");
        sendCommandToServer("USE " + db2 + ";");
        String r = sendCommandToServer("DROP DATABASE " + db + ";");
        assertTrue(r.startsWith("[OK]"), "DROP DATABASE should return [OK]: " + r);
    }

    // ─── PERSISTENCE ───────────────────────────────────────────────────

    @Test
    public void testPersistenceAcrossServerRestart() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE persist (data);");
        sendCommandToServer("INSERT INTO persist VALUES ('survives');");
        // New server instance
        server = new DBServer();
        sendCommandToServer("USE " + db + ";");
        String r = sendCommandToServer("SELECT * FROM persist;");
        assertTrue(r.contains("survives"), "Data should persist across server restart: " + r);
    }

    // ─── ERROR PROTOCOL ────────────────────────────────────────────────

    @Test
    public void testAllResponsesHaveBrackets() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE bracket (x);");
        sendCommandToServer("INSERT INTO bracket VALUES ('y');");

        String[] commands = {
            "SELECT * FROM bracket;",
            "UPDATE bracket SET x='z' WHERE x == 'y';",
            "DELETE FROM bracket WHERE x == 'z';",
            "ALTER TABLE bracket ADD newcol;",
            "CREATE TABLE bracket2 (a);",
            "INSERT INTO bracket2 VALUES ('1');",
        };

        for (String cmd : commands) {
            String r = sendCommandToServer(cmd);
            assertTrue(r.startsWith("[OK]") || r.startsWith("[ERROR]"),
                "Response for '" + cmd + "' should start with [OK] or [ERROR]: " + r);
        }
    }

    // ─── ORDER BY ────────────────────────────────────────────────────

    @Test
    public void testOrderByAscending() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE scores (player, points);");
        sendCommandToServer("INSERT INTO scores VALUES ('Charlie', 50);");
        sendCommandToServer("INSERT INTO scores VALUES ('Alice', 100);");
        sendCommandToServer("INSERT INTO scores VALUES ('Bob', 75);");
        String r = sendCommandToServer("SELECT * FROM scores ORDER BY points ASC;");
        assertTrue(r.startsWith("[OK]"), "ORDER BY ASC should return [OK]: " + r);
        // Charlie(50) should come before Bob(75) should come before Alice(100)
        int alicePos = r.indexOf("Alice");
        int bobPos = r.indexOf("Bob");
        int charliePos = r.indexOf("Charlie");
        assertTrue(alicePos > 0 && bobPos > 0 && charliePos > 0, "All three names should appear in result");
        assertTrue(charliePos < bobPos, "Charlie (50) should come before Bob (75) in ASC order");
        assertTrue(bobPos < alicePos, "Bob (75) should come before Alice (100) in ASC order");
    }

    @Test
    public void testOrderByDescending() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE items (name, price);");
        sendCommandToServer("INSERT INTO items VALUES ('Widget', 10);");
        sendCommandToServer("INSERT INTO items VALUES ('Gadget', 50);");
        sendCommandToServer("INSERT INTO items VALUES ('Thing', 30);");
        String r = sendCommandToServer("SELECT * FROM items ORDER BY price DESC;");
        assertTrue(r.startsWith("[OK]"), "ORDER BY DESC should return [OK]: " + r);
        int gadgetPos = r.indexOf("Gadget");
        int thingPos = r.indexOf("Thing");
        int widgetPos = r.indexOf("Widget");
        assertTrue(gadgetPos < thingPos, "Gadget (50) should come before Thing (30) in DESC order");
        assertTrue(thingPos < widgetPos, "Thing (30) should come before Widget (10) in DESC order");
    }

    @Test
    public void testOrderByDefaultAsc() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE vals (x);");
        sendCommandToServer("INSERT INTO vals VALUES ('c');");
        sendCommandToServer("INSERT INTO vals VALUES ('a');");
        sendCommandToServer("INSERT INTO vals VALUES ('b');");
        String r = sendCommandToServer("SELECT * FROM vals ORDER BY x;");
        assertTrue(r.startsWith("[OK]"), "ORDER BY without ASC/DESC should default to ASC: " + r);
        int aPos = r.indexOf("a");
        int bPos = r.indexOf("b");
        int cPos = r.indexOf("c");
        assertTrue(aPos < bPos && bPos < cPos, "Should be in alphabetical order: a, b, c");
    }

    @Test
    public void testOrderByNonexistentColumn() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (a);");
        sendCommandToServer("INSERT INTO t VALUES ('x');");
        String r = sendCommandToServer("SELECT * FROM t ORDER BY nonexistent ASC;");
        assertTrue(r.startsWith("[ERROR]"), "ORDER BY on nonexistent column should error: " + r);
    }

    // ─── GROUP BY ────────────────────────────────────────────────────

    @Test
    public void testGroupByCount() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE sales (product, region);");
        sendCommandToServer("INSERT INTO sales VALUES ('Widget', 'North');");
        sendCommandToServer("INSERT INTO sales VALUES ('Widget', 'South');");
        sendCommandToServer("INSERT INTO sales VALUES ('Widget', 'North');");
        sendCommandToServer("INSERT INTO sales VALUES ('Gadget', 'North');");
        sendCommandToServer("INSERT INTO sales VALUES ('Gadget', 'South');");
        String r = sendCommandToServer("SELECT product, COUNT(*) FROM sales GROUP BY product;");
        assertTrue(r.startsWith("[OK]"), "GROUP BY with COUNT should return [OK]: " + r);
        assertTrue(r.contains("Widget"), "Widget should appear in GROUP BY result");
        assertTrue(r.contains("Gadget"), "Gadget should appear in GROUP BY result");
        assertTrue(r.contains("3"), "Widget COUNT should be 3");
        assertTrue(r.contains("2"), "Gadget COUNT should be 2");
    }

    @Test
    public void testGroupBySum() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE orders (customer, amount);");
        sendCommandToServer("INSERT INTO orders VALUES ('Alice', 100);");
        sendCommandToServer("INSERT INTO orders VALUES ('Alice', 50);");
        sendCommandToServer("INSERT INTO orders VALUES ('Bob', 200);");
        String r = sendCommandToServer("SELECT customer, SUM(amount) FROM orders GROUP BY customer;");
        assertTrue(r.startsWith("[OK]"), "GROUP BY with SUM should return [OK]: " + r);
        assertTrue(r.contains("Alice") && r.contains("150"), "Alice SUM should be 150");
        assertTrue(r.contains("Bob") && r.contains("200"), "Bob SUM should be 200");
    }

    @Test
    public void testGroupByAvg() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE grades (student, score);");
        sendCommandToServer("INSERT INTO grades VALUES ('S1', 80);");
        sendCommandToServer("INSERT INTO grades VALUES ('S1', 90);");
        sendCommandToServer("INSERT INTO grades VALUES ('S2', 60);");
        String r = sendCommandToServer("SELECT student, AVG(score) FROM grades GROUP BY student;");
        assertTrue(r.startsWith("[OK]"), "GROUP BY with AVG should return [OK]: " + r);
        assertTrue(r.contains("S1") && r.contains("85"), "S1 AVG should be 85");
        assertTrue(r.contains("S2") && r.contains("60"), "S2 AVG should be 60");
    }

    // ─── DISTINCT ────────────────────────────────────────────────────

    @Test
    public void testDistinctSingleColumn() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (city);");
        sendCommandToServer("INSERT INTO t VALUES ('London');");
        sendCommandToServer("INSERT INTO t VALUES ('Paris');");
        sendCommandToServer("INSERT INTO t VALUES ('London');");
        sendCommandToServer("INSERT INTO t VALUES ('Berlin');");
        sendCommandToServer("INSERT INTO t VALUES ('Paris');");
        String r = sendCommandToServer("SELECT DISTINCT city FROM t;");
        assertTrue(r.startsWith("[OK]"), "SELECT DISTINCT should return [OK]: " + r);
        // Should have exactly 3 distinct cities: Berlin, London, Paris (order may vary)
        int londonCount = countOccurrences(r, "London");
        int parisCount = countOccurrences(r, "Paris");
        int berlinCount = countOccurrences(r, "Berlin");
        assertTrue(londonCount == 1, "London should appear exactly once, got " + londonCount + ": " + r);
        assertTrue(parisCount == 1, "Paris should appear exactly once, got " + parisCount + ": " + r);
        assertTrue(berlinCount == 1, "Berlin should appear exactly once, got " + berlinCount + ": " + r);
    }

    @Test
    public void testDistinctWildcard() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE dupes (a, b);");
        sendCommandToServer("INSERT INTO dupes VALUES ('x', '1');");
        sendCommandToServer("INSERT INTO dupes VALUES ('x', '1');");
        sendCommandToServer("INSERT INTO dupes VALUES ('x', '2');");
        sendCommandToServer("INSERT INTO dupes VALUES ('y', '1');");
        String r = sendCommandToServer("SELECT DISTINCT * FROM dupes;");
        assertTrue(r.startsWith("[OK]"), "SELECT DISTINCT * should return [OK]: " + r);
        // DISTINCT * includes the id column — each row has a unique id so all rows are distinct.
        // 4 rows inserted, all with different ids, so all 4 should appear.
        int xCount = countOccurrences(r, "x");
        assertTrue(xCount == 3, "x should appear 3 times (3 rows with x, all distinct by id): " + r);
        int yCount = countOccurrences(r, "y");
        assertTrue(yCount == 1, "y should appear once: " + r);
    }

    @Test
    public void testDistinctWithOrderBy() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE scores (name, pts);");
        sendCommandToServer("INSERT INTO scores VALUES ('A', 10);");
        sendCommandToServer("INSERT INTO scores VALUES ('B', 20);");
        sendCommandToServer("INSERT INTO scores VALUES ('A', 10);");
        String r = sendCommandToServer("SELECT DISTINCT name, pts FROM scores ORDER BY name ASC;");
        assertTrue(r.startsWith("[OK]"), "DISTINCT with ORDER BY should return [OK]: " + r);
        int aIdx = r.indexOf("A");
        int bIdx = r.indexOf("B");
        assertTrue(aIdx < bIdx, "A should come before B in ASC order: " + r);
        // No duplicates
        assertTrue(countOccurrences(r, "10") == 1, "10 should appear once: " + r);
        assertTrue(countOccurrences(r, "20") == 1, "20 should appear once: " + r);
    }

    @Test
    public void testDistinctNoMatches() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE empty (x);");
        String r = sendCommandToServer("SELECT DISTINCT x FROM empty;");
        assertTrue(r.startsWith("[OK]"), "DISTINCT on empty table should return [OK]: " + r);
    }

    // ─── LIMIT / OFFSET ──────────────────────────────────────────────

    @Test
    public void testLimitBasic() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE nums (val);");
        for (int i = 1; i <= 5; i++) {
            sendCommandToServer("INSERT INTO nums VALUES ('" + i + "');");
        }
        String r = sendCommandToServer("SELECT * FROM nums LIMIT 2;");
        assertTrue(r.startsWith("[OK]"), "LIMIT should return [OK]: " + r);
        // [OK] + header + 2 data rows = 4 lines
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 4, "[OK] + header + 2 rows = 4 lines, got " + lines.length + ": " + r);
    }

    @Test
    public void testLimitWithOffset() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE items (n);");
        for (int i = 1; i <= 5; i++) {
            sendCommandToServer("INSERT INTO items VALUES ('" + i + "');");
        }
        String r = sendCommandToServer("SELECT * FROM items LIMIT 2 OFFSET 1;");
        assertTrue(r.startsWith("[OK]"), "LIMIT OFFSET should return [OK]: " + r);
        // [OK] + header + 2 data rows = 4 lines. Values 2 and 3 should appear.
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 4, "[OK] + header + 2 rows = 4 lines, got " + lines.length + ": " + r);
        assertTrue(r.contains("2") && r.contains("3"), "Should contain values 2 and 3: " + r);
        assertFalse(r.contains("\t1\n") && !r.contains("\t1\t"), "Should NOT contain value 1 (offset skipped it): " + r);
    }

    @Test
    public void testLimitZero() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (x);");
        sendCommandToServer("INSERT INTO t VALUES ('hello');");
        String r = sendCommandToServer("SELECT * FROM t LIMIT 0;");
        assertTrue(r.startsWith("[OK]"), "LIMIT 0 should return [OK]: " + r);
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 2, "LIMIT 0: [OK] + header only = 2 lines. Got " + lines.length + ": " + r);
    }

    @Test
    public void testLimitWithOrderBy() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE scores (name, pts);");
        sendCommandToServer("INSERT INTO scores VALUES ('C', 50);");
        sendCommandToServer("INSERT INTO scores VALUES ('A', 100);");
        sendCommandToServer("INSERT INTO scores VALUES ('B', 75);");
        String r = sendCommandToServer("SELECT * FROM scores ORDER BY pts DESC LIMIT 2;");
        assertTrue(r.startsWith("[OK]"), "ORDER BY + LIMIT should return [OK]: " + r);
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 4, "[OK] + header + 2 rows = 4 lines: " + r);
        // A (100) should come before B (75)
        assertTrue(r.indexOf("A") < r.indexOf("B"), "A should appear before B: " + r);
    }

    @Test
    public void testLimitNegative() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (x);");
        String r = sendCommandToServer("SELECT * FROM t LIMIT -1;");
        assertTrue(r.startsWith("[ERROR]"), "Negative LIMIT should error: " + r);
    }

    // ─── LEFT JOIN ───────────────────────────────────────────────────

    @Test
    public void testLeftJoinUnmatchedRows() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE users (name, dept_id);");
        sendCommandToServer("INSERT INTO users VALUES ('Alice', '1');");
        sendCommandToServer("INSERT INTO users VALUES ('Bob', '2');");
        sendCommandToServer("INSERT INTO users VALUES ('Charlie', '3');");
        sendCommandToServer("CREATE TABLE depts (dept_code, dept_name);");
        sendCommandToServer("INSERT INTO depts VALUES ('1', 'Engineering');");
        sendCommandToServer("INSERT INTO depts VALUES ('2', 'Sales');");
        // depts has no dept_code=3, so Charlie should appear with blanks for dept columns
        String r = sendCommandToServer("LEFT JOIN users AND depts ON dept_id AND dept_code;");
        assertTrue(r.startsWith("[OK]"), "LEFT JOIN should return [OK]: " + r);
        assertTrue(r.contains("Alice") && r.contains("Engineering"), "Alice should match Engineering: " + r);
        assertTrue(r.contains("Bob") && r.contains("Sales"), "Bob should match Sales: " + r);
        assertTrue(r.contains("Charlie"), "Charlie should appear (unmatched left row): " + r);
    }

    @Test
    public void testLeftJoinAllMatch() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE a (x, y);");
        sendCommandToServer("INSERT INTO a VALUES ('foo', '1');");
        sendCommandToServer("INSERT INTO a VALUES ('bar', '2');");
        sendCommandToServer("CREATE TABLE b (z, w);");
        sendCommandToServer("INSERT INTO b VALUES ('1', 'baz');");
        sendCommandToServer("INSERT INTO b VALUES ('2', 'qux');");
        String r = sendCommandToServer("LEFT JOIN a AND b ON y AND z;");
        assertTrue(r.startsWith("[OK]"), "LEFT JOIN all-match should return [OK]: " + r);
        assertTrue(r.contains("baz") && r.contains("qux"), "Both joined values should appear: " + r);
    }

    @Test
    public void testLeftJoinNoMatch() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t1 (c1);");
        sendCommandToServer("INSERT INTO t1 VALUES ('x');");
        sendCommandToServer("CREATE TABLE t2 (c2);");
        sendCommandToServer("INSERT INTO t2 VALUES ('y');");
        String r = sendCommandToServer("LEFT JOIN t1 AND t2 ON c1 AND c2;");
        assertTrue(r.startsWith("[OK]"), "LEFT JOIN no-match should return [OK]: " + r);
        assertTrue(r.contains("x"), "x should appear from left table: " + r);
    }

    @Test
    public void testJoinStillWorksAsInner() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE p (name, val);");
        sendCommandToServer("INSERT INTO p VALUES ('A', '1');");
        sendCommandToServer("INSERT INTO p VALUES ('B', '2');");
        sendCommandToServer("CREATE TABLE q (num, label);");
        sendCommandToServer("INSERT INTO q VALUES ('1', 'one');");
        String r = sendCommandToServer("JOIN p AND q ON val AND num;");
        assertTrue(r.startsWith("[OK]"), "Plain JOIN should still work: " + r);
        assertTrue(r.contains("A") && r.contains("one"), "A should join with one: " + r);
        assertFalse(r.contains("B"), "B should not appear (no match in inner join): " + r);
    }

    // ─── NULL HANDLING ────────────────────────────────────────────────

    @Test
    public void testInsertNull() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (name);");
        String r = sendCommandToServer("INSERT INTO t VALUES (NULL);");
        assertTrue(r.startsWith("[OK]"), "INSERT with NULL should succeed: " + r);
        r = sendCommandToServer("SELECT * FROM t;");
        assertTrue(r.startsWith("[OK]"), "SELECT after NULL insert should work: " + r);
        // NULL should display as empty string
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length >= 3, "Should have [OK], header, and data row");
        // The data row (line 2) should have id and empty value
        assertTrue(lines[2].endsWith("\t"), "NULL should display as empty: " + lines[2]);
    }

    @Test
    public void testNullEqualityReturnsFalse() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (name);");
        sendCommandToServer("INSERT INTO t VALUES (NULL);");
        // NULL != 'anything' in SQL, so WHERE name == 'anything' should not match
        String r = sendCommandToServer("SELECT * FROM t WHERE name == 'test';");
        assertTrue(r.startsWith("[OK]"), "WHERE == on NULL should not error: " + r);
        // The NULL row should NOT match
        assertFalse(r.contains("test"), "NULL should not match equality check: " + r);
        // Only [OK] and header line should appear (no data rows)
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 2, "Should only have [OK] + header, got " + lines.length + ": " + r);
    }

    @Test
    public void testIsNull() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (name);");
        sendCommandToServer("INSERT INTO t VALUES (NULL);");
        sendCommandToServer("INSERT INTO t VALUES ('hello');");
        String r = sendCommandToServer("SELECT * FROM t WHERE name IS NULL;");
        assertTrue(r.startsWith("[OK]"), "IS NULL should return [OK]: " + r);
        // Only the NULL row should match
        assertFalse(r.contains("hello"), "Non-null row should not match IS NULL: " + r);
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 3, "IS NULL should return exactly 1 data row, got " + (lines.length - 2) + ": " + r);
    }

    @Test
    public void testIsNotNull() {
        String db = randomName();
        sendCommandToServer("CREATE DATABASE " + db + ";");
        sendCommandToServer("USE " + db + ";");
        sendCommandToServer("CREATE TABLE t (name);");
        sendCommandToServer("INSERT INTO t VALUES (NULL);");
        sendCommandToServer("INSERT INTO t VALUES ('hello');");
        String r = sendCommandToServer("SELECT * FROM t WHERE name IS NOT NULL;");
        assertTrue(r.startsWith("[OK]"), "IS NOT NULL should return [OK]: " + r);
        assertTrue(r.contains("hello"), "Non-null row should match IS NOT NULL: " + r);
        String[] lines = r.split(System.lineSeparator());
        assertTrue(lines.length == 3, "IS NOT NULL should return exactly 1 data row, got " + (lines.length - 2) + ": " + r);
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
