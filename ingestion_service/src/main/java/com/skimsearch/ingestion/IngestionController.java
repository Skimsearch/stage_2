package com.skimsearch.ingestion;

import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public class IngestionController {

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
    }
}
