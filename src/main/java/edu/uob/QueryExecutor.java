package edu.uob;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // TODO: delete database folder recursively.
            return notImplemented("DROP DATABASE");
        }

        @Override
        public String visit(QueryParser.DropTableCommand command) {
            // TODO: delete table .tab file from current database.
            return notImplemented("DROP TABLE");
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

        ////////  DEBUG ///////////
        System.out.println("Saving table: " + table.getName());
        ////////       ///////////

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


    ///  I/O / Debug Helpers ///

}
