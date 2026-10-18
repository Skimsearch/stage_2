package com.skimsearch.indexing;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts title, author, language and release year from the text of a
 * Gutenberg header (the part before "*** START OF THE PROJECT GUTENBERG EBOOK").
 *
 * Header lines look like:
 *   Title: Pride and Prejudice
 *   Author: Jane Austen
 *   Release date: June 1, 1998 [eBook #1342]
 *   Language: English
 */
public final class MetadataExtractor {

    private static final Pattern TITLE = Pattern.compile("^Title:\\s*(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern AUTHOR = Pattern.compile("^Author:\\s*(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern LANGUAGE = Pattern.compile("^Language:\\s*(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern RELEASE = Pattern.compile("^Release date:\\s*(.+?)\\s*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR = Pattern.compile("\\b(\\d{4})\\b");

    // Gutenberg writes the language name; the API filters by language code.
    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("english", "en"),
            Map.entry("french", "fr"),
            Map.entry("german", "de"),
            Map.entry("spanish", "es"),
            Map.entry("italian", "it"),
            Map.entry("portuguese", "pt"),
            Map.entry("dutch", "nl"),
            Map.entry("finnish", "fi"),
            Map.entry("latin", "la"),
            Map.entry("swedish", "sv"),
            Map.entry("greek", "el")
    );

    private MetadataExtractor() {}

    public static BookMetadata extract(int bookId, String header) {
        String safeHeader = header == null ? "" : header.replace("\r", "");
        return new BookMetadata(
                bookId,
                firstGroup(TITLE, safeHeader),
                firstGroup(AUTHOR, safeHeader),
                toLanguageCode(firstGroup(LANGUAGE, safeHeader)),
                extractYear(firstGroup(RELEASE, safeHeader))
        );
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String toLanguageCode(String language) {
        if (language == null) {
            return null;
        }
        String key = language.trim().toLowerCase(Locale.ROOT);
        return LANGUAGE_CODES.getOrDefault(key, key);
    }

    private static Integer extractYear(String releaseDate) {
        if (releaseDate == null) {
            return null;
        }
        Matcher matcher = YEAR.matcher(releaseDate);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}