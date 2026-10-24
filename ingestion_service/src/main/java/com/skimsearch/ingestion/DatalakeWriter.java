package com.skimsearch.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DatalakeWriter {

    private final Path dataDir;

    public DatalakeWriter(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Path getBatchDirectory(int bookId) {
        int start = (bookId / 1000) * 1000;
        int end = start + 999;

        return dataDir
                .resolve("datalake")
                .resolve("batch")
                .resolve(start + "-" + end);
    }

    public void writeBook(
            int bookId,
            String header,
            String body
    ) throws IOException {

        Path batchDir = getBatchDirectory(bookId);
        Files.createDirectories(batchDir);

        Path finalHeader =
                batchDir.resolve(bookId + ".header.txt");

        Path finalBody =
                batchDir.resolve(bookId + ".body.txt");

        Path tempHeader =
                batchDir.resolve(bookId + ".header.tmp");

        Path tempBody =
                batchDir.resolve(bookId + ".body.tmp");

        Files.writeString(
                tempHeader,
                header,
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempBody,
                body,
                StandardCharsets.UTF_8
        );

        Files.move(
                tempHeader,
                finalHeader,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );

        Files.move(
                tempBody,
                finalBody,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    public boolean isReady(int bookId) {
        Path batchDir = getBatchDirectory(bookId);

        Path header =
                batchDir.resolve(bookId + ".header.txt");

        Path body =
                batchDir.resolve(bookId + ".body.txt");

        return Files.exists(header)
                && Files.exists(body);
    }
}