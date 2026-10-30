
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
 * implementando timeouts, validacion de respuestas y reintentos.
 */
public class ServiceClients {

    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;

    private final HttpClient httpClient;
    private final String ingestionUrl;
    private final String indexingUrl;

    public ServiceClients(String ingestionUrl, String indexingUrl) {
        this.ingestionUrl = normalizeUrl(ingestionUrl);
        this.indexingUrl = normalizeUrl(indexingUrl);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Solicita a Ingestion descargar y almacenar un libro.
     */
    public boolean requestIngestion(int bookId) {
        String endpoint = ingestionUrl + "/ingest/" + bookId;

        return executeWithRetry(
                endpoint,
                "Ingestion",
                json -> hasValue(json, "status", "READY")
        );
    }

    /**
     * Solicita a Indexing actualizar el indice del libro.
     */
    public boolean requestIndexing(int bookId) {
        String endpoint = indexingUrl + "/index/update/" + bookId;

        return executeWithRetry(
                endpoint,
                "Indexing",
                json -> hasValue(json, "index", "updated")
                        && hasValue(json, "status", "PUBLISHED")
        );
    }

    /**
     * Ejecuta una peticion POST con timeout y politica de reintentos.
     */
    private boolean executeWithRetry(
            String url,
            String serviceName,
            ResponseValidator validator) {

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                int statusCode = response.statusCode();

                if (statusCode >= 200 && statusCode < 300) {
                    try {
                        JsonObject json = JsonParser
                                .parseString(response.body())
                                .getAsJsonObject();

                        if (validator.isValid(json)) {
                            System.out.printf(
                                    "[%s] Request successful for %s%n",
                                    serviceName, url
                            );
                            return true;
                        }

                        System.err.printf(
                                "[%s] Response does not match the expected contract: %s%n",
                                serviceName, response.body()
                        );

                        // No se considera exitoso un JSON que no cumple
                        // el contrato esperado del microservicio.
                        return false;

                    } catch (RuntimeException e) {
                        System.err.printf(
                                "[%s] Invalid JSON response: %s%n",
                                serviceName, e.getMessage()
                        );
                        return false;
                    }
                }

                System.err.printf(
                        "[%s] Attempt %d/%d returned HTTP %d from %s%n",
                        serviceName,
                        attempt,
                        MAX_ATTEMPTS,
                        statusCode,
                        url
                );

                // Reintentar errores temporales del servidor,
                // timeouts HTTP y limites de peticiones.
                boolean retryable = statusCode >= 500
                        || statusCode == 408
                        || statusCode == 429;

                if (!retryable) {
                    return false;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                System.err.printf(
                        "[%s] Request interrupted: %s%n",
                        serviceName, url
                );
                return false;

            } catch (IOException e) {
                // Fallos de comunicacion: se puede intentar de nuevo.
                System.err.printf(
                        "[%s] Attempt %d/%d failed for %s: %s%n",
                        serviceName,
                        attempt,
                        MAX_ATTEMPTS,
                        url,
                        e.getMessage()
                );

            } catch (IllegalArgumentException e) {
                // Por ejemplo, una URL invalida.
                System.err.printf(
                        "[%s] Invalid request URL: %s%n",
                        serviceName, e.getMessage()
                );
                return false;
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    System.err.printf(
                            "[%s] Retry interrupted.%n",
                            serviceName
                    );
                    return false;
                }
            }
        }

        System.err.printf(
                "[%s] Request failed after %d attempts: %s%n",
                serviceName, MAX_ATTEMPTS, url
        );

        return false;
    }

    /**
     * Comprueba que un campo JSON existe y contiene el valor esperado.
     */
    private static boolean hasValue(
            JsonObject json,
            String field,
            String expectedValue) {

        return json.has(field)
                && json.get(field).isJsonPrimitive()
                && expectedValue.equalsIgnoreCase(
                        json.get(field).getAsString()
                );
    }

    /**
     * Elimina barras finales de la URL para evitar rutas mal construidas.
     */
    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Service URL cannot be null or empty"
            );
        }

        String normalized = url.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                    0, normalized.length() - 1
            );
        }

        return normalized;
    }

    @FunctionalInterface
    private interface ResponseValidator {
        boolean isValid(JsonObject json);
    }
}
