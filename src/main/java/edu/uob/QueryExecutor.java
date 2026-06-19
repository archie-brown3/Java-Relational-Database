package edu.uob;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryExecutor {

    // Mutable runtime state used by command execution.
    // Keep this as a single context object so commands remain plain parsed data.
    public static class ExecutionContext {
        private final String storageFolderPath;
        private String currentDatabase;

        public ExecutionContext(String storageFolderPath) {
            this.storageFolderPath = storageFolderPath;
        }

        public String getStorageFolderPath() {
            return storageFolderPath;
        }

        public String getCurrentDatabase() {
            return currentDatabase;
        }

        public void setCurrentDatabase(String currentDatabase) {
            this.currentDatabase = currentDatabase;
        }
    }

    // Main execution entrypoint for DBServer: one call for every parsed command.
    public String execute(QueryParser.Command command, ExecutionContext context) {
        ExecutionVisitor visitor = new ExecutionVisitor(context, this);
        return command.accept(visitor);
    }

    // Visitor implementation that maps each command type to execution behavior.
    // TODO: Replace placeholder returns with real filesystem/table operations.
    public static class ExecutionVisitor implements QueryParser.CommandVisitor<String> {
        private final ExecutionContext context;
        private final QueryExecutor executor;

        public ExecutionVisitor(ExecutionContext context, QueryExecutor executor) {
            this.context = context;
            this.executor = executor;
        }

        @Override
        public String visit(QueryParser.UseCommand command) {
            String databaseName = command.databaseName();
            String databaseFolderPath = context.getStorageFolderPath() + File.separator + databaseName;
            if (!new File(databaseFolderPath).exists()) {
                return "[ERROR] Database does not exist";
            }
            context.setCurrentDatabase(databaseName);
            return "[OK]";
        }

        @Override
        public String visit(QueryParser.CreateDatabaseCommand command) {
            // Create database folder (lowercase), reject existing names.
            String databaseName = command.databaseName().toLowerCase();
            String databaseFolderPath = context.getStorageFolderPath() + File.separator + databaseName;
            File databaseFolder = new File(databaseFolderPath);

            if (databaseFolder.exists()) {
                return "[ERROR] Database already exists";
            }

            if (!databaseFolder.mkdir()) {
                return "[ERROR] Database could not be created";
            }

            context.setCurrentDatabase(databaseName);
            return "[OK]";
        }

        @Override
        public String visit(QueryParser.CreateTableCommand command) {
            // Create new .tab file with id + attributes header.
            try {
                requireCurrentDatabase();
                String databaseFolder = context.getStorageFolderPath() + File.separator + requireCurrentDatabase();
                File tableFile = new File(databaseFolder + File.separator + command.tableName() + ".tab");

                if (tableFile.exists()) {
                    return "[ERROR] Table " + command.tableName() + " already exists";
                }

                // Ensure the database directory exists
                new File(databaseFolder).mkdirs();

                // Write header: id + each attribute
                List<String> attributes = command.attributes();
                List<String> header = new ArrayList<>();
                header.add("id");
                header.addAll(attributes);

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tableFile))) {
                    writer.write(String.join("\t", header));
                    writer.newLine();
                }

                return "[OK]";
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not create table " + command.tableName();
            }
        }

        @Override
        public String visit(QueryParser.DropDatabaseCommand command) {
            String databaseName = command.databaseName();
            String databaseFolderPath = context.getStorageFolderPath() + File.separator + databaseName;
            File dir = new File(databaseFolderPath);

            if (!dir.exists()) {
                return "[ERROR] Database " + databaseName + " does not exist";
            }

            if (!removeDirectory(dir)) {
                return "[ERROR] Database " + databaseName + " could not be deleted";
            }

            // Clear current database if we dropped the active one
            if (databaseName.equalsIgnoreCase(context.getCurrentDatabase())) {
                context.setCurrentDatabase(null);
            }
            return "[OK]";
        }
        public Boolean removeDirectory(File file){
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (File f : files) {
                    removeDirectory(f);
                }
            }
            return file.delete();
        }



        @Override
        public String visit(QueryParser.DropTableCommand command) {
            // Delete table .tab file from current database.
            try {
                File file = resolveTableFile(command.tableName());
                if (!file.exists()) {
                    return "[ERROR] Table " + command.tableName() + " does not exist";
                }
                if (!file.delete()) {
                    return "[ERROR] Table " + command.tableName() + " could not be deleted";
                }
                return "[OK]";
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            }
        }

        @Override
        public String visit(QueryParser.AlterCommand command) {
            // TODO: support ALTER TABLE ADD/DROP while enforcing ID-column rules.
            return notImplemented("ALTER TABLE");
        }

        @Override
        public String visit(QueryParser.InsertCommand command) {
            // Load table, append row with generated id, persist file.
            try {
                File tableFile = resolveTableFile(command.tableName());
                if (!tableFile.exists() || !tableFile.isFile()) {
                    return "[ERROR] Table " + command.tableName() + " does not exist";
                }

                Table table = executor.load(tableFile);
                List<String> columns = table.getColumnNames();

                // Build value map: skip the id column (auto-generated), map values to column names
                Map<String, String> rowValues = new HashMap<>();
                List<String> values = command.values();

                // First column is always id (auto-generated), so map values starting from column index 1
                int valueIndex = 0;
                for (int i = 0; i < columns.size(); i++) {
                    String colName = columns.get(i);
                    if ("id".equalsIgnoreCase(colName)) {
                        continue; // id is auto-generated
                    }
                    if (valueIndex >= values.size()) {
                        return "[ERROR] Not enough values for table " + command.tableName();
                    }
                    rowValues.put(colName, values.get(valueIndex));
                    valueIndex++;
                }

                if (valueIndex != values.size()) {
                    return "[ERROR] Too many values for table " + command.tableName();
                }

                table.insertRow(rowValues);
                executor.saveTable(table, context.getStorageFolderPath() + File.separator + requireCurrentDatabase());
                return "[OK]";
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not write to table " + command.tableName();
            }
        }

        @Override
        public String visit(QueryParser.SelectCommand command) {
            Table table;
            try {
                File tableFile = resolveTableFile(command.tableName());
                if (!tableFile.exists() || !tableFile.isFile()) {
                    return "[ERROR] Table " + command.tableName() + " does not exist";
                }
                table = executor.load(tableFile);
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not read table " + command.tableName();
            }

            // Step 1: Decide which columns to output.
            List<String> requested = command.selectedAttributes();
            List<String> allColumns = table.getColumnNames();
            List<String> projection = new ArrayList<>();

            if (requested.contains("*")) {
                // Keep SELECT * simple: reject mixed wildcard + explicit columns.
                if (requested.size() != 1) {
                    return "[ERROR] '*' cannot be mixed with named columns";
                }
                projection.addAll(allColumns);
            } else {
                for (String requestedColumn : requested) {
                    String actualColumn = findColumnNameIgnoreCase(allColumns, requestedColumn);
                    if (actualColumn == null) {
                        return "[ERROR] Unknown column '" + requestedColumn + "'";
                    }
                    projection.add(actualColumn);
                }
            }

            // Step 2: Build header and then append matching rows.
            StringBuilder response = new StringBuilder();
            response.append("[OK]");
            response.append(System.lineSeparator());
            response.append(String.join("\t", projection));

            for (Row row : table.getAllRows()) {
                boolean includeRow;
                try {
                    includeRow = matchesRawCondition(table, row, command.rawCondition());
                } catch (IllegalArgumentException e) {
                    return "[ERROR] " + e.getMessage();
                }

                if (!includeRow) {
                    continue;
                }

                List<String> selectedValues = new ArrayList<>();
                for (String columnName : projection) {
                    if (columnName.equalsIgnoreCase("id")) {
                        selectedValues.add(String.valueOf(row.getId()));
                    } else {
                        selectedValues.add(row.get(columnName));
                    }
                }

                response.append(System.lineSeparator());
                response.append(String.join("\t", selectedValues));
            }

            return response.toString();
        }

        @Override
        public String visit(QueryParser.UpdateCommand command) {
            // TODO: load table, update matching rows, persist file.
            return notImplemented("UPDATE");
        }

        @Override
        public String visit(QueryParser.DeleteCommand command) {
            // TODO: load table, delete matching rows, persist file.
            return notImplemented("DELETE");
        }

        @Override
        public String visit(QueryParser.JoinCommand command) {
            // TODO: implement inner join output format from coursework brief.
            return notImplemented("JOIN");
        }

        private String withTableWrite(String tableName, Consumer<Table> action) {
            // Helper wrapper for mutating commands (e.g. INSERT/UPDATE/DELETE/ALTER).
            // Steps to implement:
            // 1) Resolve and validate table file using resolveTableFile(tableName).
            if (resolveTableFile(tableName) != null) {

            }
            // 2) Load current table state via executor.load(...).
            // 3) Apply mutation action against in-memory Table API.
            // 4) Persist changes via executor.saveTable(...).
            // 5) Return [OK] on success, [ERROR] on failure.
            throw new UnsupportedOperationException("TODO: implement withTableWrite helper");
        }

        private File resolveTableFile(String tableName) {
            // Centralized table path resolver.
            // Steps to implement:
            // 1) Validate a database is selected in ExecutionContext.
            String currentDb = requireCurrentDatabase();
            // 2) Build path: <storageRoot>/<currentDatabase>/<tableName>.tab.

            // 3) Return the File object for downstream load/save operations.
            return new File(context.getStorageFolderPath() + File.separator + currentDb + File.separator + tableName + ".tab");
        }

        private String requireCurrentDatabase() {
            // Validation helper for commands that require USE to be set.
            // Steps to implement:
            // 1) Read current database from context.
            String currentDb = context.getCurrentDatabase();
            // 2) Reject null/blank with a clear IllegalArgumentException message.
            if (currentDb == null || currentDb.isBlank()) {
                throw new IllegalArgumentException("Database is not selected. Use a database first.");
            }
            // 3) Return normalized database name for path construction.
            return executor.normaliseDatabaseName(currentDb);
        }

        private String notImplemented(String commandName) {
            return "[ERROR] " + commandName + " execution not implemented yet";
        }

        private String findColumnNameIgnoreCase(List<String> columnNames, String targetColumn) {
            for (String columnName : columnNames) {
                if (columnName.equalsIgnoreCase(targetColumn)) {
                    return columnName;
                }
            }
            return null;
        }

        private boolean matchesRawCondition(Table table, Row row, String rawCondition) {
            if (rawCondition == null || rawCondition.isBlank()) {
                return true;
            }

            String condition = rawCondition.trim();

            String[] orParts = condition.split("(?i)\\s+OR\\s+");
            if (orParts.length > 1) {
                for (String part : orParts) {
                    if (matchesRawCondition(table, row, part)) {
                        return true;
                    }
                }
                return false;
            }

            String[] andParts = condition.split("(?i)\\s+AND\\s+");
            if (andParts.length > 1) {
                for (String part : andParts) {
                    if (!matchesRawCondition(table, row, part)) {
                        return false;
                    }
                }
                return true;
            }

            return evaluateSingleCondition(table, row, condition);
        }

        private boolean evaluateSingleCondition(Table table, Row row, String condition) {
            Pattern pattern = Pattern.compile("^([A-Za-z0-9]+)\\s*(==|!=|>=|<=|>|<|(?i:LIKE))\\s*(.+)$");
            Matcher matcher = pattern.matcher(condition.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unsupported WHERE condition: " + condition);
            }

            String requestedColumn = matcher.group(1);
            String operator = matcher.group(2).toUpperCase();
            String rightRaw = stripMatchingQuotes(matcher.group(3).trim());

            String columnName = findColumnNameIgnoreCase(table.getColumnNames(), requestedColumn);
            if (columnName == null) {
                throw new IllegalArgumentException("Unknown column in WHERE: " + requestedColumn);
            }

            String leftValue;
            if (columnName.equalsIgnoreCase("id")) {
                leftValue = String.valueOf(row.getId());
            } else {
                leftValue = row.get(columnName);
            }

            if (leftValue == null) {
                leftValue = "";
            }

            return compareValues(leftValue, operator, rightRaw);
        }

        private boolean compareValues(String leftValue, String operator, String rightValue) {
            Double leftNumber = tryParseDouble(leftValue);
            Double rightNumber = tryParseDouble(rightValue);

            if (leftNumber != null && rightNumber != null) {
                return switch (operator) {
                    case "==" -> leftNumber.equals(rightNumber);
                    case "!=" -> !leftNumber.equals(rightNumber);
                    case ">" -> leftNumber > rightNumber;
                    case "<" -> leftNumber < rightNumber;
                    case ">=" -> leftNumber >= rightNumber;
                    case "<=" -> leftNumber <= rightNumber;
                    default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
                };
            }

            if ("LIKE".equals(operator)) {
                String regex = rightValue.replace("%", ".*");
                return leftValue.matches(regex);
            }

            return switch (operator) {
                case "==" -> leftValue.equals(rightValue);
                case "!=" -> !leftValue.equals(rightValue);
                default -> throw new IllegalArgumentException("Unsupported operator for text values: " + operator);
            };
        }

        private Double tryParseDouble(String value) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String stripMatchingQuotes(String value) {
            if (value.length() >= 2) {
                boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
                boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
                if (singleQuoted || doubleQuoted) {
                    return value.substring(1, value.length() - 1);
                }
            }
            return value;
        }
    }

    ///  I/O Helpers ///

    // Load table from file
    Table load(File fileToOpen) throws IOException, FileNotFoundException {
        String name = fileToOpen.getName().replaceFirst("\\.[^.]+$", "");
        String currentLine;
        String[] columnNames = new String[0];
        FileReader reader = new FileReader(fileToOpen);
        BufferedReader buffReader = new BufferedReader(reader);
        int rows = 0;
        Table table = new Table(name, new ArrayList<>());
        // Parse columns
        while ((currentLine = buffReader.readLine()) != null) {
            if (rows == 0) {
                columnNames = currentLine.split("\\t");
                for (String columnName : columnNames) {
                    table.addColumn(new Column(columnName));
                }
            } else {
                String[] rowData = currentLine.split("\\t", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < rowData.length; i++) {
                    row.put(columnNames[i], rowData[i]);
                }
                table.insertRow(row);
            }
            rows++;
        }
        buffReader.close();
        return table;
    }

    // Save table to file
    public void saveTable(Table table, String destination) throws IOException {
        File destDir = new File(destination);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        FileWriter writer = new FileWriter(destination + File.separator + table.getName() + ".tab");
        BufferedWriter buffWriter = new BufferedWriter(writer);
        List<String> columns = table.getColumnNames();
        // Write column names to header
        buffWriter.write(String.join("\t", columns));
        buffWriter.newLine();
        // Write data rows
        for (Row row : table.getAllRows()) {
            String[] rowData = row.toArray(columns);
            buffWriter.write(String.join("\t", rowData));
            buffWriter.newLine();
        }
        buffWriter.close();
    }

    public String normaliseDatabaseName(String databaseName) {
        if (databaseName == null) {
            return null;
        }
        return databaseName.toLowerCase();
    }

}
