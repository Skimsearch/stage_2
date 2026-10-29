package com.skimsearch.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GutenbergClientTest {

    @Test
    void downloadsAndSplitsBook() throws Exception {

        GutenbergClient client =
                new GutenbergClient();

        GutenbergClient.BookContent book =
                client.downloadBook(1342);

        assertNotNull(book.header());
        assertNotNull(book.body());

        assertFalse(book.header().isBlank());
        assertFalse(book.body().isBlank());

        assertTrue(
                book.header().contains("Title:")
        );
    }
}