package com.skimsearch.ingestion;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.json.JavalinGson;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class App {

    public static void main(String[] args) {

        int port = Integer.parseInt(
                System.getenv().getOrDefault(
                        "INGESTION_PORT",
                        "7001"
                )
        );

        int threads = Integer.parseInt(
                System.getenv().getOrDefault(
                        "INGESTION_THREADS",
                        "250"
                )
        );

        if (threads < 8 || threads > 1000) {
            throw new IllegalArgumentException(
                    "INGESTION_THREADS must be between 8 and 1000"
            );
        }

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(
                    new JavalinGson(new Gson(), false)
            );
            config.jetty.threadPool =
                    new QueuedThreadPool(threads);
        }).start(port);

        IngestionController controller =
                new IngestionController();

        controller.registerRoutes(app);

        System.out.println(
                "Ingestion service running on port "
                        + port
                        + " with "
                        + threads
                        + " threads"
        );
    }
}