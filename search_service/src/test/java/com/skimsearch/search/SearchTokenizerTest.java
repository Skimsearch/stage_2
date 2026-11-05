package com.skimsearch.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTokenizerTest {

    @Test
    void convertsQueryToLowercase() {
        assertEquals(
                List.of("pride"),
                SearchTokenizer.tokenize("PRIDE")
        );
    }

    @Test
    void extractsMultipleWords() {
        assertEquals(
                List.of("pride", "and", "prejudice"),
                SearchTokenizer.tokenize("Pride and Prejudice")
        );
    }

    @Test
    void ignoresNumbersAndPunctuation() {
        assertEquals(
                List.of("hello", "world"),
                SearchTokenizer.tokenize("123 hello, world! 456")
        );
    }

    @Test
    void returnsEmptyListWhenNoValidWordsExist() {
        assertTrue(
                SearchTokenizer.tokenize("12345 !!!").isEmpty()
        );
    }
}