package com.skimsearch.search;

public record Book(
        int bookId,
        String title,
        String author,
        String language,
        Integer year,
        int termCount,
        String indexedAt
) {
}