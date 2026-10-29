package com.skimsearch.ingestion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatalakeWriterTest {

    @Test
    void writesHeaderAndBodyToBatchDirectory()
            throws Exception {

        Path tempDir =
                Files.createTempDirectory("ingestion-test");

        DatalakeWriter writer =
                new DatalakeWriter(tempDir);

        writer.writeBook(
                1342,
                "Title: Pride and Prejudice",
                "Example body"
        );

        Path batchDir =
                tempDir
                        .resolve("datalake")
                        .resolve("batch")
                        .resolve("1000-1999");

        assertTrue(
                Files.exists(
                        batchDir.resolve("1342.header.txt")
                )
        );

        assertTrue(
                Files.exists(
                        batchDir.resolve("1342.body.txt")
                )
        );

        assertTrue(writer.isReady(1342));
    }
}