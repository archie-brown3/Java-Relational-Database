package db.engine;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.nio.file.Files;

/** TCP server that accepts SQL commands and delegates to QueryParser + QueryExecutor. */
public class DBServer {

    private static final char END_OF_TRANSMISSION = 4;
    private final String storageFolderPath;
    private final QueryExecutor queryExecutor;
    private final QueryExecutor.ExecutionContext executionContext;

    public static void main(String args[]) throws IOException {
        DBServer server = new DBServer();
        server.blockingListenOn(8888);
    }

    public DBServer() {
        storageFolderPath = Paths.get("databases").toAbsolutePath().toString();
        queryExecutor = new QueryExecutor();
        executionContext = new QueryExecutor.ExecutionContext(storageFolderPath);

        try {
            Files.createDirectories(Paths.get(storageFolderPath));
        } catch (IOException ioe) {
            System.err.println("Failed to create database storage folder: " + storageFolderPath);
        }
    }

    /**
     * Parses a SQL command string and executes it against the current database context.
     * Returns either {@code [OK]} or {@code [ERROR]} as the first line of the response.
     */
    public String handleCommand(String command) {
        try {
            QueryParser.Command parsedCommand = QueryParser.parse(command);
            return queryExecutor.execute(parsedCommand, executionContext);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return "[ERROR] " + e.getMessage();
        } catch (Exception e) {
            return "[ERROR] Unexpected server error: " + e.getMessage();
        }
    }

    // ── Networking ──────────────────────────────────────────────────

    public void blockingListenOn(int portNumber) throws IOException {
        try (ServerSocket s = new ServerSocket(portNumber)) {
            System.out.println("Server listening on port " + portNumber);
            while (!Thread.interrupted()) {
                try {
                    blockingHandleConnection(s);
                } catch (IOException e) {
                    System.err.println("Server encountered a non-fatal IO error:");
                    e.printStackTrace();
                    System.err.println("Continuing...");
                }
            }
        }
    }

    private void blockingHandleConnection(ServerSocket serverSocket) throws IOException {
        try (Socket s = serverSocket.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {

            System.out.println("Connection established: " + serverSocket.getInetAddress());
            while (!Thread.interrupted()) {
                String incomingCommand = reader.readLine();
                System.out.println("Received message: " + incomingCommand);
                String result = handleCommand(incomingCommand);
                writer.write(result);
                writer.write("\n" + END_OF_TRANSMISSION + "\n");
                writer.flush();
            }
        }
    }
}
