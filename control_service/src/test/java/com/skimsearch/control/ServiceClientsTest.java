
package com.skimsearch.control;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceClientsTest {

    private HttpServer server;
    private ServiceClients clients;

    private volatile int httpStatus = 200;
    private volatile String ingestionBody =
            "{\"status\":\"READY\"}";
    private volatile String indexingBody =
            "{\"index\":\"updated\",\"status\":\"PUBLISHED\"}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("localhost", 0), 0);

        server.createContext("/ingest/123", exchange ->
                respond(exchange, ingestionBody));

        server.createContext("/index/update/123", exchange ->
                respond(exchange, indexingBody));

        server.start();

        String baseUrl = "http://localhost:"
                + server.getAddress().getPort();

        clients = new ServiceClients(baseUrl, baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            String body) throws IOException {

        byte[] response = body.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");

            exchange.sendResponseHeaders(httpStatus, response.length);

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        } finally {
            exchange.close();
        }
    }

    @Test
    void ingestionAcceptsReadyResponse() {
        assertTrue(clients.requestIngestion(123));
    }

    @Test
    void ingestionRejectsUnexpectedStatus() {
        ingestionBody = "{\"status\":\"FAILED\"}";

        assertFalse(clients.requestIngestion(123));
    }

    @Test
    void indexingAcceptsPublishedResponse() {
        assertTrue(clients.requestIndexing(123));
    }

    @Test
    void indexingRejectsUnexpectedStatus() {
        indexingBody =
                "{\"index\":\"updated\",\"status\":\"PROCESSING\"}";

        assertFalse(clients.requestIndexing(123));
    }

    @Test
    void indexingRejectsUnexpectedIndexValue() {
        indexingBody =
                "{\"index\":\"failed\",\"status\":\"PUBLISHED\"}";

        assertFalse(clients.requestIndexing(123));
    }

    @Test
    void rejectsServerError() {
        httpStatus = 500;

        assertFalse(clients.requestIngestion(123));
    }

    @Test
    void rejectsMalformedJson() {
        ingestionBody = "this is not JSON";

        assertFalse(clients.requestIngestion(123));
    }
}
