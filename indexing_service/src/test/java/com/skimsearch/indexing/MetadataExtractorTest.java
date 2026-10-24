package com.skimsearch.indexing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetadataExtractorTest {

    private static final String HEADER = String.join("\r\n",
            "The Project Gutenberg eBook of Pride and Prejudice",
            "",
            "Title: Pride and Prejudice",
            "Author: Jane Austen",
            "Release date: June 1, 1998 [eBook #1342]",
            "Language: English",
            "");

    @Test
    void extractsAllFields() {
        BookMetadata metadata = MetadataExtractor.extract(1342, HEADER);

        assertEquals(1342, metadata.bookId());
        assertEquals("Pride and Prejudice", metadata.title());
        assertEquals("Jane Austen", metadata.author());
        assertEquals("en", metadata.language());
        assertEquals(1998, metadata.year());
    }

    @Test
    void missingFieldsAreNull() {
        BookMetadata metadata = MetadataExtractor.extract(5, "Title: Only a title");

        assertEquals("Only a title", metadata.title());
        assertNull(metadata.author());
        assertNull(metadata.language());
        assertNull(metadata.year());
    }

    @Test
    void unknownLanguageKeepsLowercasedName() {
        BookMetadata metadata = MetadataExtractor.extract(7, "Language: Esperanto");

        assertEquals("esperanto", metadata.language());
    }

    @Test
    void nullHeaderGivesEmptyMetadata() {
        BookMetadata metadata = MetadataExtractor.extract(9, null);

        assertNull(metadata.title());
        assertNull(metadata.year());
    }
}