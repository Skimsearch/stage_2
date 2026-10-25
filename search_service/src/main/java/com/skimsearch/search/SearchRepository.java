package com.skimsearch.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SearchRepository {

    private final SearchDatabase database;

    public SearchRepository(SearchDatabase database) {
        this.database = database;
    }

    public List<SearchResult> searchByTerm(String term) throws SQLException {

        String sql = """
                SELECT
                    b.book_id,
                    b.title,
                    b.author,
                    b.language,
                    b.year,
                    p.tf
                FROM postings p
                JOIN books b ON b.book_id = p.book_id
                WHERE p.term = ?
                ORDER BY p.tf DESC, b.book_id ASC
                """;

        List<SearchResult> results = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, term);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    Integer year = result.getObject("year") == null
                            ? null
                            : result.getInt("year");

                    results.add(new SearchResult(
                            result.getInt("book_id"),
                            result.getString("title"),
                            result.getString("author"),
                            result.getString("language"),
                            year,
                            result.getInt("tf")
                    ));
                }
            }
        }

        return results;
    }
}