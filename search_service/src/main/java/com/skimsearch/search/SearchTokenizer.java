package com.skimsearch.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchTokenizer {

    private static final Pattern TOKEN =
            Pattern.compile("\\b[a-zA-Z]+\\b",
                    Pattern.UNICODE_CHARACTER_CLASS);

    private SearchTokenizer() {
    }

    public static List<String> tokenize(String text) {

        List<String> tokens = new ArrayList<>();

        Matcher matcher = TOKEN.matcher(
                text.toLowerCase(Locale.ROOT)
        );

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }
}