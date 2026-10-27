package com.skimsearch.search;

import java.nio.file.Path;

public record SearchConfig(
        int port,
        Path dataDir,
        int threads
) {

    public static SearchConfig fromEnvironment() {

        int port = Integer.parseInt(
                System.getenv().getOrDefault("SEARCH_PORT", "7003")
        );

        Path dataDir = Path.of(
                System.getenv().getOrDefault("DATA_DIR", "data")
        );

        int threads = Integer.parseInt(
            System.getenv().getOrDefault("SEARCH_THREADS", "250")
        );

        return new SearchConfig(port, dataDir, threads);
    }

    public Path indexPath() {
        return dataDir.resolve("datamart").resolve("index.db");
    }
}