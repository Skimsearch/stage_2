package com.skimsearch.indexing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Same rules as Stage 1 (tokenizer.py / IndexBuilder.java): lowercase the
 * text, then keep every match of \b[a-zA-Z]+\b.
 *
 * Words that mix ASCII letters with accented letters or digits are skipped
 * entirely (e.g. "tete-a-tete" written with accents, or "abc123"), because
 * there is no word boundary inside them. This is what made the C
 * implementation produce 21 extra terms in Stage 1, so Search must tokenize
 * queries with exactly this class (or an identical copy).
 */
public final class Tokenizer {

private static final Pattern TOKEN_PATTERN =
        Pattern.compile("\\b[a-zA-Z]+\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private Tokenizer() {}

    /** All tokens in order, with repetitions. */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /** Distinct tokens, which is what a posting list needs (one entry per book). */
    public static Set<String> uniqueTokens(String text) {
        return new LinkedHashSet<>(tokenize(text));
    }
}