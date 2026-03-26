package edu.uob;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryExecutor {

    // Interface
    public interface QueryCommand {
        void execute(Table table);
    }

    void readAndSaveTable(File fileToOpen, String destination) throws IOException, FileNotFoundException {
        saveTable(load(fileToOpen), destination);
    }

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

    // todo: update from basic array to more advanced data structure

    public void handleWrite(File fileToOpen) throws IOException, FileNotFoundException {
        return;
    }

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


        // Insert command
        public static class InsertCommand implements QueryCommand {
            String tableName;
            Map<String, String> values;

            // Constructor
            public InsertCommand(String tableName, Map<String, String> values) {
                this.tableName = tableName;
                this.values = values;
            }
            @Override
            public void execute(Table table){
                table.insertRow(values);
            }
        }


        // Delete command
        public static class DeleteCommand implements QueryCommand {
            String tableName;
            int id;

            // Constructor
            public DeleteCommand(String tableName, int id) {
                this.tableName = tableName;
                this.id = id;
            }
            @Override
            public void execute(Table table){
                table.deleteRow(id);
            }
        }

        // Select command










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
