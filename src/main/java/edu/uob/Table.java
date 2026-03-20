package edu.uob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// In memory data model representing single tables data and metadata
public class Table {
    String tableName;
    List<Column> columns;
    public Set<Integer> existingIds = new HashSet<>();
    int maxUsedId;
    List<String[]> rows;
    int rowCount;
    int nextId = nextId(); // todo: move to constructor

    //todo: constructor method



    // todo: update from basic array to more advanced data structure
    public int getMaxUsedId() {
        return this.existingIds.stream().max(Integer::compareTo).get();
    }

    void insertRow(Table table, String[] row){
        table.existingIds.add(nextId());
        table.rows.add(row);
        table.rowCount++;
    }

    void deleteRow(Table table, int id){
        table.existingIds.remove(id);
        table.rows.remove(id);
        table.rowCount--;
    }

    int nextId(){
        return maxUsedId++;
    }

    String[] parseRow(String line){
        return line.split("\t");
    }

}
