package com.skimsearch.benchmark;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic synthetic data for the benchmarks (fixed seed), so every run
 * and every machine processes exactly the same input.
 */
final class SyntheticData {

    private static final String[] PUNCTUATION = {" ", " ", " ", ", ", ". ", "; ", " - ", "\n"};

    private SyntheticData() {}

    /** A vocabulary of distinct lowercase words (w0000a, w0001b ... made only of letters). */
    static String[] vocabulary(int size) {
        String[] words = new String[size];
        for (int i = 0; i < size; i++) {
            StringBuilder word = new StringBuilder("w");
            int n = i;
            do {
                word.append((char) ('a' + n % 26));
                n /= 26;
            } while (n > 0);
            words[i] = word.toString();
        }
        return words;
    }

    /** Book-like text with the given number of words, drawn from the vocabulary. */
    static String text(int wordCount, String[] vocabulary, long seed) {
        Random random = new Random(seed);
        StringBuilder text = new StringBuilder(wordCount * 8);
        for (int i = 0; i < wordCount; i++) {
            String word = vocabulary[random.nextInt(vocabulary.length)];
            // Capitalise some words: the tokenizer lowercases everything.
            text.append(random.nextInt(10) == 0 ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word);
            text.append(PUNCTUATION[random.nextInt(PUNCTUATION.length)]);
        }
        return text.toString();
    }

    /** Term frequencies of exactly {@code uniqueTerms} distinct terms, offset so books differ. */
    static Map<String, Integer> termFrequencies(int uniqueTerms, int offset) {
        Map<String, Integer> frequencies = new HashMap<>();
        String[] words = vocabulary(uniqueTerms + offset);
        for (int i = 0; i < uniqueTerms; i++) {
            frequencies.put(words[offset + i], 1 + (i % 50));
        }
        return frequencies;
    }
}
