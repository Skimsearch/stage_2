package com.skimsearch.indexing;

/**
 * Metadata extracted from a Gutenberg header.
 *
 * year is the year of the eBook release on Project Gutenberg, not the year the
 * book was originally published (the header does not contain the latter).
 * Any field can be null when the header does not provide it.
 */
public record BookMetadata(
        int bookId,
        String title,
        String author,
        String language,
        Integer year
) {}