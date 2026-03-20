package edu.uob;

import java.io.*;
import java.util.ArrayList;


// Parse Queries and execute them
public class CommandHandler extends Table{

    void readAndSaveTable(File fileToOpen, String destination) throws IOException, FileNotFoundException {
        saveTable(handleRead(fileToOpen), destination);
    }

    Table handleRead(File fileToOpen) throws IOException, FileNotFoundException {
        String tableName = fileToOpen.getName().replaceFirst("\\.[^.]+$", "");
        // System.out.println("Reading table: " + tableName);
        String currentLine = " ";
        FileReader reader = new FileReader(fileToOpen);
        BufferedReader buffReader = new BufferedReader(reader);
        int rows = 0;

        // TODO: refactor to only read files, move table constructor logic to separate method
        // TODO: parse headers to Column objects, save row data correctly to in data structure
        Table table = new Table();
        table.tableName = tableName;
        table.rows = new ArrayList<>();

        while ((currentLine = buffReader.readLine()) != null) {
            // Create a column object for the first line
            if (rows == 0) {
                for (String columnName : parseRow(currentLine)) {
                    // Parse column name
                    Column col = new Column();
                }


            } else {
                // Read data from rows
                String[] rowData = parseRow(currentLine);
                int id = Integer.parseInt(rowData[0].trim());
                if (table.existingIds.contains(id)) {
                    System.out.println("Duplicate id: " + id);
                    throw new IOException();
                }
                table.existingIds.add(id);
                table.rows.add(rowData);
                table.rowCount++;
            }
            rows++;
        }
        table.maxUsedId = table.getMaxUsedId();
        buffReader.close();
        return table;
    }


    public void saveTable(Table table, String destination) throws IOException {
        FileWriter writer = new FileWriter(destination + File.separator + table.tableName + ".tab");
        BufferedWriter buffWriter = new BufferedWriter(writer);
        System.out.println("Saving table: " + table.tableName);
        // Write columnNames to the header
        for (int i = 0; i < table.columnNames.length; i++) {
            buffWriter.write(table.columnNames[i] + "\t");
        }
        // newline after header
        buffWriter.write(table.columnNames[0] + "\n");
        // write data to body
        for (String[] row : table.rows) {
            for (String cell : row) {
                buffWriter.write(cell + "\t");
            }
            buffWriter.write("\n");
        }
        buffWriter.close();
    }


    public void printRow(Table table) {
        for (String cell : table.columnNames) {
            System.out.printf("%-15s", cell);
        }
        System.out.println("\n");
    }


}
