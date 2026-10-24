package com.skimsearch.indexing;

import io.javalin.Javalin;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.nio.file.Path;

/**
 * Indexing Service entry point.
 *
 * Environment variables:
 *   INDEXING_PORT     HTTP port (default 7002). Start several processes on
 *                     different ports to run replicas; all replicas must use the
 *                     same DATA_DIR, because SQLite serialises their writes.
 *   DATA_DIR          root data folder (default "data"); the datalake is read
 *                     from DATA_DIR/datalake and the index is written to
 *                     DATA_DIR/datamart/index.db
 *   INDEXING_THREADS  maximum number of Jetty threads of this process, 8-1000
 *                     (default 250, which is Javalin's own default). Jetty uses
 *                     some of them internally, the rest handle requests.
 */
public final class App {

    private static final int IDLE_TIMEOUT_MS = 60_000;
    private static final int MIN_THREADS = 8;

    private App() {}

    public static void main(String[] args) {
        Config config = Config.fromEnv();
        IndexStore store = new IndexStore(config.databaseFile());
        IndexingService service = new IndexingService(new DatalakeReader(config.datalakeDir()), store);

        Javalin app = Javalin.create(javalinConfig ->
                javalinConfig.jetty.threadPool =
                        new QueuedThreadPool(config.maxThreads(), MIN_THREADS, IDLE_TIMEOUT_MS));
        new IndexingController(service).register(app);

        System.out.printf("Indexing service: port=%d, maxThreads=%d, dataDir=%s%n",
                config.port(), config.maxThreads(), config.dataDir().toAbsolutePath());
        app.start(config.port());
    }

    /** Runtime configuration, read from environment variables. */
    record Config(int port, Path dataDir, int maxThreads) {

        static Config fromEnv() {
            int port = Integer.parseInt(env("INDEXING_PORT", "7002"));
            int threads = Integer.parseInt(env("INDEXING_THREADS", "250"));
            if (threads < MIN_THREADS || threads > 1000) {
                throw new IllegalArgumentException(
                        "INDEXING_THREADS must be between " + MIN_THREADS + " and 1000, got " + threads);
            }
            return new Config(port, Path.of(env("DATA_DIR", "data")), threads);
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
