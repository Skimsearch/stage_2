package com.skimsearch.indexing;

import io.javalin.Javalin;

import java.nio.file.Path;

/**
 * Indexing Service entry point.
 *
 * Environment variables:
 *   INDEXING_PORT  HTTP port (default 7002)
 *   DATA_DIR       root data folder (default "data"); the datalake is read from
 *                  DATA_DIR/datalake and the index is written to DATA_DIR/datamart/index.db
 */
public final class App {

    private App() {}

    public static void main(String[] args) {
        Config config = Config.fromEnv();
        IndexStore store = new IndexStore(config.databaseFile());
        IndexingService service = new IndexingService(new DatalakeReader(config.datalakeDir()), store);

        Javalin app = Javalin.create();
        new IndexingController(service).register(app);
        app.start(config.port());
    }

    /** Runtime configuration, read from environment variables. */
    record Config(int port, Path dataDir) {

        static Config fromEnv() {
            int port = Integer.parseInt(env("INDEXING_PORT", "7002"));
            return new Config(port, Path.of(env("DATA_DIR", "data")));
        }

        Path datalakeDir() {
            return dataDir.resolve("datalake");
        }

        Path databaseFile() {
            return dataDir.resolve("datamart").resolve("index.db");
        }

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
