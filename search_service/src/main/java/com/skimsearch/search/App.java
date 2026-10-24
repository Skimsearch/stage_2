package com.skimsearch.search;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.json.JsonMapper;

import java.lang.reflect.Type;
import java.util.Map;

public class App {

    private static final int DEFAULT_PORT = 7003;

    public static void main(String[] args) {

        int port = Integer.parseInt(
                System.getenv().getOrDefault(
                        "SEARCH_PORT",
                        String.valueOf(DEFAULT_PORT)
                )
        );

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

        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.jsonMapper(gsonMapper);
        });

        app.get("/status", ctx -> {
            ctx.json(Map.of(
                    "service", "search-service",
                    "status", "running"
            ));
        });

        app.start(port);
    }
}