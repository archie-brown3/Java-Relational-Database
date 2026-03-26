package edu.uob;

import java.util.*;

// In memory representation of table's data rows
public class Row {
    private final int id;
    private final Table table;
    private final Map<String, String> data;

    Row(int id, Table table, Map<String, String> values) {
        this.id = id;
        this.table = table;
        this.data = new LinkedHashMap<>(values);
    }

    public int getId() { return id; }
    
    public String get(String columnName) {
        return data.get(columnName);
    }

    public void set(String columnName, String value) {
        if (table.getColumn(columnName).isEmpty()) {
            throw new IllegalArgumentException("Unknown column: " + columnName);
        }
        data.put(columnName, value);
    }

    public String[] toArray(List<String> columnOrder) {
        return columnOrder.stream()
            .map(col -> col.equalsIgnoreCase("id") ? String.valueOf(id) : data.getOrDefault(col, ""))
            .toArray(String[]::new);
    }
}
