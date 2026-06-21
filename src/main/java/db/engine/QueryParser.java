package db.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryParser {

    private QueryParser() {
        // Utility class
    }

    // Shared command abstraction for all parsed commands.
    public sealed interface Command permits UseCommand,
            CreateDatabaseCommand,
            CreateTableCommand,
            DropDatabaseCommand,
            DropTableCommand,
            AlterCommand,
            InsertCommand,
            SelectCommand,
            UpdateCommand,
            DeleteCommand,
            JoinCommand {
        <R> R accept(CommandVisitor<R> visitor);
    }

    // Visitor contract for operations over command objects.
    public interface CommandVisitor<R> {
        R visit(UseCommand command);

        R visit(CreateDatabaseCommand command);

        R visit(CreateTableCommand command);

        R visit(DropDatabaseCommand command);

        R visit(DropTableCommand command);

        R visit(AlterCommand command);

        R visit(InsertCommand command);

        R visit(SelectCommand command);

        R visit(UpdateCommand command);

        R visit(DeleteCommand command);

        R visit(JoinCommand command);
    }

    public record UseCommand(String databaseName) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record CreateDatabaseCommand(String databaseName) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record CreateTableCommand(String tableName, List<String> attributes) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record DropDatabaseCommand(String databaseName) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record DropTableCommand(String tableName) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record AlterCommand(String tableName, String alterationType, String attributeName) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record InsertCommand(String tableName, List<String> values) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record SelectCommand(List<String> selectedAttributes, String tableName, String rawCondition,
                                String orderByColumn, boolean orderByDesc,
                                String groupByColumn, String aggregateFunction, String aggregateColumn,
                                boolean distinct) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record UpdateCommand(String tableName, Map<String, String> assignments, String rawCondition) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record DeleteCommand(String tableName, String rawCondition) implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public record JoinCommand(String leftTable, String rightTable, String leftAttribute, String rightAttribute)
            implements Command {
        @Override
        public <R> R accept(CommandVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    // Parse only: this method builds command objects and does not execute anything.
    public static Command parse(String rawCommand) {
        String command = validateAndTrim(rawCommand);
        String keyword = firstKeyword(command);

        return switch (keyword) {
            case "USE" -> parseUse(command);
            case "CREATE" -> parseCreate(command);
            case "DROP" -> parseDrop(command);
            case "ALTER" -> parseAlter(command);
            case "INSERT" -> parseInsert(command);
            case "SELECT" -> parseSelect(command);
            case "UPDATE" -> parseUpdate(command);
            case "DELETE" -> parseDelete(command);
            case "JOIN" -> parseJoin(command);
            default -> throw new IllegalArgumentException("Unknown command type: " + keyword);
        };
    }

    private static String validateAndTrim(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            throw new IllegalArgumentException("Command is empty");
        }

        String command = rawCommand.trim();
        if (!command.endsWith(";")) {
            throw new IllegalArgumentException("Command must end with ';'");
        }

        return command.substring(0, command.length() - 1).trim();
    }

    private static String firstKeyword(String commandWithoutSemicolon) {
        int firstSpace = commandWithoutSemicolon.indexOf(' ');
        if (firstSpace < 0) {
            return commandWithoutSemicolon.toUpperCase();
        }
        return commandWithoutSemicolon.substring(0, firstSpace).toUpperCase();
    }

    private static Command parseUse(String command) {
        // Parse USE <DatabaseName>.
        String[] parts = command.trim().split("\\s+");
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("USE")) {
            throw new IllegalArgumentException("Invalid USE syntax. Expected: USE <DatabaseName>");
        }

        String databaseName = validateIdentifier(parts[1], "database");
        return new UseCommand(databaseName);
    }

    private static Command parseCreate(String command) {
        // Route CREATE variants and return the corresponding concrete command object.
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("CREATE DATABASE ")) {
            return parseCreateDatabase(trimmed);
        }

        if (upper.startsWith("CREATE TABLE ")) {
            return parseCreateTable(trimmed);
        }

        throw new IllegalArgumentException("Invalid CREATE syntax. Expected DATABASE or TABLE");
    }

    private static Command parseCreateDatabase(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("CREATE") || !parts[1].equalsIgnoreCase("DATABASE")) {
            throw new IllegalArgumentException("Invalid CREATE DATABASE syntax. Expected: CREATE DATABASE <DatabaseName>");
        }

        String databaseName = validateIdentifier(parts[2], "database");
        return new CreateDatabaseCommand(databaseName);
    }

    private static Command parseCreateTable(String command) {
        String rest = command.substring("CREATE TABLE".length()).trim();
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("Table name is required for CREATE TABLE");
        }

        int openParen = rest.indexOf('(');
        if (openParen < 0) {
            // Supports CREATE TABLE <TableName> without attribute list.
            String tableNameOnly = validateIdentifier(rest, "table");
            return new CreateTableCommand(tableNameOnly, List.of());
        }

        int closeParen = rest.lastIndexOf(')');
        if (closeParen < 0 || closeParen < openParen) {
            throw new IllegalArgumentException("Invalid CREATE TABLE syntax. Missing closing ')' in attribute list");
        }

        String tableName = validateIdentifier(rest.substring(0, openParen).trim(), "table");
        String attributesRaw = rest.substring(openParen + 1, closeParen).trim();
        List<String> attributes = parseAttributeList(attributesRaw);

        return new CreateTableCommand(tableName, attributes);
    }

    private static List<String> parseAttributeList(String attributesRaw) {
        if (attributesRaw.isEmpty()) {
            return List.of();
        }

        String[] tokens = attributesRaw.split(",");
        List<String> attributes = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.equals("*")) {
                attributes.add("*");
            } else {
                String attribute = validateIdentifier(trimmed, "attribute");
                attributes.add(attribute);
            }
        }
        return attributes;
    }

    private static String validateIdentifier(String raw, String kind) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing " + kind + " name");
        }

        String trimmed = raw.trim();
        if (!trimmed.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid " + kind + " name: " + trimmed);
        }
        return trimmed;
    }

    private static Command parseDrop(String command) {
        // Route between DROP DATABASE and DROP TABLE forms.
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();
        String[] parts = trimmed.split("\\s+", 3);  // Use original-case for name extraction

        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid DROP syntax. Expected: DROP TABLE <name> or DROP DATABASE <name>");
        }

        String targetType = parts[1].toUpperCase();
        if (!targetType.equals("DATABASE") && !targetType.equals("TABLE")) {
            throw new IllegalArgumentException("Invalid DROP syntax. Expected DATABASE or TABLE after DROP");
        }

        if (parts.length < 3) {
            throw new IllegalArgumentException("Missing name for DROP " + targetType);
        }

        String targetName = validateIdentifier(parts[2], targetType.equals("TABLE") ? "table" : "database");

        return switch (targetType) {
            case "DATABASE" -> new DropDatabaseCommand(targetName);
            case "TABLE" -> new DropTableCommand(targetName);
            default -> throw new IllegalArgumentException("Invalid DROP syntax");
        };
    }

    private static Command parseAlter(String command) {
        // Parse ALTER TABLE <TableName> ADD|DROP <AttributeName>.
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();

        if (!upper.startsWith("ALTER TABLE ")) {
            throw new IllegalArgumentException("Invalid ALTER syntax. Expected: ALTER TABLE <name> ADD|DROP <attribute>");
        }

        String rest = trimmed.substring("ALTER TABLE ".length()).trim();
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("Table name is required for ALTER TABLE");
        }

        // Split on whitespace: first token is table name, second is ADD/DROP, third is attribute name
        String[] parts = rest.split("\\s+", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid ALTER syntax. Expected: ALTER TABLE <name> ADD|DROP <attribute>");
        }

        String tableName = validateIdentifier(parts[0], "table");
        String alterationType = parts[1].toUpperCase();

        if (!alterationType.equals("ADD") && !alterationType.equals("DROP")) {
            throw new IllegalArgumentException("Invalid ALTER type: " + alterationType + ". Expected ADD or DROP");
        }

        String attributeName = validateIdentifier(parts[2], "attribute");
        return new AlterCommand(tableName, alterationType, attributeName);
    }

    private static Command parseInsert(String command) {
        // Parse INSERT INTO [TableName] VALUES(...).
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();

        if (!upper.startsWith("INSERT INTO ")) {
            throw new IllegalArgumentException("Invalid INSERT syntax. Expected: INSERT INTO <table> VALUES(...)");
        }

        // Find VALUES boundary — handle both "VALUES(" and "VALUES ("
        int valuesKeywordIndex = upper.indexOf(" VALUES");
        if (valuesKeywordIndex < 0) {
            throw new IllegalArgumentException("Invalid INSERT syntax. Missing VALUES clause");
        }
        // Skip past "VALUES" and any whitespace to find the opening paren
        int valuesEnd = valuesKeywordIndex + " VALUES".length();
        while (valuesEnd < trimmed.length() && Character.isWhitespace(trimmed.charAt(valuesEnd))) {
            valuesEnd++;
        }
        if (valuesEnd >= trimmed.length() || trimmed.charAt(valuesEnd) != '(') {
            throw new IllegalArgumentException("Invalid INSERT syntax. VALUES must be followed by (value list)");
        }

        String tableName = trimmed.substring("INSERT INTO ".length(), valuesKeywordIndex).trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name is required after INSERT INTO");
        }
        tableName = validateIdentifier(tableName, "table");

        // Extract and parse values between parentheses
        String afterParen = trimmed.substring(valuesEnd + 1).trim();  // skip the '('
        if (!afterParen.endsWith(")")) {
            throw new IllegalArgumentException("INSERT VALUES must be wrapped in parentheses");
        }
        String valuesRaw = afterParen.substring(0, afterParen.length() - 1).trim();
        if (valuesRaw.isEmpty()) {
            throw new IllegalArgumentException("INSERT VALUES clause must contain at least one value");
        }

        // Split on comma, handling quoted strings with commas inside them
        List<String> values = splitValues(valuesRaw);
        return new InsertCommand(tableName, values);
    }

    private static List<String> splitValues(String valuesRaw) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < valuesRaw.length(); i++) {
            char c = valuesRaw.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
            } else if (c == ',' && !inSingleQuote && !inDoubleQuote) {
                result.add(stripMatchingQuotes(current.toString().trim()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        // Don't forget the last value
        if (!current.isEmpty()) {
            result.add(stripMatchingQuotes(current.toString().trim()));
        }
        return result;
    }

    private static String stripMatchingQuotes(String value) {
        if (value.length() >= 2) {
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            if (singleQuoted || doubleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static Command parseSelect(String command) {
        // Parse SELECT <WildAttribList> FROM [TableName] [WHERE <Condition>] [GROUP BY <col>] [ORDER BY <col> [ASC|DESC]].
        // Selector can include aggregate functions: COUNT(*), SUM(col), AVG(col).

        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();
        if (!upper.startsWith("SELECT ")) {
            throw new IllegalArgumentException("Invalid SELECT syntax.");
        }

        int fromIndex = upper.indexOf(" FROM ");
        if (fromIndex < 0) {
            throw new IllegalArgumentException("Invalid SELECT syntax. Missing FROM clause");
        }

        String selectorRaw = trimmed.substring("SELECT ".length(), fromIndex).trim();
        if (selectorRaw.isEmpty()) {
            throw new IllegalArgumentException("SELECT must specify at least one column or aggregate");
        }

        // Detect DISTINCT keyword
        boolean distinct = false;
        String upperSelectorStart = selectorRaw.toUpperCase();
        if (upperSelectorStart.startsWith("DISTINCT ")) {
            distinct = true;
            selectorRaw = selectorRaw.substring("DISTINCT ".length()).trim();
        }

        // Parse selector — pre-process aggregates before splitting into attributes
        String processedSelector = selectorRaw;
        String aggregateFunction = null;
        String aggregateColumn = null;

        // Detect and extract aggregate function from the selector
        String upperSelector = selectorRaw.toUpperCase().trim();
        if (upperSelector.contains("COUNT(")) {
            aggregateFunction = "COUNT";
            int start = upperSelector.indexOf("COUNT(");
            int end = upperSelector.indexOf(")", start);
            if (end < 0) throw new IllegalArgumentException("COUNT(...) missing closing parenthesis");
            String inner = selectorRaw.substring(start + "COUNT(".length(), end).trim();
            aggregateColumn = inner.equals("*") ? null : inner;
            // Remove COUNT(...) from selector; keep remaining attributes
            processedSelector = (selectorRaw.substring(0, start) + selectorRaw.substring(end + 1)).trim();
            // Clean up dangling commas
            processedSelector = processedSelector.replaceAll("^,\\s*", "").replaceAll(",\\s*$", "").replaceAll(",\\s*,", ",");
        } else if (upperSelector.contains("SUM(")) {
            aggregateFunction = "SUM";
            int start = upperSelector.indexOf("SUM(");
            int end = upperSelector.indexOf(")", start);
            if (end < 0) throw new IllegalArgumentException("SUM(...) missing closing parenthesis");
            aggregateColumn = selectorRaw.substring(start + "SUM(".length(), end).trim();
            if (aggregateColumn.isEmpty()) throw new IllegalArgumentException("SUM requires a column name");
            processedSelector = (selectorRaw.substring(0, start) + aggregateColumn + selectorRaw.substring(end + 1)).trim();
        } else if (upperSelector.contains("AVG(")) {
            aggregateFunction = "AVG";
            int start = upperSelector.indexOf("AVG(");
            int end = upperSelector.indexOf(")", start);
            if (end < 0) throw new IllegalArgumentException("AVG(...) missing closing parenthesis");
            aggregateColumn = selectorRaw.substring(start + "AVG(".length(), end).trim();
            if (aggregateColumn.isEmpty()) throw new IllegalArgumentException("AVG requires a column name");
            processedSelector = (selectorRaw.substring(0, start) + aggregateColumn + selectorRaw.substring(end + 1)).trim();
        }

        List<String> selectedAttributes;
        if (processedSelector.trim().equals("*")) {
            selectedAttributes = List.of("*");
        } else {
            selectedAttributes = parseAttributeList(processedSelector);
        }

        String fromTail = trimmed.substring(fromIndex + " FROM ".length()).trim();
        if (fromTail.isEmpty()) {
            throw new IllegalArgumentException("Table name is required after FROM");
        }

        String fromTailUpper = fromTail.toUpperCase();
        int whereIndex = fromTailUpper.indexOf(" WHERE ");
        int groupByIndex = fromTailUpper.lastIndexOf(" GROUP BY ");
        int orderByIndex = fromTailUpper.lastIndexOf(" ORDER BY ");

        // Validate clause ordering
        if (whereIndex >= 0 && groupByIndex >= 0 && groupByIndex < whereIndex) {
            throw new IllegalArgumentException("GROUP BY must come after WHERE");
        }
        if (groupByIndex >= 0 && orderByIndex >= 0 && orderByIndex < groupByIndex) {
            throw new IllegalArgumentException("ORDER BY must come after GROUP BY");
        }

        // Split the tail into segments
        String tablePart = fromTail;
        String wherePart = null;
        String groupByPart = null;
        String orderByPart = null;

        if (whereIndex >= 0) {
            tablePart = fromTail.substring(0, whereIndex).trim();
            String remainder = fromTail.substring(whereIndex + " WHERE ".length()).trim();
            groupByIndex = remainder.toUpperCase().lastIndexOf(" GROUP BY ");
            orderByIndex = remainder.toUpperCase().lastIndexOf(" ORDER BY ");

            if (groupByIndex >= 0 && orderByIndex >= 0) {
                wherePart = remainder.substring(0, groupByIndex).trim();
                groupByPart = remainder.substring(groupByIndex + " GROUP BY ".length(), orderByIndex).trim();
                orderByPart = remainder.substring(orderByIndex + " ORDER BY ".length()).trim();
            } else if (groupByIndex >= 0) {
                wherePart = remainder.substring(0, groupByIndex).trim();
                groupByPart = remainder.substring(groupByIndex + " GROUP BY ".length()).trim();
            } else if (orderByIndex >= 0) {
                wherePart = remainder.substring(0, orderByIndex).trim();
                orderByPart = remainder.substring(orderByIndex + " ORDER BY ".length()).trim();
            } else {
                wherePart = remainder;
            }
        } else {
            String remainder = fromTail;
            groupByIndex = remainder.toUpperCase().lastIndexOf(" GROUP BY ");
            orderByIndex = remainder.toUpperCase().lastIndexOf(" ORDER BY ");

            if (groupByIndex >= 0 && orderByIndex >= 0) {
                tablePart = remainder.substring(0, groupByIndex).trim();
                groupByPart = remainder.substring(groupByIndex + " GROUP BY ".length(), orderByIndex).trim();
                orderByPart = remainder.substring(orderByIndex + " ORDER BY ".length()).trim();
            } else if (groupByIndex >= 0) {
                tablePart = remainder.substring(0, groupByIndex).trim();
                groupByPart = remainder.substring(groupByIndex + " GROUP BY ".length()).trim();
            } else if (orderByIndex >= 0) {
                tablePart = remainder.substring(0, orderByIndex).trim();
                orderByPart = remainder.substring(orderByIndex + " ORDER BY ".length()).trim();
            } else {
                tablePart = remainder;
            }
        }

        // Validate table name
        String tableName = validateIdentifier(tablePart, "table");

        // Validate WHERE
        String rawCondition = null;
        if (wherePart != null) {
            if (wherePart.isEmpty()) {
                throw new IllegalArgumentException("WHERE clause requires a condition");
            }
            rawCondition = wherePart;
        }

        // Parse GROUP BY
        String groupByColumn = null;
        if (groupByPart != null) {
            if (groupByPart.isEmpty()) {
                throw new IllegalArgumentException("GROUP BY requires a column name");
            }
            groupByColumn = groupByPart.trim();
        }

        // Parse ORDER BY
        String orderByColumn = null;
        boolean orderByDesc = false;
        if (orderByPart != null) {
            if (orderByPart.isEmpty()) {
                throw new IllegalArgumentException("ORDER BY requires a column name");
            }
            String[] orderParts = orderByPart.split("\\s+", 2);
            orderByColumn = orderParts[0];
            if (orderParts.length > 1) {
                String direction = orderParts[1].toUpperCase();
                if ("DESC".equals(direction)) {
                    orderByDesc = true;
                } else if (!"ASC".equals(direction)) {
                    throw new IllegalArgumentException("Invalid ORDER BY direction: " + orderParts[1]);
                }
            }
        }

        return new SelectCommand(selectedAttributes, tableName, rawCondition,
                                 orderByColumn, orderByDesc, groupByColumn, aggregateFunction, aggregateColumn, distinct);
    }

    private static Command parseUpdate(String command) {
        // Parse UPDATE <TableName> SET <col1>=<val1>, <col2>=<val2>, ... WHERE <Condition>.
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();

        if (!upper.startsWith("UPDATE ")) {
            throw new IllegalArgumentException("Invalid UPDATE syntax. Expected: UPDATE <table> SET <assignments> WHERE <condition>");
        }

        int setIndex = upper.indexOf(" SET ");
        if (setIndex < 0) {
            throw new IllegalArgumentException("Invalid UPDATE syntax. Missing SET clause");
        }

        String tableName = trimmed.substring("UPDATE ".length(), setIndex).trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name is required for UPDATE");
        }
        tableName = validateIdentifier(tableName, "table");

        String afterSet = trimmed.substring(setIndex + " SET ".length()).trim();
        int whereIndex = afterSet.toUpperCase().indexOf(" WHERE ");
        if (whereIndex < 0) {
            throw new IllegalArgumentException("Invalid UPDATE syntax. Missing WHERE clause");
        }

        String assignmentsRaw = afterSet.substring(0, whereIndex).trim();
        String conditionRaw = afterSet.substring(whereIndex + " WHERE ".length()).trim();

        if (assignmentsRaw.isEmpty()) {
            throw new IllegalArgumentException("UPDATE SET clause must contain at least one assignment");
        }
        if (conditionRaw.isEmpty()) {
            throw new IllegalArgumentException("UPDATE WHERE clause requires a condition");
        }

        Map<String, String> assignments = parseAssignments(assignmentsRaw);
        return new UpdateCommand(tableName, assignments, conditionRaw);
    }

    private static Map<String, String> parseAssignments(String assignmentsRaw) {
        Map<String, String> assignments = new HashMap<>();
        String[] parts = assignmentsRaw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            int eqIndex = trimmed.indexOf('=');
            if (eqIndex < 0) {
                throw new IllegalArgumentException("Invalid assignment: " + trimmed + ". Expected column=value");
            }
            String colName = validateIdentifier(trimmed.substring(0, eqIndex).trim(), "column");
            String value = stripMatchingQuotes(trimmed.substring(eqIndex + 1).trim());
            assignments.put(colName, value);
        }
        return assignments;
    }

    private static Command parseDelete(String command) {
        // Parse DELETE FROM <TableName> WHERE <Condition>.
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();

        if (!upper.startsWith("DELETE FROM ")) {
            throw new IllegalArgumentException("Invalid DELETE syntax. Expected: DELETE FROM <table> WHERE <condition>");
        }

        String afterFrom = trimmed.substring("DELETE FROM ".length()).trim();
        int whereIndex = afterFrom.toUpperCase().indexOf(" WHERE ");
        if (whereIndex < 0) {
            throw new IllegalArgumentException("Invalid DELETE syntax. Missing WHERE clause");
        }

        String tableName = afterFrom.substring(0, whereIndex).trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name is required for DELETE FROM");
        }
        tableName = validateIdentifier(tableName, "table");

        String conditionRaw = afterFrom.substring(whereIndex + " WHERE ".length()).trim();
        if (conditionRaw.isEmpty()) {
            throw new IllegalArgumentException("DELETE WHERE clause requires a condition");
        }

        return new DeleteCommand(tableName, conditionRaw);
    }

    private static Command parseJoin(String command) {
        // Parse JOIN <Table1> AND <Table2> ON <Attr1> AND <Attr2>.
        String trimmed = command.trim();
        String upper = trimmed.toUpperCase();

        if (!upper.startsWith("JOIN ")) {
            throw new IllegalArgumentException("Invalid JOIN syntax. Expected: JOIN <table1> AND <table2> ON <attr1> AND <attr2>");
        }

        String rest = trimmed.substring("JOIN ".length()).trim();

        int andIndex = rest.toUpperCase().indexOf(" AND ");
        if (andIndex < 0) {
            throw new IllegalArgumentException("Invalid JOIN syntax. Missing AND between table names");
        }

        String leftTable = rest.substring(0, andIndex).trim();
        if (leftTable.isEmpty()) {
            throw new IllegalArgumentException("First table name is required for JOIN");
        }
        leftTable = validateIdentifier(leftTable, "table");

        String afterAnd = rest.substring(andIndex + " AND ".length()).trim();
        int onIndex = afterAnd.toUpperCase().indexOf(" ON ");
        if (onIndex < 0) {
            throw new IllegalArgumentException("Invalid JOIN syntax. Missing ON clause");
        }

        String rightTable = afterAnd.substring(0, onIndex).trim();
        if (rightTable.isEmpty()) {
            throw new IllegalArgumentException("Second table name is required for JOIN");
        }
        rightTable = validateIdentifier(rightTable, "table");

        String onClause = afterAnd.substring(onIndex + " ON ".length()).trim();
        String[] onParts = onClause.split("(?i)\\s+AND\\s+", 2);
        if (onParts.length < 2) {
            throw new IllegalArgumentException("Invalid JOIN syntax. ON clause must have two attributes separated by AND");
        }

        String leftAttribute = validateIdentifier(onParts[0].trim(), "attribute");
        String rightAttribute = validateIdentifier(onParts[1].trim(), "attribute");

        return new JoinCommand(leftTable, rightTable, leftAttribute, rightAttribute);
    }
}