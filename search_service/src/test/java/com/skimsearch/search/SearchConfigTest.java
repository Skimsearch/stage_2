package com.skimsearch.search;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchConfigTest {

    @Test
    void storesConfigurationValues() {
        SearchConfig config = new SearchConfig(
                7003,
                Path.of("data"),
                250
        );

        assertEquals(7003, config.port());
        assertEquals(Path.of("data"), config.dataDir());
        assertEquals(250, config.threads());
    }

    @Test
    void buildsCorrectIndexPath() {
        SearchConfig config = new SearchConfig(
                7003,
                Path.of("data"),
                250
        );

        assertEquals(
                Path.of("data", "datamart", "index.db"),
                config.indexPath()
        );
    }
}