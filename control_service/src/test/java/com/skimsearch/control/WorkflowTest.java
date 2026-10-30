package com.skimsearch.control;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTest {

    private static final int BOOK_ID = 123;

    private HttpServer server;
    private StateStore stateStore;
    private Workflow workflow;

    private final AtomicInteger ingestionRequests =
            new AtomicInteger();

    private final AtomicInteger indexingRequests =
            new AtomicInteger();

    private volatile int ingestionStatus = 200;
    private volatile int indexingStatus = 200;

    private volatile String ingestionBody =
            "{\"status\":\"READY\"}";

    private volatile String indexingBody =
            "{\"index\":\"updated\",\"status\":\"PUBLISHED\"}";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ingestionRequests.set(0);
        indexingRequests.set(0);

        server = HttpServer.create(
                new InetSocketAddress("localhost", 0), 0);

        server.createContext("/ingest/" + BOOK_ID, exchange -> {
            ingestionRequests.incrementAndGet();
            respond(exchange, ingestionStatus, ingestionBody);
        });

        server.createContext(
                "/index/update/" + BOOK_ID, exchange -> {
                    indexingRequests.incrementAndGet();
                    respond(exchange, indexingStatus, indexingBody);
                });

        server.start();

        String baseUrl = "http://localhost:"
                + server.getAddress().getPort();

        stateStore = new StateStore(
                tempDir.resolve("state.json").toString());

        ServiceClients clients =
                new ServiceClients(baseUrl, baseUrl);

        workflow = new Workflow(stateStore, clients);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body) throws IOException {

        byte[] response = body.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");

            exchange.sendResponseHeaders(status, response.length);

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        } finally {
            exchange.close();
        }
    }

    @Test
    void processesBookThroughBothServices() {
        assertTrue(workflow.processBook(BOOK_ID));

        assertEquals(
                StateStore.State.INDEXED,
                stateStore.getState(BOOK_ID));

        assertEquals(1, ingestionRequests.get());
        assertEquals(1, indexingRequests.get());
    }

    @Test
    void ingestionFailureCanBeRetried() {
        ingestionStatus = 400;

        assertFalse(workflow.processBook(BOOK_ID));

        assertEquals(
                StateStore.State.FAILED,
                stateStore.getState(BOOK_ID));

        assertEquals(0, indexingRequests.get());

        ingestionStatus = 200;

        assertTrue(workflow.processBook(BOOK_ID));

        assertEquals(
                StateStore.State.INDEXED,
                stateStore.getState(BOOK_ID));

        assertEquals(2, ingestionRequests.get());
	assertEquals(1, indexingRequests.get());
    }

    @Test
    void indexingFailureRetriesWithoutRepeatingIngestion() {
        indexingStatus = 400;

        assertFalse(workflow.processBook(BOOK_ID));

        assertEquals(
                StateStore.State.DOWNLOADED,
                stateStore.getState(BOOK_ID));

        assertEquals(1, ingestionRequests.get());
	assertEquals(1, indexingRequests.get());

        indexingStatus = 200;

        assertTrue(workflow.processBook(BOOK_ID));

        assertEquals(
                StateStore.State.INDEXED,
                stateStore.getState(BOOK_ID));

	assertEquals(1, ingestionRequests.get());
	assertEquals(2, indexingRequests.get());
    }

    @Test
    void resumesFromDownloadedStateWithoutIngestion()
            throws IOException {

        stateStore.updateState(
                BOOK_ID, StateStore.State.DOWNLOADED);

        assertTrue(workflow.processBook(BOOK_ID));

        assertEquals(
                StateStore.State.INDEXED,
                stateStore.getState(BOOK_ID));

        assertEquals(0, ingestionRequests.get());
        assertEquals(1, indexingRequests.get());
    }

    @Test
    void alreadyIndexedBookDoesNotCallServices()
            throws IOException {

        stateStore.updateState(
                BOOK_ID, StateStore.State.INDEXED);

        assertTrue(workflow.processBook(BOOK_ID));

        assertEquals(0, ingestionRequests.get());
        assertEquals(0, indexingRequests.get());
    }

    @Test
    void finalStateSurvivesReloadingStateStore()
            throws IOException {

        assertTrue(workflow.processBook(BOOK_ID));

        StateStore reloadedStore = new StateStore(
                tempDir.resolve("state.json").toString());

        assertEquals(
                StateStore.State.INDEXED,
                reloadedStore.getState(BOOK_ID));
    }
}
