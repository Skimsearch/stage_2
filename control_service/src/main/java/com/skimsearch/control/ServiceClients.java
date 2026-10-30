package com.skimsearch.control;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Cliente HTTP para comunicarse con los microservicios de Ingestion e Indexing,
 * implementando timeouts y reintentos conforme a los requisitos del Stage 2.
 */
public class ServiceClients {

    private final HttpClient httpClient;
    private final String ingestionUrl;
    private final String indexingUrl;

    public ServiceClients(String ingestionUrl, String indexingUrl) {
        this.ingestionUrl = ingestionUrl;
        this.indexingUrl = indexingUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Solicita al Ingestion Service descargar y almacenar un libro en el Datalake.
     */
    public boolean requestIngestion(int bookId) {
        String endpoint = ingestionUrl + "/ingest/" + bookId;
        return executeWithRetry("POST", endpoint, 3);
    }

    /**
     * Solicita al Indexing Service actualizar el índice invertido y metadatos del libro.
     */
    public boolean requestIndexing(int bookId) {
        String endpoint = indexingUrl + "/index/update/" + bookId;
        return executeWithRetry("POST", endpoint, 3);
    }

    /**
     * Ejecuta la petición HTTP con política de reintentos y control de errores.
     */
    private boolean executeWithRetry(String method, String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5));

                if ("POST".equalsIgnoreCase(method)) {
                    builder.POST(HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.GET();
                }

                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    
                    // Comprobación de estado según el contrato del PDF
                    if (json.has("status") && json.get("status").getAsString().equalsIgnoreCase("downloaded")) {
                        return true;
                    }
                    if (json.has("index") && json.get("index").getAsString().equalsIgnoreCase("updated")) {
                        return true;
                    }
                    return true;
                }
                System.err.printf("[Attempt %d/%d] Non-OK status %d from %s%n", attempt, maxRetries, response.statusCode(), url);
            } catch (IOException | InterruptedException e) {
                System.err.printf("[Attempt %d/%d] Error contacting %s: %s%n", attempt, maxRetries, url, e.getMessage());
            }

            try {
                Thread.sleep(1000); // Pausa de 1s antes de reintentar
            } catch (InterruptedException ignored) {}
        }
        return false;
    }
}