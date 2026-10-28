package com.skimsearch.ingestion;

import io.javalin.Javalin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class IngestionController {

    private final GutenbergClient gutenbergClient;
    private final DatalakeWriter datalakeWriter;

    public IngestionController() {
        this.gutenbergClient = new GutenbergClient();

        String dataDir =
                System.getenv().getOrDefault(
                        "DATA_DIR",
                        "data"
                );

        this.datalakeWriter =
                new DatalakeWriter(
                        Path.of(dataDir)
                );
    }

    public void registerRoutes(Javalin app) {

        app.get("/status", ctx -> {

            Map<String, Object> response =
                    new HashMap<>();

            response.put(
                    "service",
                    "ingestion"
            );

            response.put(
                    "status",
                    "running"
            );

            ctx.json(response);
        });

        app.post("/ingest/{book_id}", ctx -> {

            String bookIdParam =
                    ctx.pathParam("book_id");

            int bookId;

            try {
                bookId =
                        Integer.parseInt(
                                bookIdParam
                        );
            } catch (NumberFormatException e) {

                ctx.status(400);

                ctx.json(
                        Map.of(
                                "error",
                                "Invalid book id"
                        )
                );

                return;
            }

            if (bookId <= 0) {

                ctx.status(400);

                ctx.json(
                        Map.of(
                                "error",
                                "Book id must be positive"
                        )
                );

                return;
            }

            if (datalakeWriter.isReady(bookId)) {

                ctx.status(200);

                ctx.json(
                        Map.of(
                                "book_id",
                                bookId,
                                "status",
                                "READY",
                                "message",
                                "Book already ingested"
                        )
                );

                return;
            }

            try {

                GutenbergClient.BookContent book =
                        gutenbergClient.downloadBook(
                                bookId
                        );

                datalakeWriter.writeBook(
                        bookId,
                        book.header(),
                        book.body()
                );

                ctx.status(200);

                ctx.json(
                        Map.of(
                                "book_id",
                                bookId,
                                "status",
                                "READY"
                        )
                );

            } catch (Exception e) {

                ctx.status(500);

                ctx.json(
                        Map.of(
                                "book_id",
                                bookId,
                                "status",
                                "FAILED",
                                "error",
                                e.getMessage()
                        )
                );
            }
        });

        app.get("/ingest/status/{book_id}", ctx -> {

            String bookIdParam =
                    ctx.pathParam("book_id");

            int bookId;

            try {
                bookId =
                        Integer.parseInt(
                                bookIdParam
                        );
            } catch (NumberFormatException e) {

                ctx.status(400);

                ctx.json(
                        Map.of(
                                "error",
                                "Invalid book id"
                        )
                );

                return;
            }

            if (bookId <= 0) {

                ctx.status(400);

                ctx.json(
                        Map.of(
                                "error",
                                "Book id must be positive"
                        )
                );

                return;
            }

            String status =
                    datalakeWriter.getStatus(bookId);

            ctx.status(200);

            ctx.json(
                    Map.of(
                            "book_id",
                            bookId,
                            "status",
                            status
                    )
            );
        });
        app.get("/ingest/list", ctx -> {

    try {

        ctx.status(200);

        ctx.json(
                Map.of(
                        "books",
                        datalakeWriter.listReadyBooks()
                )
        );

    } catch (Exception e) {

        ctx.status(500);

        ctx.json(
                Map.of(
                        "error",
                        e.getMessage()
                )
        );
    }
});
    }
    
}