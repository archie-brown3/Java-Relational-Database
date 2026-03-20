package edu.uob;


// Contains metadata about column from table Class
public class Column {
    enum columnType {
        INTEGER,
        STRING,
        BOOLEAN
    }

    private int parentTableId;
    private columnType type;
    private String columnName;
    private Boolean isForeignKey;
    private Boolean isPrimaryKey;
    private String referencesTable;
    private String referencesColumn;

    // Constructor
    public Column(int parentTableId, String columnName) {

    }



    // Getters and Setters

    public String getColumnName() {
        return columnName;
    }

    public String getType() {
        return type.toString();
    }

    public boolean isForeignKey() {
        return isForeignKey;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public String getReferencesTable() {
        return referencesTable;
    }

    public String getReferencesColumn() {
        return referencesColumn;
    }



}