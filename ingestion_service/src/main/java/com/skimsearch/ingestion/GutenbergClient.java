package com.skimsearch.ingestion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GutenbergClient {

    private static final String START_MARKER =
            "*** START OF THE PROJECT GUTENBERG EBOOK";

    private static final String END_MARKER =
            "*** END OF THE PROJECT GUTENBERG EBOOK";

    private final HttpClient client;

    public GutenbergClient() {
        this.client = HttpClient.newHttpClient();
    }

    public BookContent downloadBook(int bookId)
            throws IOException, InterruptedException {

        String url =
                "https://www.gutenberg.org/cache/epub/"
                + bookId
                + "/pg"
                + bookId
                + ".txt";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Gutenberg returned HTTP "
                    + response.statusCode()
            );
        }

        return splitBook(response.body());
    }

    public BookContent splitBook(String text) {

        int start = text.indexOf(START_MARKER);
        int end = text.indexOf(END_MARKER);

        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalArgumentException(
                    "Gutenberg markers not found"
            );
        }

        int bodyStart =
                text.indexOf('\n', start);

        if (bodyStart == -1) {
            throw new IllegalArgumentException(
                    "Invalid Gutenberg start marker"
            );
        }

        String header =
                text.substring(0, start).trim();

        String body =
                text.substring(
                        bodyStart + 1,
                        end
                ).trim();

        return new BookContent(
                header,
                body
        );
    }

    public record BookContent(
            String header,
            String body
    ) {}
}