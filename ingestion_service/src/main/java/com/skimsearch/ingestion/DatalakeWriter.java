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


public String getStatus(int bookId) {

    Path batchDir = getBatchDirectory(bookId);

    Path header =
            batchDir.resolve(bookId + ".header.txt");

    Path body =
            batchDir.resolve(bookId + ".body.txt");

    boolean headerExists =
            Files.exists(header);

    boolean bodyExists =
            Files.exists(body);

    if (headerExists && bodyExists) {
        return "READY";
    }

    if (!headerExists && !bodyExists) {
        return "NOT_FOUND";
    }

    return "INCOMPLETE";
}

public java.util.List<Integer> listReadyBooks() throws IOException {

    Path batchRoot =
            dataDir
                    .resolve("datalake")
                    .resolve("batch");

    if (!Files.exists(batchRoot)) {
        return java.util.List.of();
    }

    java.util.Set<Integer> readyBooks =
            new java.util.TreeSet<>();

    try (var batchDirectories =
                 Files.list(batchRoot)) {

        for (Path batchDir :
                batchDirectories
                        .filter(Files::isDirectory)
                        .toList()) {

            try (var files =
                         Files.list(batchDir)) {

                for (Path file : files.toList()) {

                    String fileName =
                            file.getFileName()
                                    .toString();

                    if (!fileName.endsWith(
                            ".header.txt"
                    )) {
                        continue;
                    }

                    String idText =
                            fileName.replace(
                                    ".header.txt",
                                    ""
                            );

                    try {

                        int bookId =
                                Integer.parseInt(
                                        idText
                                );

                        if (isReady(bookId)) {
                            readyBooks.add(bookId);
                        }

                    } catch (
                            NumberFormatException ignored
                    ) {
                    }
                }
            }
        }
    }

    return new java.util.ArrayList<>(
            readyBooks
    );
}
}