package edu.uob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Table {
    public Set<Integer> existingIds = new HashSet<>();
    int maxUsedId;
    String[] columnNames;
    List<String[]> rows;
    int rowCount;
    String tableName;
}
