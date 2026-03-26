package edu.uob;

public record Column(String name, DataType type, String referenceTable, String referenceColumn ) {

    // Basic constructor
    public Column(String name) {
        this(name, DataType.STRING, null, null);  // Default to string
    }

    // Constructor with type
    public Column(String name, DataType type) {
        this(name, type, null, null);  // Default to string
    }

    // Constructor for FK columns
    public Column(String name, DataType type, String referenceTable, String referenceColumn) {
        this.name = name;
        this.type = type;
        this.referenceTable = referenceTable;
        this.referenceColumn = referenceColumn;
    }

    public enum DataType {
        STRING, INTEGER, FLOAT, BOOLEAN
    }

    public boolean isForeignKey() {
        return referenceTable != null;
    }
}



