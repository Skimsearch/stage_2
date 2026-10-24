package com.skimsearch.indexing;

import com.skimsearch.indexing.IndexingService.BookNotFoundException;
import com.skimsearch.indexing.IndexingService.BookNotReadyException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads books from the datalake written by the Ingestion Service:
 * {root}/batch/{start}-{end}/{id}.header.txt and {id}.body.txt
 * with start = (id / 1000) * 1000 and end = start + 999.
 *
 * Ingestion writes through a temp file and renames atomically, so a file
 * that exists is a complete file. A book is COMPLETE only with both files.
 */
public final class DatalakeReader {

    public enum State { NOT_FOUND, INCOMPLETE, COMPLETE }

    public record RawBook(int bookId, String header, String body) {}

    private final Path root;

    public DatalakeReader(Path root) {
        this.root = root;
    }

    Path headerPath(int bookId) {
        return folder(bookId).resolve(bookId + ".header.txt");
    }

    Path bodyPath(int bookId) {
        return folder(bookId).resolve(bookId + ".body.txt");
    }

    private Path folder(int bookId) {
        int start = (bookId / 1000) * 1000;
        return root.resolve("batch").resolve(start + "-" + (start + 999));
    }

    public State state(int bookId) {
        boolean header = Files.isRegularFile(headerPath(bookId));
        boolean body = Files.isRegularFile(bodyPath(bookId));
        if (header && body) {
            return State.COMPLETE;
        }
        return header || body ? State.INCOMPLETE : State.NOT_FOUND;
    }

    public RawBook read(int bookId) {
        switch (state(bookId)) {
            case NOT_FOUND -> throw new BookNotFoundException(bookId);
            case INCOMPLETE -> throw new BookNotReadyException(bookId);
            default -> { }
        }
        try {
            String header = Files.readString(headerPath(bookId), StandardCharsets.UTF_8);
            String body = Files.readString(bodyPath(bookId), StandardCharsets.UTF_8);
            return new RawBook(bookId, header, body);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read book " + bookId, e);
        }
    }

    /** Ids of every COMPLETE book in the datalake, ascending. */
    public List<Integer> completeBookIds() {
        Path batchRoot = root.resolve("batch");
        List<Integer> ids = new ArrayList<>();
        if (!Files.isDirectory(batchRoot)) {
            return ids;
        }
        try (Stream<Path> files = Files.walk(batchRoot, 2)) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".header.txt"))
                    .forEach(n -> {
                        try {
                            int id = Integer.parseInt(n.substring(0, n.indexOf('.')));
                            if (state(id) == State.COMPLETE) {
                                ids.add(id);
                            }
                        } catch (NumberFormatException ignored) {
                            // not one of ours
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan the datalake", e);
        }
        ids.sort(Integer::compare);
        return ids;
    }
}
