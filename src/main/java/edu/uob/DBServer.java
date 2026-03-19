package edu.uob;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Objects;

/** This class implements the DB server. */
public class DBServer {

    private static final char END_OF_TRANSMISSION = 4;
    private String storageFolderPath;

    public static void main(String args[]) throws IOException {
        DBServer server = new DBServer();
        server.blockingListenOn(8888);
    }

    /**
    * KEEP this signature otherwise we won't be able to mark your submission correctly.
    */
    public DBServer() {
        // all data stored inside of databases directory
        storageFolderPath = Paths.get("databases").toAbsolutePath().toString();
        edu.uob.CommandHandler commandHandler = new CommandHandler();
        try {
            // Create the database storage folder if it doesn't already exist !
            Files.createDirectories(Paths.get(storageFolderPath));
        } catch(IOException ioe) {
            System.out.println("Can't seem to create database storage folder " + storageFolderPath);
        }
    }

    /**
    * KEEP this signature (i.e. {@code edu.uob.DBServer.handleCommand(String)}) otherwise we won't be
    * able to mark your submission correctly.
    *
    * <p>This method handles all incoming DB commands and carries out the required actions.
    */
    public String handleCommand(String command) {
        // TODO implement your server logic here
        edu.uob.CommandHandler commandHandler = new CommandHandler();
        String pathToFile = "src/test/java/edu/uob";
        String name = pathToFile + File.separator + "sheds.tab";
        String destination = "databases";
        File fileToOpen = new File(name);

        // Parse command for Create, Insert, Select etc..

        // Read from File
        if (Objects.equals(command, "read")) {
            try {
                commandHandler.handleRead(fileToOpen);
            } catch (FileNotFoundException e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

        // Write from file
        if (Objects.equals(command, "write")){
            try {
                commandHandler.handleWrite(fileToOpen);
            } catch (FileNotFoundException e){
                System.out.println((e.getMessage()));
            } catch (IOException e){
                System.out.println(e.getMessage());
            }
        }

        if (Objects.equals(command, "save")){
            try{
                //TODO: seperate read and save methods in commandhandler
                commandHandler.readAndSaveTable(fileToOpen, destination);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return "";
    }

    //  === Methods below handle networking aspects of the project - you will not need to change these ! ===

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
