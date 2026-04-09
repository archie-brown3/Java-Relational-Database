package edu.uob;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

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
                System.out.println("Using database: " + databaseName + " in " + databaseFolderPath);
            }

            // TODO: validate database exists in storage, then set active database.
            return notImplemented("USE");
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
            File file = new File(context.getStorageFolderPath() + File.separator +
                    context.getCurrentDatabase() + File.separator + command.tableName() + ".tab");
            file.delete();
            if(!file.exists()) {
                return "[OK] Deleted " + command.tableName() + " in " + context.getCurrentDatabase();
            }
            return "File " + command.tableName() + " could not be deleted"; //todo: add error handling
        }

        @Override
        public String visit(QueryParser.AlterCommand command) {
            // TODO: support ALTER TABLE ADD/DROP while enforcing ID-column rules.
            return notImplemented("ALTER TABLE");
        }

        @Override
        public String visit(QueryParser.InsertCommand command) {
            // TODO: load table, append row with generated id, persist file.
            return notImplemented("INSERT");
        }

        @Override
        public String visit(QueryParser.SelectCommand command) {
            // TODO: load table, filter rows by condition, format result text.
            return notImplemented("SELECT");
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

        private String withTableRead(String tableName, Function<Table, String> action) {
            // Helper wrapper for read-only commands (e.g. SELECT).
            // Steps to implement:
            // 1) Resolve and validate table file using resolveTableFile(tableName).
            File tableFile = resolveTableFile(tableName);
            // 2) Load the table into memory via executor.load(...).
            // 3) Execute the supplied read action and return its formatted response.
            // 4) Map IO/validation failures to a single [ERROR] response format.
            throw new UnsupportedOperationException("TODO: implement withTableRead helper");
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
            String currentDb = executor.normaliseDatabaseName(context.getCurrentDatabase());
            // 2) Build path: <storageRoot>/<currentDatabase>/<tableName>.tab.

            // 3) Validate the path exists and is a regular file.
            if (currentDb == null) {
                return null; //todo: error handling
            }
            // 4) Return the File object for downstream load/save operations.
            return new File(context.getStorageFolderPath() + File.separator + currentDb + File.separator + tableName + ".tab");
        }

        private String requireCurrentDatabase() {
            // Validation helper for commands that require USE to be set.
            // Steps to implement:
            // 1) Read current database from context.
            String currentDb = executor.normaliseDatabaseName(context.getCurrentDatabase());
            // 2) Reject null/blank with a clear IllegalArgumentException message.
            if (currentDb == null) {
                throw new IllegalArgumentException("Database is not selected. Use a database first.");
            }
            // 3) Return normalized database name for path construction.
            return currentDb;
        }

        private String notImplemented(String commandName) {
            return "[ERROR] " + commandName + " execution not implemented yet";
        }
    }

    void readAndSaveTable(File fileToOpen, String destination) throws IOException, FileNotFoundException {
        saveTable(load(fileToOpen), destination);
    }

    // Load table from file
    Table load(File fileToOpen) throws IOException, FileNotFoundException {
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
        return databaseName.toLowerCase();
    }




    ///  I/O / Debug Helpers ///

}
