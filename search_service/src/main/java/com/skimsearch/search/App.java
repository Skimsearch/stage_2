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

            String author = ctx.queryParam("author");
            String language = ctx.queryParam("language");

            Integer year = null;
            String rawYear = ctx.queryParam("year");

            if (rawYear != null) {
                try {
                    year = Integer.parseInt(rawYear);
                } catch (NumberFormatException e) {
                    ctx.status(400).json(Map.of(
                            "error", "year must be an integer"
                    ));
                    return;
                }
            }

            int offset = 0;
            String rawOffset = ctx.queryParam("offset");

            if (rawOffset != null) {
                try {
                    offset = Integer.parseInt(rawOffset);
                } catch (NumberFormatException e) {
                    ctx.status(400).json(Map.of(
                            "error", "offset must be an integer"
                    ));
                    return;
                }

                if (offset < 0) {
                    ctx.status(400).json(Map.of(
                            "error", "offset must be greater than or equal to zero"
                    ));
                    return;
                }
            }

            int limit = 20;
            String rawLimit = ctx.queryParam("limit");

            if (rawLimit != null) {
                try {
                    limit = Integer.parseInt(rawLimit);
                } catch (NumberFormatException e) {
                    ctx.status(400).json(Map.of(
                            "error", "limit must be an integer"
                    ));
                    return;
                }

                if (limit < 1 || limit > 100) {
                    ctx.status(400).json(Map.of(
                            "error", "limit must be between 1 and 100"
                    ));
                    return;
                }
            }

            try {

                int total = searchRepository.count(
                        term,
                        author,
                        language,
                        year
                );

                var results = searchRepository.search(
                        term,
                        author,
                        language,
                        year,
                        offset,
                        limit
                );

                Map<String, Object> filters = new java.util.LinkedHashMap<>();

                if (author != null) {
                    filters.put("author", author);
                }

                if (language != null) {
                    filters.put("language", language);
                }

                if (year != null) {
                    filters.put("year", year);
                }

                Map<String, Object> response = new java.util.LinkedHashMap<>();

                response.put("query", term);
                response.put("filters", filters);
                response.put("total", total);
                response.put("offset", offset);
                response.put("limit", limit);
                response.put("results", results);

                ctx.json(response);

            } catch (Exception e) {
                ctx.status(500).json(Map.of(
                        "error", "database error"
                ));
            }
        });

        app.start(searchConfig.port());
    }
}