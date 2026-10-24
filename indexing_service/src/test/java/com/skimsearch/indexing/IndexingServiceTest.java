package com.skimsearch.indexing;

import com.skimsearch.indexing.IndexingService.BookNotFoundException;
import com.skimsearch.indexing.IndexingService.BookNotReadyException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexingServiceTest {

    @TempDir
    Path tmp;

    IndexingService service;
    IndexStore store;
    Path batch;

    @BeforeEach
    void setUp() throws IOException {
        Path lake = tmp.resolve("datalake");
        batch = lake.resolve("batch").resolve("1000-1999");
        Files.createDirectories(batch);
        store = new IndexStore(tmp.resolve("datamart").resolve("index.db"));
        service = new IndexingService(new DatalakeReader(lake), store);
    }

    private void writeBook(int id, String title, String body) throws IOException {
        Files.writeString(batch.resolve(id + ".header.txt"),
                "Title: " + title + "\nAuthor: Jane Austen\nLanguage: English\nRelease date: June 1, 1998 [eBook #" + id + "]\n");
        Files.writeString(batch.resolve(id + ".body.txt"), body);
    }

    @Test
    void updateIndexesCompleteBook() throws IOException {
        writeBook(1342, "Pride", "pride and prejudice pride");
        IndexStore.IndexResult result = service.update(1342);

        assertEquals(3, result.uniqueTerms());
        assertEquals(2, store.termFrequency("pride", 1342));
        assertEquals(1, service.status().books());
    }

    @Test
    void updateIsIdempotent() throws IOException {
        writeBook(1342, "Pride", "pride and prejudice");
        service.update(1342);
        IndexStore.IndexResult second = service.update(1342);

        assertTrue(second.replaced());
        assertEquals(3, service.status().postings());
    }

    @Test
    void unknownBookIsNotFound() {
        assertThrows(BookNotFoundException.class, () -> service.update(1999));
    }

    @Test
    void incompleteBookIsNotReady() throws IOException {
        Files.writeString(batch.resolve("1500.header.txt"), "Title: X\n");
        assertThrows(BookNotReadyException.class, () -> service.update(1500));
    }

    @Test
    void rebuildIndexesOnlyCompleteBooks() throws IOException {
        writeBook(1342, "Pride", "pride");
        writeBook(1343, "Other", "other words here");
        Files.writeString(batch.resolve("1500.header.txt"), "Title: X\n");

        IndexStore.RebuildResult result = service.rebuild();

        assertEquals(2, result.booksIndexed());
        assertEquals(2, service.status().books());
    }
}
