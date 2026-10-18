package com.skimsearch.indexing;

import com.google.gson.Gson;
import io.javalin.Javalin;

import java.util.Map;

public class App {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("INDEXING_PORT", "7002"));

        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
        });

        app.get("/status", ctx -> ctx.result(GSON.toJson(Map.of(
                "service", "indexing-service",
                "status", "running"
        ))));

        app.start(port);
    }
}