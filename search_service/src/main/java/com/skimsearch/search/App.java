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

        app.start(searchConfig.port());
    }
}