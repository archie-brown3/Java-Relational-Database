package edu.uob;

import edu.uob.Table;

public class QueryParser {

    // Responsible for parsing the query and returning a QueryCommand object
    public interface QueryCommand {
        void execute(Table table);
    }

    public static QueryCommand parse(String command) {
        String[] tokens = command.trim().split("\\s+", 2);
        // tokens[0] = "INSERT"
        String action = tokens[0].toUpperCase();
        String clause = tokens.length > 1 ? tokens[1] : "";
        // tokens[1] = "INTO people VALUES (1, Bob)"

        switch(action) {
            case "INSERT": return parseInsert(clause);
            case "SELECT": return parseSelect(clause);
            case "DELETE": return parseDelete(clause);
            default: throw new IllegalArgumentException("Unknown: " + action);
        }
    }

    private static QueryCommand parseInsert(String clause) {

    }
    private static QueryCommand parseSelect(String clause) {

    }
    private static QueryCommand parseDelete(String clause) {

    }
}