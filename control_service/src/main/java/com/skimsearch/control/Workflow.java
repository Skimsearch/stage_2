package com.skimsearch.control;

public class Workflow {
    private final StateStore stateStore;
    private final ServiceClients serviceClients;

    public Workflow(StateStore stateStore, ServiceClients serviceClients) {
        this.stateStore = stateStore;
        this.serviceClients = serviceClients;
    }

    public boolean processBook(int bookId) {
        StateStore.State current = stateStore.getState(bookId);
        if (current == StateStore.State.INDEXED) {
            System.out.printf("Book %d already indexed.%n", bookId);
            return true;
        }

        if (current == StateStore.State.PENDING || current == StateStore.State.FAILED) {
            stateStore.updateState(bookId, StateStore.State.INGESTING);
            System.out.printf("-> Ingesting book %d...%n", bookId);
            if (!serviceClients.requestIngestion(bookId)) {
                stateStore.updateState(bookId, StateStore.State.FAILED);
                return false;
            }
            stateStore.updateState(bookId, StateStore.State.DOWNLOADED);
            current = StateStore.State.DOWNLOADED;
        }

        if (current == StateStore.State.DOWNLOADED) {
            stateStore.updateState(bookId, StateStore.State.INDEXING);
            System.out.printf("-> Indexing book %d...%n", bookId);
            if (!serviceClients.requestIndexing(bookId)) {
                stateStore.updateState(bookId, StateStore.State.FAILED);
                return false;
            }
            stateStore.updateState(bookId, StateStore.State.INDEXED);
            System.out.printf("✓ Book %d ready!%n", bookId);
            return true;
        }
        return false;
    }
}