package edu.uob;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

public class CommandHandler {

    // Store all primary keys in table
    private Set<Integer> existingIds = new HashSet<>();

    public void handleRead(File fileToOpen) throws IOException, FileNotFoundException {
        existingIds.clear();
        String currentLine = " ";
        // Read fileToOpen
        FileReader reader = new FileReader(fileToOpen);
        BufferedReader buffReader = new BufferedReader(reader);
        String[] columns = null;
        int lines = 0;

        while ((currentLine = buffReader.readLine()) != null){
            // Check 0th column = id
            columns = currentLine.split("\t");        // use tab char as delimiter
            lines ++;
                

            if (columns.length > 0){
                // Collect primary ID's into set
                try {
                    int id = Integer.parseInt(columns[0].trim());
                    if existingIds.contains(id){
                        // Handle duplicate id
                    }
                }
        }



        if (columns.length != lines){
            System.out.println("One or more entries are not formatted correctly");
            throw new IOException();
        }





        buffReader.close();
    }

    public void handleWrite(File fileToOpen) throws IOException, FileNotFoundException{
        // Get Primary key
        //
    }


    // todo: update from basic array to more advanced data structure
    public boolean primaryKeyIsFree(int key, String[] table){
        set
        // Parse ID from string to int
        String i = table[0].;


        if (){
            return true;
        }

    }

    public int generatePrimaryKey(String[] table){
        int key = table.length + 1;

        if (!primaryKeyIsFree(key,table)){
            // Handle exception
        }
        return key;
    }



    public void printTable(String[] table){
        for (String cell: table){
            System.out.printf("%-15s", cell);
        }
        System.out.println("\n");
    }




}
