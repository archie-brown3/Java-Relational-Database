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
            String databaseFolderPath = context.getStorageFolderPath() + File.separator + databaseName; // todo: change to file (table)
            if(!new File(databaseFolderPath).exists()) {
                return "[ERROR] Database does not exist";
            }
            else{
                context.setCurrentDatabase(databaseName);
                return "[OK]";
            }
            // TODO: validate database exists in storage, then set active database.
        }

        @Override
        public String visit(QueryParser.CreateDatabaseCommand command) {
            // TODO: create database folder (lowercase), reject existing names.
            // Get database name + normalise to lowercase
            String databaseName = command.databaseName().toLowerCase();
            String databaseFolderPath = context.getStorageFolderPath() + File.separator + databaseName;
            if(new File(databaseFolderPath).exists()) {
                return "[ERROR] Database already exists";
            }
            else{ // else create new folder
                System.out.println("Creating database: " + databaseName + " in " + databaseFolderPath); //todo: change to mkdirs if parent doesn't exist
                new File(databaseFolderPath).mkdir();
                context.setCurrentDatabase(databaseName);
                // check that the database folder was created
                if(!new File(databaseFolderPath).exists()) {
                    return "[ERROR] Database could not be created";
                } else {
                    return "Database created successfully";
                }
            }
        }

        @Override
        public String visit(QueryParser.CreateTableCommand command) {
            // TODO: create new .tab file with id + attributes header.
            // new File
            return notImplemented("CREATE TABLE");
        }

        @Override
        public String visit(QueryParser.DropDatabaseCommand command) {
            // TODO: clean up code
            String databaseName = command.databaseName();
            String databaseFolderPath = context.getStorageFolderPath() + File.separator + databaseName;
            File dir = new File(databaseFolderPath);
            boolean deleted = removeDirectory(dir);
            if(!new File(databaseFolderPath).exists()) { // todo: add error validation for !repo
                return "[ERROR] Database " + databaseName + " does not exist";
            }
            else {
                // Delete the database folder recursively
                // check the folder !exists
                if (new File(databaseFolderPath).exists()) {
                    return("[ERROR] Database " + databaseName + " Could not be deleted");
                }
                return "[OK] Deleted " + databaseName + " in " + databaseFolderPath;
            }
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
            // TODO: delete table .tab file from current database.
            File file = resolveTableFile(command.tableName());
            try {
                boolean deleted = file.delete();
                if (!deleted) {
                    return "[ERROR] File " + command.tableName() + " could not be deleted"; //todo: add error handling
                }
                return "[0K] Table " + command.tableName() + " deleted successfully";
            } catch (IllegalArgumentException e){
                return "[ERROR]" + e.getMessage() ;
            }
        }

        @Override
        public String visit(QueryParser.AlterCommand command) {
            // TODO: support ALTER TABLE ADD/DROP while enforcing ID-column rules.
            return notImplemented("ALTER TABLE");
        }

        @Override
        public String visit(QueryParser.InsertCommand command) {
            // TODO: load table, append row with generated id, persist file.
            // Verify the table exists
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

            table.insertRow()
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

    void readAndSaveTable(File fileToOpen, String destination) throws IOException, FileNotFoundException {
        saveTable(load(fileToOpen), destination);
    }

    // Load table from file
    Table load(File fileToOpen) throws IOException, FileNotFoundException {
        // used to load a table from a file into memory
        String name  = fileToOpen.getName().replaceFirst("\\.[^.]+$", "");
        System.out.println("Reading table: " + name );
        String currentLine = " ";
        String[] columnNames = new String[0];
        FileReader reader = new FileReader(fileToOpen);
        BufferedReader buffReader = new BufferedReader(reader);
        int rows = 0;
        Table table = new Table(name , new ArrayList<>()); // initialise with name and empty schema
        // Parse columns
        while ((currentLine = buffReader.readLine()) != null) {
            if (rows == 0) {
                columnNames = currentLine.split("\t"); // Split column into string array
                List<Column> schema = new ArrayList<>();
                for (String columnName : columnNames){
                    table.addColumn(new Column(columnName));
                }
                // todo: update from using string as default type for all cols
            } else {
                String[] rowData = currentLine.split("\t", -1);
                Map<String,String> row = new HashMap<>(); // Key value pair for primary id's and values
                // Store row data
                for (int i = 0; i < rowData.length; i++) {
                    // Add column name, value pair to row
                    row.put(columnNames[i], rowData[i]);
                }
                table.insertRow(row);
            }
            rows++;
        }
        buffReader.close();
        printTable(table);
        return table;
    }

    public void handleWrite(File fileToOpen) throws IOException, FileNotFoundException {
        // todo: implement this
        return;
    }

    // Save table to file/memory
    // todo: refactor this to match the format of other classes below
    public void saveTable(Table table, String destination) throws IOException {
        FileWriter writer = new FileWriter(destination + File.separator + table.getName() + ".tab");
        BufferedWriter buffWriter = new BufferedWriter(writer);
        List<String> columns = table.getColumnNames();
        // Write columnNames to the header
        for (int i = 0; i < columns.size(); i++) {
            buffWriter.write(columns.get(i) + "\t");
        }
        // newline after header
        buffWriter.newLine();
        // write data to body
        for (Row row : table.getAllRows()) {
            String[] rowData = row.toArray(columns);
            buffWriter.write(String.join("\t", rowData));
            buffWriter.newLine();
        }
        buffWriter.close();
    }

    ///  I/O / Debug Helpers ///

    public void printTable(Table table) {
        System.out.println("PRINTING TABLE");
        List<String> columns = table.getColumnNames();

        // Header
        System.out.println(String.join("\t", columns));

        // Body
        for (Row row : table.getAllRows()) {
            String[] rowData = row.toArray(columns);
            System.out.println(String.join("\t", rowData));
        }
    }

    public String normaliseDatabaseName(String databaseName) {
        if (databaseName == null) {
            return null;
        }
        return databaseName.toLowerCase();
    }




    ///  I/O / Debug Helpers ///

}
