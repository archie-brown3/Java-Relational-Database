package db.engine;

public record Column(String name, DataType type) {

    public Column(String name) {
        this(name, DataType.STRING);
    }

    public enum DataType {
        STRING, INTEGER, FLOAT, BOOLEAN
    }
}
