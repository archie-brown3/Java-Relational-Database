package edu.uob;

import java.io.*;
import java.util.ArrayList;

public class CommandHandler extends Table{

    void readAndSaveTable(File fileToOpen, String destination) throws IOException, FileNotFoundException {
        saveTable(handleRead(fileToOpen), destination);
    }

    Table handleRead(File fileToOpen) throws IOException, FileNotFoundException {
        String tableName = fileToOpen.getName().replaceFirst("\\.[^.]+$", "");
        System.out.println("Reading table: " + tableName);
        String currentLine = " ";
        FileReader reader = new FileReader(fileToOpen);
        BufferedReader buffReader = new BufferedReader(reader);
        int rows = 0;

        // TODO: refactor to only read files, move table constructor logic to seperate method
        Table table = new Table();
        table.tableName = tableName;
        table.rows = new ArrayList<>();

        while ((currentLine = buffReader.readLine()) != null) {
            // Read labels from the first line only
            if (rows == 0) {
                table.columnNames = currentLine.split("\t");
            } else {
                // Read data from rows
                String[] rowData = currentLine.split("\t");
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
        buffReader.close();
        return table;

    }

    // todo: update from basic array to more advanced data structure
    public int generatePrimaryKey(Table table){
        int maxUsedId = table.existingIds.stream().max(Integer::compareTo).get();
        return maxUsedId + 1;
    }

    public void handleWrite(File fileToOpen) throws IOException, FileNotFoundException {
        return;
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
