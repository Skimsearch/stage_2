
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
            System.out.printf(
                    "Book %d is already indexed.%n", bookId);
            return true;
        }

        /*
         * Phase 1: ingestion.
         *
         * INGESTING may mean that the application stopped before
         * saving the successful ingestion result. Retrying ingestion
         * lets the service confirm whether the book is ready.
         *
         * FAILED is also retried from ingestion because the persisted
         * state does not tell us which phase originally failed.
         */
        if (current == StateStore.State.PENDING
                || current == StateStore.State.FAILED
                || current == StateStore.State.INGESTING) {

            stateStore.updateState(
                    bookId, StateStore.State.INGESTING);

            System.out.printf(
                    "-> Ingesting book %d...%n", bookId);

            if (!serviceClients.requestIngestion(bookId)) {
                stateStore.updateState(
                        bookId, StateStore.State.FAILED);

                System.err.printf(
                        "Ingestion failed for book %d.%n", bookId);
                return false;
            }

            stateStore.updateState(
                    bookId, StateStore.State.DOWNLOADED);

            current = StateStore.State.DOWNLOADED;
        }

        /*
         * Phase 2: indexing.
         *
         * DOWNLOADED means ingestion succeeded.
         * INDEXING means the application may have stopped while
         * indexing, so retry indexing without repeating ingestion.
         */
        if (current == StateStore.State.DOWNLOADED
                || current == StateStore.State.INDEXING) {

            stateStore.updateState(
                    bookId, StateStore.State.INDEXING);

            System.out.printf(
                    "-> Indexing book %d...%n", bookId);

            if (!serviceClients.requestIndexing(bookId)) {
                /*
                 * Keep the successful ingestion result.
                 * The next attempt can retry indexing directly.
                 */
                stateStore.updateState(
                        bookId, StateStore.State.DOWNLOADED);

                System.err.printf(
                        "Indexing failed for book %d. "
                                + "The book remains downloaded and can be retried.%n",
                        bookId);
                return false;
            }

            stateStore.updateState(
                    bookId, StateStore.State.INDEXED);

            System.out.printf(
                    "Book %d indexed successfully!%n", bookId);
            return true;
        }

        System.err.printf(
                "Cannot process book %d from state %s.%n",
                bookId, current);

        return false;
    }
}
