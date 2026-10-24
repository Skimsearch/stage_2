package com.skimsearch.indexing;

import com.skimsearch.indexing.IndexingService.BookNotFoundException;
import com.skimsearch.indexing.IndexingService.BookNotReadyException;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP layer. Errors: {"error": code, "message": text}. */
public final class IndexingController {

    private static final Gson GSON = new Gson();

    private final IndexingService service;

    public IndexingController(IndexingService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/status", ctx -> json(ctx, 200, Map.of("service", "indexing-service", "status", "running")));
        app.get("/index/status", this::indexStatus);
        app.post("/index/update/{book_id}", this::update);
        app.post("/index/rebuild", this::rebuild);

        app.exception(BookNotFoundException.class, (e, ctx) -> error(ctx, 404, "not_found", e.getMessage()));
        app.exception(BookNotReadyException.class, (e, ctx) -> error(ctx, 409, "not_ready", e.getMessage()));
        app.exception(IndexStore.IndexStoreException.class,
                (e, ctx) -> error(ctx, 500, "internal_error", e.getMessage()));
    }

    private void update(Context ctx) {
        int bookId;
        try {
            bookId = Integer.parseInt(ctx.pathParam("book_id"));
        } catch (NumberFormatException e) {
            error(ctx, 400, "bad_request", "book_id must be an integer");
            return;
        }
        if (bookId <= 0) {
            error(ctx, 400, "bad_request", "book_id must be positive");
            return;
        }
        IndexStore.IndexResult result = service.update(bookId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("book_id", result.bookId());
        body.put("index", "updated");
        body.put("status", "PUBLISHED");
        body.put("unique_terms", result.uniqueTerms());
        body.put("replaced", result.replaced());
        json(ctx, 200, body);
    }

    private void rebuild(Context ctx) {
        long start = System.nanoTime();
        IndexStore.RebuildResult result = service.rebuild();
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("books_processed", result.booksIndexed());
        body.put("elapsed_time", String.format(java.util.Locale.ROOT, "%.1fs", seconds));
        json(ctx, 200, body);
    }

    private void indexStatus(Context ctx) {
        IndexStore.IndexStats stats = service.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("books_indexed", stats.books());
        body.put("unique_terms", stats.uniqueTerms());
        body.put("postings", stats.postings());
        body.put("last_update", stats.lastUpdate());
        body.put("index_size_MB", Math.round(stats.sizeBytes() / 1024.0 / 1024.0 * 10) / 10.0);
        json(ctx, 200, body);
    }

    private static void error(Context ctx, int status, String code, String message) {
        json(ctx, status, Map.of("error", code, "message", String.valueOf(message)));
    }

    private static void json(Context ctx, int status, Object body) {
        ctx.status(status).contentType("application/json").result(GSON.toJson(body));
    }
}
