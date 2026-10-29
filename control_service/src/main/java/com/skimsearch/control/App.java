package com.skimsearch.control;

import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("=== Starting Search Engine Orchestrator ===");

        String ingestionUrl = "http://localhost:7001";
        String indexingUrl = "http://localhost:7002";
        String stateFile = "data/control/state.json";

        StateStore stateStore = new StateStore(stateFile);
        ServiceClients clients = new ServiceClients(ingestionUrl, indexingUrl);
        Workflow workflow = new Workflow(stateStore, clients);

        // Libros de prueba para orquestar
        List<Integer> books = List.of(1342, 11, 84);

        for (int id : books) {
            System.out.println("\n--- Processing Book: " + id + " ---");
            workflow.processBook(id);
        }

        System.out.println("\n=== Final State Summary ===");
        stateStore.getAllStates().forEach((id, state) -> 
            System.out.println("Book " + id + " -> " + state)
        );
    }
}