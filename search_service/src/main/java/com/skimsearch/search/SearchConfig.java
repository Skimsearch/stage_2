package com.skimsearch.search;

import java.nio.file.Path;

public record SearchConfig(
        int port,
        Path dataDir
) {

    public static SearchConfig fromEnvironment() {

        int port = Integer.parseInt(
                System.getenv().getOrDefault("SEARCH_PORT", "7003")
        );

        Path dataDir = Path.of(
                System.getenv().getOrDefault("DATA_DIR", "data")
        );

        return new SearchConfig(port, dataDir);
    }

    public Path indexPath() {
        return dataDir.resolve("datamart").resolve("index.db");
    }
}