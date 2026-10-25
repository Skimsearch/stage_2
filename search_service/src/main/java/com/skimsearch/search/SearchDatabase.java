package com.skimsearch.search;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SearchDatabase {

    private final String jdbcUrl;

    public SearchDatabase(SearchConfig config) {
        this.jdbcUrl = "jdbc:sqlite:file:"
                + config.indexPath().toAbsolutePath()
                + "?mode=ro";
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}