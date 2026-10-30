
package com.skimsearch.control;

import java.util.ArrayList;
import java.util.List;

public class App {

    private static final String DEFAULT_INGESTION_URL =
            "http://localhost:7001";

    private static final String DEFAULT_INDEXING_URL =
            "http://localhost:7002";

    private static final String DEFAULT_STATE_FILE =
            "data/control/state.json";

    private static final List<Integer> DEFAULT_BOOKS =
            List.of(1342, 11, 84);

    public static void main(String[] args) {
        System.out.println("=== Starting Search Engine Orchestrator ===");

        String ingestionUrl = getConfig(
                "INGESTION_URL", DEFAULT_INGESTION_URL);

        String indexingUrl = getConfig(
                "INDEXING_URL", DEFAULT_INDEXING_URL);

        String stateFile = getConfig(
                "CONTROL_STATE_FILE", DEFAULT_STATE_FILE);

        List<Integer> books;

        try {
            books = parseBookIds(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println(
                    "Usage: java -jar control-service-1.0.0.jar [bookId ...]");
            return;
        }

        System.out.println("Ingestion URL: " + ingestionUrl);
        System.out.println("Indexing URL: " + indexingUrl);
        System.out.println("State file: " + stateFile);
        System.out.println("Books to process: " + books);

        StateStore stateStore = new StateStore(stateFile);

        ServiceClients clients = new ServiceClients(
                ingestionUrl, indexingUrl);

        Workflow workflow = new Workflow(stateStore, clients);

        for (int id : books) {
            System.out.printf("%n--- Processing Book: %d ---%n", id);

            boolean success = workflow.processBook(id);

            if (!success) {
                System.err.printf(
                        "Book %d could not be fully processed.%n", id);
            }
        }

        System.out.println("\n=== Final State Summary ===");

        stateStore.getAllStates().forEach((id, state) ->
                System.out.printf("Book %d -> %s%n", id, state));

        System.out.println("\n=== Orchestration Finished ===");
    }

    /**
     * Reads a configuration value from an environment variable.
     * Uses the supplied default when the variable is not set or is blank.
     */
    private static String getConfig(
            String variableName, String defaultValue) {

        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }

    /**
     * Uses the default books when no arguments are provided.
     * Otherwise, treats every argument as a book ID.
     */
    private static List<Integer> parseBookIds(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_BOOKS;
        }

        List<Integer> bookIds = new ArrayList<>();

        for (String arg : args) {
            try {
                int bookId = Integer.parseInt(arg);

                if (bookId <= 0) {
                    throw new IllegalArgumentException(
                            "Book IDs must be positive integers: " + arg);
                }

                bookIds.add(bookId);

            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid book ID: " + arg
                                + ". Book IDs must be integers.");
            }
        }

        return bookIds;
    }
}
