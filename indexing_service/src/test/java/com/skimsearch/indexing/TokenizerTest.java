package com.skimsearch.indexing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenizerTest {

    @Test
    void lowercasesAndSplitsOnPunctuation() {
        assertEquals(List.of("hello", "world", "hello"), Tokenizer.tokenize("Hello, World! HELLO"));
    }

    @Test
    void apostropheSplitsWords() {
        assertEquals(List.of("don", "t"), Tokenizer.tokenize("don't"));
    }

    @Test
    void skipsWordsMixedWithDigits() {
        assertEquals(List.of("def"), Tokenizer.tokenize("abc123 def"));
    }

    @Test
    void skipsWholeWordsWithAccentedLetters() {
        // Regression for the Stage 1 bug: the C version split these words into
        // fragments ("encyclop", "dia"), producing 21 extra terms.
        assertEquals(List.of("with", "him"), Tokenizer.tokenize("t\u00eate-\u00e0-t\u00eate with him"));
        assertTrue(Tokenizer.tokenize("encyclop\u00e6dia").isEmpty());
    }

    @Test
    void uniqueTokensRemovesRepetitions() {
        assertEquals(Set.of("the", "cat"), Tokenizer.uniqueTokens("The cat the CAT"));
    }

    @Test
    void emptyAndNullGiveNoTokens() {
        assertTrue(Tokenizer.tokenize("").isEmpty());
        assertTrue(Tokenizer.tokenize(null).isEmpty());
    }
}