package com.skimsearch.indexing;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

/**
 * The shared datamart: one SQLite file (index.db) holding both the metadata
 * and the inverted index, so they can be updated together in ONE transaction.
 *
 * Consistency rules (Stage 2 requirements):
 *  - A book is "PUBLISHED" exactly when the transaction that wrote its
 *    metadata and postings has committed. Until then, Search (which reads
 *    the same file) keeps seeing the previous version, never a half-updated
 *    one. WAL mode lets readers keep reading while this class writes.
 *  - Re-indexing a book deletes its old postings and inserts the new ones in
 *    that same transaction, so it never creates duplicates (idempotent).
 *  - Writers take the write lock up front (BEGIN IMMEDIATE) and wait up to
 *    BUSY_TIMEOUT_MS for each other, so concurrent updates (threads or
 *    several Indexing replicas on the same machine) are serialised instead
 *    of failing or interleaving.
 *
 * Schema:
 *   books(book_id, title, author, language, year, term_count, indexed_at)
 *   postings(term, book_id, tf)      -- tf = occurrences of term in the book
 */
public final class IndexStore {

    static final int BUSY_TIMEOUT_MS = 10_000;
    private static final int BATCH_SIZE = 5_000;

    private final Path databaseFile;
    private final String url;
    private final Properties properties;

    public IndexStore(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create datamart directory for " + databaseFile, e);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        this.databaseFile = databaseFile.toAbsolutePath();
        this.url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        this.properties = config.toProperties();

        initSchema();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    private void initSchema() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS books (
                        book_id    INTEGER PRIMARY KEY,
                        title      TEXT,
                        author     TEXT,
                        language   TEXT,
                        year       INTEGER,
                        term_count INTEGER NOT NULL,
                        indexed_at TEXT    NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS postings (
                        term    TEXT    NOT NULL,
                        book_id INTEGER NOT NULL,
                        tf      INTEGER NOT NULL,
                        PRIMARY KEY (term, book_id)
                    ) WITHOUT ROWID""");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_postings_book ON postings(book_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_books_author ON books(author)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_books_language ON books(language)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_books_year ON books(year)");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialise the index schema", e);
        }
    }

    /**
     * Indexes (or re-indexes) one book atomically.
     *
     * @return whether the book already had a published version (a re-index)
     */
    public IndexResult indexBook(BookMetadata metadata, Map<String, Integer> termFrequencies) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                boolean replaced = replaceBook(connection, metadata, termFrequencies);
                connection.commit();
                return new IndexResult(metadata.bookId(), termFrequencies.size(), replaced);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IndexStoreException("Could not index book " + metadata.bookId(), e);
        }
    }

    /**
     * Rebuilds the whole index in a single transaction. Search keeps seeing
     * the previous complete index until the commit, then the new one.
     */
    public RebuildResult rebuild(Iterable<BookIndexData> books) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM postings");
                    statement.executeUpdate("DELETE FROM books");
                }
                int count = 0;
                for (BookIndexData book : books) {
                    replaceBook(connection, book.metadata(), book.termFrequencies());
                    count++;
                }
                connection.commit();
                return new RebuildResult(count);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IndexStoreException("Could not rebuild the index", e);
        }
    }

    private boolean replaceBook(Connection connection, BookMetadata metadata,
                                Map<String, Integer> termFrequencies) throws SQLException {
        boolean existed = bookExists(connection, metadata.bookId());

        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM postings WHERE book_id = ?")) {
            delete.setInt(1, metadata.bookId());
            delete.executeUpdate();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO postings(term, book_id, tf) VALUES (?, ?, ?)")) {
            int pending = 0;
            for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
                insert.setString(1, entry.getKey());
                insert.setInt(2, metadata.bookId());
                insert.setInt(3, entry.getValue());
                insert.addBatch();
                if (++pending == BATCH_SIZE) {
                    insert.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                insert.executeBatch();
            }
        }

        try (PreparedStatement upsert = connection.prepareStatement("""
                INSERT INTO books(book_id, title, author, language, year, term_count, indexed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(book_id) DO UPDATE SET
                    title = excluded.title,
                    author = excluded.author,
                    language = excluded.language,
                    year = excluded.year,
                    term_count = excluded.term_count,
                    indexed_at = excluded.indexed_at""")) {
            upsert.setInt(1, metadata.bookId());
            upsert.setString(2, metadata.title());
            upsert.setString(3, metadata.author());
            upsert.setString(4, metadata.language());
            if (metadata.year() == null) {
                upsert.setNull(5, java.sql.Types.INTEGER);
            } else {
                upsert.setInt(5, metadata.year());
            }
            upsert.setInt(6, termFrequencies.size());
            upsert.setString(7, Instant.now().toString());
            upsert.executeUpdate();
        }

        return existed;
    }

    private boolean bookExists(Connection connection, int bookId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM books WHERE book_id = ?")) {
            select.setInt(1, bookId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    public IndexStats stats() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            long books = scalarLong(statement, "SELECT COUNT(*) FROM books");
            long terms = scalarLong(statement, "SELECT COUNT(DISTINCT term) FROM postings");
            long postings = scalarLong(statement, "SELECT COUNT(*) FROM postings");
            String lastUpdate = null;
            try (ResultSet rows = statement.executeQuery("SELECT MAX(indexed_at) FROM books")) {
                if (rows.next()) {
                    lastUpdate = rows.getString(1);
                }
            }
            return new IndexStats(books, terms, postings, lastUpdate, sizeBytes());
        } catch (SQLException e) {
            throw new IndexStoreException("Could not read index statistics", e);
        }
    }

    /** Size on disk of index.db plus its WAL file. */
    public long sizeBytes() {
        long total = 0;
        for (String suffix : new String[] {"", "-wal"}) {
            Path file = Path.of(databaseFile + suffix);
            try {
                if (Files.exists(file)) {
                    total += Files.size(file);
                }
            } catch (IOException ignored) {
                // size is informational only
            }
        }
        return total;
    }

    /** Number of postings stored for one book (0 if it is not indexed). */
    public int postingCount(int bookId) {
        try (Connection connection = open();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT COUNT(*) FROM postings WHERE book_id = ?")) {
            select.setInt(1, bookId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IndexStoreException("Could not count postings for book " + bookId, e);
        }
    }

    /** Stored term frequency of a term in a book, or 0 if absent. */
    public int termFrequency(String term, int bookId) {
        try (Connection connection = open();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT tf FROM postings WHERE term = ? AND book_id = ?")) {
            select.setString(1, term);
            select.setInt(2, bookId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IndexStoreException("Could not read term frequency", e);
        }
    }

    /** Title stored for a book, or null. */
    public String title(int bookId) {
        try (Connection connection = open();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT title FROM books WHERE book_id = ?")) {
            select.setInt(1, bookId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IndexStoreException("Could not read book title", e);
        }
    }

    private static long scalarLong(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }

    public record IndexResult(int bookId, int uniqueTerms, boolean replaced) {}

    public record RebuildResult(int booksIndexed) {}

    public record IndexStats(long books, long uniqueTerms, long postings, String lastUpdate, long sizeBytes) {}

    public record BookIndexData(BookMetadata metadata, Map<String, Integer> termFrequencies) {}

    public static class IndexStoreException extends RuntimeException {
        public IndexStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
