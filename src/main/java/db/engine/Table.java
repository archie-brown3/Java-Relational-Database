package db.engine;

import java.util.*;

public class Table {
    private final String name;
    private final List<Column> schema;
    private final Map<Integer, Row> rows;  // ID → Row for O(1) lookup
    private int nextId;

    public Table(String name, List<Column> schema) {
        this.name = name;
        this.schema = new ArrayList<>(schema);
        this.rows = new LinkedHashMap<>();  // Preserves insertion order
        this.nextId = 1;
    }

    // --- Row Operations ---
    
    public Row insertRow(Map<String, String> values) throws IllegalArgumentException{
        for (Column column : schema) {
            if(column.isForeignKey()){
                // Get the value of the foreign key column
                String FKvalue = values.get(column.name());
                if (column.isForeignKey() && FKvalue != null) {
                    throw new IllegalArgumentException("Foreign key violation: " + FKvalue);
                }
            }

        }
        int id = nextId++;
        Row row = new Row(id, this, values);
        rows.put(id, row); // key value store
        return row;
        // todo: add error handling
    }

    public Optional<Row> getRowById(int id) {
        return Optional.ofNullable(rows.get(id));
    }

    public boolean deleteRow(int id) {
        return rows.remove(id) != null;
    }

    public List<Row> getAllRows() {
        return new ArrayList<>(rows.values());
    }

    public List<Row> selectWhere(String columnName, String value) {
        return rows.values().stream()
            .filter(row -> value.equals(row.get(columnName)))
            .toList();
    }

    // --- Schema Operations ---
    
    public void addColumn(Column column) {
        schema.add(column);
        // Set default empty values for existing rows
        for (Row row : rows.values()) {
            row.initializeColumn(column.name());
        }
    }

    public boolean removeColumn(String columnName) {
        // Find and remove the column from schema
        Column toRemove = null;
        for (Column col : schema) {
            if (col.name().equalsIgnoreCase(columnName)) {
                toRemove = col;
                break;
            }
        }
        if (toRemove == null) return false;
        schema.remove(toRemove);

        // Remove the column data from all rows
        for (Row row : rows.values()) {
            row.removeColumn(columnName);
        }
        return true;
    }

    public Optional<Column> getColumn(String name) {
        return schema.stream()
            .filter(c -> c.name().equalsIgnoreCase(name))
            .findFirst();
    }

    public List<String> getColumnNames() {
        return schema.stream().map(Column::name).toList();
    }

    public int getColumnIndex(String name) {
        for (int i = 0; i < schema.size(); i++) {
            if (schema.get(i).name().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    // --- Getters ---
    
    public String getName() { return name; }
    public int getRowCount() { return rows.size(); }
    public boolean isEmpty() { return rows.isEmpty(); }
}

