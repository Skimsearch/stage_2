package com.skimsearch.search;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SearchDatabase {

    private final String jdbcUrl;

    public SearchDatabase(SearchConfig config) {
        String path = config.indexPath()
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace("\\", "/");

        this.jdbcUrl = "jdbc:sqlite:file:" + path + "?mode=ro";
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public boolean isAvailable() {
        try (Connection connection = getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}