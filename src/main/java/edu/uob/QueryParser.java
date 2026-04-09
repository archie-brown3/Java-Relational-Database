package edu.uob;

import java.util.ArrayList;
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
        // Method signature for a visitor pattern, declares a placeholder type for the method where r is some type determined at runtime
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

    public record SelectCommand(List<String> selectedAttributes, String tableName, String rawCondition) implements Command {
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
        // Minimal USE parser scaffold to enable visitor dispatch.
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
            String attribute = validateIdentifier(token.trim(), "attribute");
            attributes.add(attribute);
        }
        return attributes;
    }

    private static String validateIdentifier(String raw, String kind) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing " + kind + " name");
        }

        String trimmed = raw.trim();
        if (!trimmed.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Invalid " + kind + " name: " + trimmed);
        }
        return trimmed;
    }

    private static Command parseDrop(String command) {
        // TODO: Route between DROP DATABASE and DROP TABLE forms.
        // Two stage parse
        // Validate and route first two keywords:
        String normalised = command.trim().toUpperCase();
        String[] parts = normalised.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid DROP syntax. Expected: DROP TABLE <name> or DROP DATABASE <name>");
        }
        if (!parts[0].equalsIgnoreCase("DROP")) {
            throw new IllegalArgumentException("Invalid DROP syntax");
        }
        String targetType = parts[1];
        String targetName = validateIdentifier(parts[2], targetType.equals("TABLE") ? "table" : "database");

        return switch (targetType){
            case "DATABASE" -> new DropDatabaseCommand(targetName);
            case "TABLE" -> new DropTableCommand(targetName);
            default -> throw new IllegalArgumentException("Invalid DROP syntax");
        };
    }

    private static Command parseAlter(String command) {
        // TODO: Parse ALTER TABLE [TableName] [ADD|DROP] [AttributeName].
        throw new UnsupportedOperationException("TODO: implement ALTER parsing");
    }

    private static Command parseInsert(String command) {
        // TODO: Parse INSERT INTO [TableName] VALUES(...).
        // TODO: Preserve quoted strings and embedded whitespace.
        throw new UnsupportedOperationException("TODO: implement INSERT parsing");
    }

    private static Command parseSelect(String command) {
        // TODO: Parse SELECT <WildAttribList> FROM [TableName] [WHERE <Condition>].
        throw new UnsupportedOperationException("TODO: implement SELECT parsing");
    }

    private static Command parseUpdate(String command) {
        // TODO: Parse UPDATE [TableName] SET <NameValueList> WHERE <Condition>.
        throw new UnsupportedOperationException("TODO: implement UPDATE parsing");
    }

    private static Command parseDelete(String command) {
        // TODO: Parse DELETE FROM [TableName] WHERE <Condition>.
        throw new UnsupportedOperationException("TODO: implement DELETE parsing");
    }

    private static Command parseJoin(String command) {
        // TODO: Parse JOIN [TableName] AND [TableName] ON [AttributeName] AND [AttributeName].
        throw new UnsupportedOperationException("TODO: implement JOIN parsing");
    }
}