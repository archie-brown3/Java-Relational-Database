package db.engine;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

                List<String> attributes = command.attributes();

                // Reject 'id' as a column name — it conflicts with the auto-generated primary key
                for (String attr : attributes) {
                    if ("id".equalsIgnoreCase(attr)) {
                        return "[ERROR] Cannot create column named 'id' — it is reserved for the primary key";
                    }
                }

                // Write header: id + each attribute
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
            String databaseName = command.databaseName().toLowerCase();
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
        private boolean removeDirectory(File file) {
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
            // Support ALTER TABLE ADD/DROP while enforcing ID-column rules.
            try {
                File tableFile = resolveTableFile(command.tableName());
                if (!tableFile.exists() || !tableFile.isFile()) {
                    return "[ERROR] Table " + command.tableName() + " does not exist";
                }

                String attributeName = command.attributeName();
                if ("id".equalsIgnoreCase(attributeName)) {
                    return "[ERROR] Cannot alter the id column";
                }

                Table table = executor.load(tableFile);
                String alteration = command.alterationType();

                if ("ADD".equals(alteration)) {
                    // Check column doesn't already exist
                    if (findColumnNameIgnoreCase(table.getColumnNames(), attributeName) != null) {
                        return "[ERROR] Column '" + attributeName + "' already exists";
                    }
                    table.addColumn(new Column(attributeName));
                } else if ("DROP".equals(alteration)) {
                    // Check column exists
                    String actualCol = findColumnNameIgnoreCase(table.getColumnNames(), attributeName);
                    if (actualCol == null) {
                        return "[ERROR] Column '" + attributeName + "' does not exist";
                    }
                    table.removeColumn(actualCol);
                } else {
                    return "[ERROR] Unknown ALTER operation: " + alteration;
                }

                executor.saveTable(table, context.getStorageFolderPath() + File.separator + requireCurrentDatabase());
                return "[OK]";
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not alter table " + command.tableName();
            }
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

            // Step 2: Collect matching rows, apply grouping if requested.
            List<Row> matchingRows = new ArrayList<>();
            for (Row row : table.getAllRows()) {
                boolean includeRow;
                try {
                    includeRow = matchesRawCondition(table, row, command.rawCondition());
                } catch (IllegalArgumentException e) {
                    return "[ERROR] " + e.getMessage();
                }

                if (includeRow) {
                    matchingRows.add(row);
                }
            }

            // Group-by aggregation branch
            if (command.groupByColumn() != null) {
                String groupCol = findColumnNameIgnoreCase(allColumns, command.groupByColumn());
                if (groupCol == null) {
                    return "[ERROR] Unknown column in GROUP BY: " + command.groupByColumn();
                }
                if (!projection.contains(groupCol)) {
                    return "[ERROR] GROUP BY column '" + groupCol + "' must appear in SELECT";
                }

                String aggFn = command.aggregateFunction();
                if (aggFn == null) {
                    return "[ERROR] SELECT with GROUP BY requires an aggregate function (COUNT, SUM, AVG)";
                }

                // Group rows by the group column value
                Map<String, List<Row>> groups = new LinkedHashMap<>();
                for (Row row : matchingRows) {
                    String key = row.get(groupCol);
                    if (key == null) key = "";
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                }

                // Compute aggregate per group and build output
                StringBuilder response = new StringBuilder();
                response.append("[OK]");
                response.append(System.lineSeparator());

                // Header: group column and aggregate label
                String aggLabel = aggFn + "(" + (command.aggregateColumn() != null ? command.aggregateColumn() : "*") + ")";
                response.append(groupCol);
                response.append("\t");
                response.append(aggLabel);

                for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
                    response.append(System.lineSeparator());
                    response.append(entry.getKey());
                    response.append("\t");

                    List<Row> groupRows = entry.getValue();
                    String aggResult = switch (aggFn) {
                        case "COUNT" -> String.valueOf(groupRows.size());
                        case "SUM" -> {
                            double sum = 0;
                            String aggCol = command.aggregateColumn();
                            String actualAggCol = findColumnNameIgnoreCase(allColumns, aggCol);
                            if (actualAggCol == null) {
                                throw new IllegalArgumentException("Unknown column in aggregate: " + aggCol);
                            }
                            for (Row r : groupRows) {
                                String val = r.get(actualAggCol);
                                Double num = tryParseDouble(val);
                                if (num == null) {
                                    throw new IllegalArgumentException("Cannot SUM non-numeric value: " + val);
                                }
                                sum += num;
                            }
                            yield formatNumber(sum);
                        }
                        case "AVG" -> {
                            double sum = 0;
                            String aggCol = command.aggregateColumn();
                            String actualAggCol = findColumnNameIgnoreCase(allColumns, aggCol);
                            if (actualAggCol == null) {
                                throw new IllegalArgumentException("Unknown column in aggregate: " + aggCol);
                            }
                            for (Row r : groupRows) {
                                String val = r.get(actualAggCol);
                                Double num = tryParseDouble(val);
                                if (num == null) {
                                    throw new IllegalArgumentException("Cannot AVG non-numeric value: " + val);
                                }
                                sum += num;
                            }
                            yield formatNumber(sum / groupRows.size());
                        }
                        default -> throw new IllegalArgumentException("Unknown aggregate function: " + aggFn);
                    };
                    response.append(aggResult);
                }

                return response.toString();
            }

            // Standard (non-grouped) output
            StringBuilder response = new StringBuilder();
            response.append("[OK]");
            response.append(System.lineSeparator());
            response.append(String.join("\t", projection));

            // Apply ORDER BY if specified
            if (command.orderByColumn() != null) {
                String orderCol = findColumnNameIgnoreCase(allColumns, command.orderByColumn());
                if (orderCol == null) {
                    return "[ERROR] Unknown column in ORDER BY: " + command.orderByColumn();
                }
                final String sortColumn = orderCol;
                boolean descending = command.orderByDesc();

                matchingRows.sort((a, b) -> {
                    String valA = sortColumn.equalsIgnoreCase("id") ? String.valueOf(a.getId()) : a.get(sortColumn);
                    String valB = sortColumn.equalsIgnoreCase("id") ? String.valueOf(b.getId()) : b.get(sortColumn);
                    if (valA == null) valA = "";
                    if (valB == null) valB = "";

                    Double numA = tryParseDouble(valA);
                    Double numB = tryParseDouble(valB);
                    int cmp;
                    if (numA != null && numB != null) {
                        cmp = numA.compareTo(numB);
                    } else {
                        cmp = valA.compareTo(valB);
                    }
                    return descending ? -cmp : cmp;
                });
            }

            // Apply DISTINCT deduplication after sorting
            if (command.distinct()) {
                List<Row> distinctRows = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (Row row : matchingRows) {
                    StringBuilder key = new StringBuilder();
                    for (String columnName : projection) {
                        if (columnName.equalsIgnoreCase("id")) {
                            key.append(String.valueOf(row.getId()));
                        } else {
                            key.append(row.get(columnName));
                        }
                        key.append("\t");
                    }
                    if (seen.add(key.toString())) {
                        distinctRows.add(row);
                    }
                }
                matchingRows = distinctRows;
            }

            // Apply LIMIT/OFFSET after DISTINCT
            if (command.limitCount() >= 0) {
                int offset = Math.max(0, command.offsetCount());
                int limit = command.limitCount();
                int fromIndex = Math.min(offset, matchingRows.size());
                int toIndex = Math.min(fromIndex + limit, matchingRows.size());
                matchingRows = matchingRows.subList(fromIndex, toIndex);
            }

            for (Row row : matchingRows) {
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
            // Load table, update matching rows, persist file.
            try {
                File tableFile = resolveTableFile(command.tableName());
                if (!tableFile.exists() || !tableFile.isFile()) {
                    return "[ERROR] Table " + command.tableName() + " does not exist";
                }

                Table table = executor.load(tableFile);
                Map<String, String> assignments = command.assignments();
                int updatedCount = 0;

                // Validate all assignment columns exist before making changes
                for (String colName : assignments.keySet()) {
                    if ("id".equalsIgnoreCase(colName)) {
                        return "[ERROR] Cannot update id column";
                    }
                    if (findColumnNameIgnoreCase(table.getColumnNames(), colName) == null) {
                        return "[ERROR] Unknown column '" + colName + "'";
                    }
                }

                for (Row row : table.getAllRows()) {
                    try {
                        if (matchesRawCondition(table, row, command.rawCondition())) {
                            for (var entry : assignments.entrySet()) {
                                String actualCol = findColumnNameIgnoreCase(table.getColumnNames(), entry.getKey());
                                row.set(actualCol, entry.getValue());
                            }
                            updatedCount++;
                        }
                    } catch (IllegalArgumentException e) {
                        return "[ERROR] " + e.getMessage();
                    }
                }

                executor.saveTable(table, context.getStorageFolderPath() + File.separator + requireCurrentDatabase());
                return "[OK] " + updatedCount + " row(s) updated";
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not update table " + command.tableName();
            }
        }

        @Override
        public String visit(QueryParser.DeleteCommand command) {
            // Load table, delete matching rows, persist file.
            try {
                File tableFile = resolveTableFile(command.tableName());
                if (!tableFile.exists() || !tableFile.isFile()) {
                    return "[ERROR] Table " + command.tableName() + " does not exist";
                }

                Table table = executor.load(tableFile);
                int deletedCount = 0;

                // Build list of IDs to delete (avoid ConcurrentModificationException)
                List<Integer> idsToDelete = new ArrayList<>();
                for (Row row : table.getAllRows()) {
                    try {
                        if (matchesRawCondition(table, row, command.rawCondition())) {
                            idsToDelete.add(row.getId());
                        }
                    } catch (IllegalArgumentException e) {
                        return "[ERROR] " + e.getMessage();
                    }
                }

                for (int id : idsToDelete) {
                    table.deleteRow(id);
                    deletedCount++;
                }

                executor.saveTable(table, context.getStorageFolderPath() + File.separator + requireCurrentDatabase());
                return "[OK] " + deletedCount + " row(s) deleted";
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not delete from table " + command.tableName();
            }
        }

        @Override
        public String visit(QueryParser.JoinCommand command) {
            // Inner join: load both tables, match on specified attributes, return combined rows.
            try {
                File leftFile = resolveTableFile(command.leftTable());
                File rightFile = resolveTableFile(command.rightTable());

                if (!leftFile.exists() || !leftFile.isFile()) {
                    return "[ERROR] Table " + command.leftTable() + " does not exist";
                }
                if (!rightFile.exists() || !rightFile.isFile()) {
                    return "[ERROR] Table " + command.rightTable() + " does not exist";
                }

                Table leftTable = executor.load(leftFile);
                Table rightTable = executor.load(rightFile);

                // Validate join attributes exist
                String leftCol = findColumnNameIgnoreCase(leftTable.getColumnNames(), command.leftAttribute());
                String rightCol = findColumnNameIgnoreCase(rightTable.getColumnNames(), command.rightAttribute());
                if (leftCol == null) {
                    return "[ERROR] Column '" + command.leftAttribute() + "' not found in " + command.leftTable();
                }
                if (rightCol == null) {
                    return "[ERROR] Column '" + command.rightAttribute() + "' not found in " + command.rightTable();
                }

                // Build header: id, leftTable.attr1, leftTable.attr2, ..., rightTable.attr1, rightTable.attr2, ...
                List<String> leftColumns = leftTable.getColumnNames();
                List<String> rightColumns = rightTable.getColumnNames();

                StringBuilder response = new StringBuilder("[OK]");
                response.append(System.lineSeparator());
                response.append("id\t");

                List<String> outputColumns = new ArrayList<>();
                for (String col : leftColumns) {
                    if ("id".equalsIgnoreCase(col)) continue;
                    outputColumns.add(command.leftTable() + "." + col);
                }
                for (String col : rightColumns) {
                    if ("id".equalsIgnoreCase(col)) continue;
                    outputColumns.add(command.rightTable() + "." + col);
                }
                response.append(String.join("\t", outputColumns));

                // Perform inner join
                Set<Integer> matchedLeftIds = new HashSet<>();
                for (Row leftRow : leftTable.getAllRows()) {
                    String leftValue = leftRow.get(leftCol);
                    if (leftValue == null) leftValue = "";

                    for (Row rightRow : rightTable.getAllRows()) {
                        String rightValue = rightRow.get(rightCol);
                        if (rightValue == null) rightValue = "";

                        if (leftValue.equals(rightValue)) {
                            matchedLeftIds.add(leftRow.getId());
                            response.append(System.lineSeparator());
                            response.append(leftRow.getId()).append("\t");
                            List<String> joinedValues = new ArrayList<>();
                            for (String col : leftColumns) {
                                if ("id".equalsIgnoreCase(col)) continue;
                                String val = leftRow.get(col);
                                joinedValues.add(val != null ? val : "");
                            }
                            for (String col : rightColumns) {
                                if ("id".equalsIgnoreCase(col)) continue;
                                String val = rightRow.get(col);
                                joinedValues.add(val != null ? val : "");
                            }
                            response.append(String.join("\t", joinedValues));
                        }
                    }
                }

                // LEFT JOIN: append unmatched left-table rows with blanks for right-table columns
                if (command.leftJoin()) {
                    for (Row leftRow : leftTable.getAllRows()) {
                        if (!matchedLeftIds.contains(leftRow.getId())) {
                            response.append(System.lineSeparator());
                            response.append(leftRow.getId()).append("\t");
                            List<String> joinedValues = new ArrayList<>();
                            for (String col : leftColumns) {
                                if ("id".equalsIgnoreCase(col)) continue;
                                String val = leftRow.get(col);
                                joinedValues.add(val != null ? val : "");
                            }
                            for (String col : rightColumns) {
                                if ("id".equalsIgnoreCase(col)) continue;
                                joinedValues.add("");
                            }
                            response.append(String.join("\t", joinedValues));
                        }
                    }
                }

                return response.toString();
            } catch (IllegalArgumentException e) {
                return "[ERROR] " + e.getMessage();
            } catch (IOException e) {
                return "[ERROR] Could not perform JOIN operation";
            }
        }

        private File resolveTableFile(String tableName) {
            // Build path: <storageRoot>/<currentDatabase>/<tableName>.tab
            String currentDb = requireCurrentDatabase();
            return new File(context.getStorageFolderPath() + File.separator + currentDb + File.separator + tableName + ".tab");
        }

        private String requireCurrentDatabase() {
            // Read current database from context and validate it is set.
            String currentDb = context.getCurrentDatabase();
            if (currentDb == null || currentDb.isBlank()) {
                throw new IllegalArgumentException("Database is not selected. Use a database first.");
            }
            return executor.normaliseDatabaseName(currentDb);
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

        private String formatNumber(double value) {
            if (value == Math.floor(value) && !Double.isInfinite(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
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
