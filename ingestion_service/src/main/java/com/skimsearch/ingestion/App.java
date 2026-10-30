package com.skimsearch.ingestion;

import io.javalin.Javalin;

public class App {

    public static void main(String[] args) {

        int port = Integer.parseInt(
        System.getenv().getOrDefault(
                "INGESTION_PORT",
                "7001"
        )
);

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new io.javalin.json.JavalinGson());
        }).start(port);

        IngestionController controller =
                new IngestionController();

        controller.registerRoutes(app);

        System.out.println(
                "Ingestion service running on port " + port
        );
    }
}