package com.skimsearch.ingestion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatalakeRecoveryTest {

    @Test
    void detectsIncompleteBook() throws Exception {

        Path tempDir =
                Files.createTempDirectory("recovery-test");

        DatalakeWriter writer =
                new DatalakeWriter(tempDir);

        Path batchDir =
                writer.getBatchDirectory(1661);

        Files.createDirectories(batchDir);

        Files.writeString(
                batchDir.resolve("1661.header.txt"),
                "broken header"
        );

        assertEquals(
                "INCOMPLETE",
                writer.getStatus(1661)
        );
    }

    @Test
    void cleansIncompleteBook() throws Exception {

        Path tempDir =
                Files.createTempDirectory("recovery-test");

        DatalakeWriter writer =
                new DatalakeWriter(tempDir);

        Path batchDir =
                writer.getBatchDirectory(1661);

        Files.createDirectories(batchDir);

        Path header =
                batchDir.resolve("1661.header.txt");

        Files.writeString(
                header,
                "broken header"
        );

        writer.cleanIncomplete(1661);

        assertFalse(
                Files.exists(header)
        );

        assertEquals(
                "NOT_FOUND",
                writer.getStatus(1661)
        );
    }

    @Test
    void becomesReadyAfterCompleteWrite()
            throws Exception {

        Path tempDir =
                Files.createTempDirectory("recovery-test");

        DatalakeWriter writer =
                new DatalakeWriter(tempDir);

        writer.writeBook(
                1661,
                "Title: The Adventures of Sherlock Holmes",
                "Example body"
        );

        assertTrue(
                writer.isReady(1661)
        );

        assertEquals(
                "READY",
                writer.getStatus(1661)
        );
    }
}