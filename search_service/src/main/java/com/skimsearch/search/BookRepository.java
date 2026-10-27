package com.skimsearch.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class BookRepository {

    private final SearchDatabase database;

    public BookRepository(SearchDatabase database) {
        this.database = database;
    }

    public Optional<Book> findById(int bookId) throws SQLException {

        String sql = """
                SELECT book_id, title, author, language, year, term_count, indexed_at
                FROM books
                WHERE book_id = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, bookId);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    return Optional.empty();
                }

                Integer year = result.getObject("year") == null
                        ? null
                        : result.getInt("year");

                Book book = new Book(
                        result.getInt("book_id"),
                        result.getString("title"),
                        result.getString("author"),
                        result.getString("language"),
                        year,
                        result.getInt("term_count"),
                        result.getString("indexed_at")
                );

                return Optional.of(book);
            }
        }
    }
}