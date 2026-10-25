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

    public List<SearchResult> search(
        String term,
        String author,
        String language,
        Integer year,
        int offset,
        int limit
    ) throws SQLException {

        StringBuilder sql = new StringBuilder("""
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
                """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(term);

        if (author != null) {
            sql.append(" AND LOWER(b.author) = LOWER(?)");
            parameters.add(author);
        }

        if (language != null) {
            sql.append(" AND LOWER(b.language) = LOWER(?)");
            parameters.add(language);
        }

        if (year != null) {
            sql.append(" AND b.year = ?");
            parameters.add(year);
        }

        sql.append(" ORDER BY p.tf DESC, b.book_id ASC");
        sql.append(" LIMIT ? OFFSET ?");

        parameters.add(limit);
        parameters.add(offset);

        List<SearchResult> results = new ArrayList<>();

        try (Connection connection = database.getConnection();
            PreparedStatement statement =
                    connection.prepareStatement(sql.toString())) {

            setParameters(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    Integer resultYear =
                            result.getObject("year") == null
                                    ? null
                                    : result.getInt("year");

                    results.add(new SearchResult(
                            result.getInt("book_id"),
                            result.getString("title"),
                            result.getString("author"),
                            result.getString("language"),
                            resultYear,
                            result.getInt("tf")
                    ));
                }
            }
        }

        return results;
    }
    public int count(
        String term,
        String author,
        String language,
        Integer year
    ) throws SQLException {

        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM postings p
                JOIN books b ON b.book_id = p.book_id
                WHERE p.term = ?
                """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(term);

        if (author != null) {
            sql.append(" AND LOWER(b.author) = LOWER(?)");
            parameters.add(author);
        }

        if (language != null) {
            sql.append(" AND LOWER(b.language) = LOWER(?)");
            parameters.add(language);
        }

        if (year != null) {
            sql.append(" AND b.year = ?");
            parameters.add(year);
        }

        try (Connection connection = database.getConnection();
            PreparedStatement statement =
                    connection.prepareStatement(sql.toString())) {

            setParameters(statement, parameters);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private void setParameters(
        PreparedStatement statement,
        List<Object> parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.size(); i++) {

            Object value = parameters.get(i);

            if (value instanceof Integer integer) {
                statement.setInt(i + 1, integer);
            } else {
                statement.setString(i + 1, value.toString());
            }
        }
    }
}