package kth.iv1351.coursealloc.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DBHandler {
    private final Connection connection;

    public DBHandler(String url, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(url, user, password);
        this.connection.setAutoCommit(false);
    }

    public void beginTransaction() throws SQLException {}
    public void commit() throws SQLException { connection.commit(); }
    public void rollback() throws SQLException { connection.rollback(); }

    public void testConnection() throws SQLException {
        String sql = "SELECT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                System.out.println("DB test OK, got: " + rs.getInt(1));
            } else {
                throw new SQLException("DB test failed");
            }
        }
    }
}

