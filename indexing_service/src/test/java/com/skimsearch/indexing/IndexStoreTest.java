package com.skimsearch.indexing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class IndexStoreTest {

    @TempDir
    Path tmp;

    IndexStore store;

    @BeforeEach
    void setUp() {
        store = new IndexStore(tmp.resolve("datamart").resolve("index.db"));
    }

    private static BookMetadata meta(int id, String title) {
        return new BookMetadata(id, title, "Author", "en", 1998);
    }

    @Test
    void indexingTwiceIsIdempotent() {
        Map<String, Integer> tf = Map.of("pride", 3, "monster", 1);
        IndexStore.IndexResult first = store.indexBook(meta(1, "A"), tf);
        IndexStore.IndexResult second = store.indexBook(meta(1, "A"), tf);

        assertFalse(first.replaced());
        assertTrue(second.replaced());
        assertEquals(1, store.stats().books());
        assertEquals(2, store.stats().postings());
        assertEquals(3, store.termFrequency("pride", 1));
    }

    @Test
    void reindexReplacesOldTermsAndMetadata() {
        store.indexBook(meta(1, "Old"), Map.of("old", 2, "shared", 1));
        store.indexBook(meta(1, "New"), Map.of("shared", 5, "fresh", 1));

        assertEquals(0, store.termFrequency("old", 1));
        assertEquals(5, store.termFrequency("shared", 1));
        assertEquals(1, store.termFrequency("fresh", 1));
        assertEquals("New", store.title(1));
        assertEquals(2, store.postingCount(1));
    }

    @Test
    void failedIndexingKeepsPreviousState() {
        store.indexBook(meta(1, "Good"), Map.of("keep", 4));

        Map<String, Integer> broken = new HashMap<>();
        broken.put("fine", 1);
        broken.put(null, 1); // violates NOT NULL on postings.term
        assertThrows(RuntimeException.class, () -> store.indexBook(meta(1, "Broken"), broken));

        assertEquals("Good", store.title(1));
        assertEquals(4, store.termFrequency("keep", 1));
        assertEquals(0, store.termFrequency("fine", 1));
        assertEquals(1, store.postingCount(1));
    }

    @Test
    void concurrentIndexingOfDifferentBooksKeepsAll() throws Exception {
        int books = 8;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= books; i++) {
            final int id = i;
            Callable<Object> task = () -> store.indexBook(meta(id, "B" + id), Map.of("common", id, "t" + id, 1));
            futures.add(pool.submit(task));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(books, store.stats().books());
        assertEquals(books * 2L, store.stats().postings());
    }

    @Test
    void concurrentReindexOfSameBookDoesNotDuplicate() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> store.indexBook(meta(7, "Same"), Map.of("a", 1, "b", 2))));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, store.stats().books());
        assertEquals(2, store.stats().postings());
    }

    @Test
    void rebuildReplacesEverythingAtomically() {
        store.indexBook(meta(1, "Stale"), Map.of("stale", 1));

        IndexStore.RebuildResult result = store.rebuild(List.of(
                new IndexStore.BookIndexData(meta(2, "Two"), Map.of("two", 1)),
                new IndexStore.BookIndexData(meta(3, "Three"), Map.of("three", 1))));

        assertEquals(2, result.booksIndexed());
        assertEquals(2, store.stats().books());
        assertEquals(0, store.postingCount(1));
    }

    @Test
    void failedRebuildKeepsPreviousIndex() {
        store.indexBook(meta(1, "Keep"), Map.of("keep", 1));
        Map<String, Integer> broken = new HashMap<>();
        broken.put(null, 1);

        assertThrows(RuntimeException.class, () -> store.rebuild(List.of(
                new IndexStore.BookIndexData(meta(2, "Bad"), broken))));

        assertEquals(1, store.stats().books());
        assertEquals("Keep", store.title(1));
    }
}
