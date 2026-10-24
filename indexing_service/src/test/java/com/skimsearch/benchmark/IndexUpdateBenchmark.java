package com.skimsearch.benchmark;

import com.skimsearch.indexing.BookMetadata;
import com.skimsearch.indexing.IndexStore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Index update: one transactional (re)index of a book into SQLite.
 *
 * Every JMH thread re-indexes its OWN book, so threads never fight over the
 * same rows, only over SQLite's single write lock. Throughput therefore shows
 * how far writer threads scale on one machine; it is run with 1, 2 and 4
 * threads by BenchmarkRunner.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class IndexUpdateBenchmark {

    @State(Scope.Benchmark)
    public static class SharedStore {

        /** About the size of a real book: Stage 1 had ~16.7k distinct terms for 5 books. */
        @Param({"5000"})
        public int uniqueTerms;

        Path directory;
        IndexStore store;
        final AtomicInteger nextBookId = new AtomicInteger(1);

        @Setup(Level.Trial)
        public void open() throws IOException {
            directory = Files.createTempDirectory("indexing-bench");
            store = new IndexStore(directory.resolve("index.db"));
        }

        @TearDown(Level.Trial)
        public void close() throws IOException {
            try (Stream<Path> files = Files.walk(directory)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    @State(Scope.Thread)
    public static class ThreadBook {
        int bookId;
        BookMetadata metadata;
        Map<String, Integer> frequencies;

        @Setup(Level.Trial)
        public void prepare(SharedStore shared) {
            bookId = shared.nextBookId.getAndIncrement();
            metadata = new BookMetadata(bookId, "Benchmark book " + bookId, "Author", "en", 1998);
            frequencies = SyntheticData.termFrequencies(shared.uniqueTerms, bookId * 10);
        }
    }

    @Benchmark
    public IndexStore.IndexResult indexBook(SharedStore shared, ThreadBook book) {
        return shared.store.indexBook(book.metadata, book.frequencies);
    }
}
