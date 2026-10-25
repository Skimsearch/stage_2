package com.skimsearch.search;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.json.JsonMapper;

import java.lang.reflect.Type;
import java.util.Map;

public class App {

    public static void main(String[] args) {

        SearchConfig searchConfig = SearchConfig.fromEnvironment();
        SearchDatabase database = new SearchDatabase(searchConfig);
        BookRepository bookRepository = new BookRepository(database);
        SearchRepository searchRepository = new SearchRepository(database);

        Gson gson = new GsonBuilder().create();

        JsonMapper gsonMapper = new JsonMapper() {

            @Override
            public String toJsonString(Object obj, Type type) {
                return gson.toJson(obj, type);
            }

            @Override
            public <T> T fromJsonString(String json, Type targetType) {
                return gson.fromJson(json, targetType);
            }
        };

        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.http.defaultContentType = "application/json";
            javalinConfig.jsonMapper(gsonMapper);
        });

        app.get("/status", ctx -> {
            ctx.json(Map.of(
                    "service", "search-service",
                    "status", "running",
                    "database", database.isAvailable() ? "available" : "unavailable"
            ));
        });

        app.get("/books/{book_id}", ctx -> {

        String rawBookId = ctx.pathParam("book_id");

        int bookId;

        try {
            bookId = Integer.parseInt(rawBookId);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of(
                    "error", "book_id must be an integer"
            ));
            return;
        }

        if (bookId <= 0) {
            ctx.status(400).json(Map.of(
                    "error", "book_id must be greater than zero"
            ));
            return;
        }

        try {
            var book = bookRepository.findById(bookId);

            if (book.isEmpty()) {
                ctx.status(404).json(Map.of(
                        "error", "book not found",
                        "book_id", bookId
                ));
                return;
            }

            ctx.json(book.get());

        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "error", "database error"
            ));
        }
    });

        app.get("/search", ctx -> {

        String query = ctx.queryParam("q");

        if (query == null || query.isBlank()) {
            ctx.status(400).json(Map.of(
                    "error", "q parameter is required"
            ));
            return;
        }

        var tokens = SearchTokenizer.tokenize(query);

        if (tokens.isEmpty()) {
            ctx.status(400).json(Map.of(
                    "error", "q must contain a valid search term"
            ));
            return;
        }

        if (tokens.size() > 1) {
            ctx.status(400).json(Map.of(
                    "error", "only one search term is currently supported"
            ));
            return;
        }

        String term = tokens.get(0);

        try {
            var results = searchRepository.searchByTerm(term);

            ctx.json(Map.of(
                    "query", term,
                    "total", results.size(),
                    "results", results
            ));

        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "error", "database error"
            ));
        }
    });

        app.start(searchConfig.port());
    }
}