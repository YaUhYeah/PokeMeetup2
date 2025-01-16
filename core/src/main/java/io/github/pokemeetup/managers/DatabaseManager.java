package io.github.pokemeetup.managers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.github.pokemeetup.utils.GameLogger;

import java.nio.charset.StandardCharsets;
import java.sql.*;

import static io.github.pokemeetup.utils.PasswordUtils.hashPassword;

public class DatabaseManager {
    private static final String DB_PATH = "real";
    public static final String DB_USER = "sa";
    public static final String DB_PASS = "";
    private static final int BASE_PORT = 9101;
    private Connection connection;


    public DatabaseManager() {
        try {
            connectToDatabase();
            initializeDatabase();
        } catch (SQLException e) {
            GameLogger.info("Database initialization error: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public boolean checkUsernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM PLAYERS WHERE USERNAME = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            GameLogger.error("Database error checking username: " + e.getMessage());
            throw new RuntimeException("Database error checking username", e);
        }
    }

    private void connectToDatabase() throws SQLException {
        @SuppressWarnings("DefaultLocale")
        String url = String.format("jdbc:h2:tcp://localhost:%d/%s", BASE_PORT, DB_PATH);
        connection = DriverManager.getConnection(url, DB_USER, DB_PASS);
        GameLogger.info("Connected to database on port " + BASE_PORT);
    }


    public void dispose() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                GameLogger.info("Database connection closed");
            }
        } catch (SQLException e) {
            GameLogger.error("Error closing database connection: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connectToDatabase();
        }
        return connection;
    }

    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                GameLogger.info("Reconnecting to database...");
                connectToDatabase();
            }
        } catch (SQLException e) {
            GameLogger.info("Error checking connection: " + e.getMessage());
        }
    }
    public boolean registerPlayer(String username, String password) {
        if (doesUsernameExist(username)) {
            GameLogger.info("Username already exists: " + username);
            return false;
        }
        String sql = "INSERT INTO PLAYERS (username, password_hash, x_pos, y_pos) VALUES (?, ?, 0, 0)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String hashedPassword = hashPassword(password);
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);

            int result = stmt.executeUpdate();
            GameLogger.info("Player registration " + (result > 0 ? "successful" : "failed") +
                " for username: " + username);
            return result > 0;
        } catch (SQLException e) {
            GameLogger.error("Database error registering player: " + e.getMessage());
            throw new RuntimeException("Database error registering player", e);
        }
    }
    private void initializeDatabase() {
        String createPlayersTable =
            "CREATE TABLE IF NOT EXISTS PLAYERS (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(255) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(), " +
                "last_login TIMESTAMP, " +
                "status VARCHAR(20) DEFAULT 'OFFLINE', " +
                "x_pos INT DEFAULT 0, " +
                "y_pos INT DEFAULT 0" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayersTable);
            GameLogger.info("Database tables initialized successfully");
        } catch (SQLException e) {
            GameLogger.error("Error initializing database: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public void updatePlayerCoordinates(String username, int x, int y) {
        ensureConnection();
        String updateSQL = "UPDATE PLAYERS SET x_pos = ?, y_pos = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setInt(1, x);
            pstmt.setInt(2, y);
            pstmt.setString(3, username);
            GameLogger.info("Updated coordinates for " + username + ": (" + x + ", " + y + ")");
        } catch (SQLException e) {
            GameLogger.error("Error updating coordinates: " + e.getMessage());
            throw new RuntimeException("Failed to update player coordinates", e);
        }
    }

    private boolean doesUsernameExist(String username) {
        String sql = "SELECT 1 FROM PLAYERS WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();
            GameLogger.info("Username check: '" + username + "' exists: " + exists);
            return exists;
        } catch (SQLException e) {
            GameLogger.error("Error checking if username exists: " + e.getMessage());
            throw new RuntimeException("Database error checking username", e);
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                GameLogger.info("Database connection closed.");
            }
        } catch (SQLException e) {
            GameLogger.info("Error closing database connection: " + e.getMessage());
        }
    }

    public boolean authenticatePlayer(String username, String password) {
        // Updated to use password_hash column name
        String query = "SELECT password_hash FROM PLAYERS WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                boolean verified = BCrypt.verifyer().verify(
                    password.getBytes(StandardCharsets.UTF_8),
                    storedHash.getBytes(StandardCharsets.UTF_8)
                ).verified;

                if (verified) {
                    updateLastLogin(username);
                    GameLogger.info("Authentication successful for username: " + username);
                } else {
                    GameLogger.info("Authentication failed - invalid password for username: " + username);
                }

                return verified;
            }
            GameLogger.info("Authentication failed - username not found: " + username);
            return false;
        } catch (SQLException e) {
            GameLogger.error("Database error during authentication: " + e.getMessage());
            return false;
        }
    }  public String getPasswordHash(String username) {
        String query = "SELECT password_hash FROM PLAYERS WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                GameLogger.info("Retrieved password hash for username: " + username);
                return storedHash;
            } else {
                GameLogger.info("No password hash found for username: " + username);
                return null;
            }
        } catch (SQLException e) {
            GameLogger.error("Database error retrieving password hash: " + e.getMessage());
            return null;
        }
    }
    private void updateLastLogin(String username) {
        String sql = "UPDATE PLAYERS SET LAST_LOGIN = CURRENT_TIMESTAMP(), STATUS = 'ONLINE' WHERE USERNAME = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            GameLogger.error("Error updating last login time: " + e.getMessage());
        }
    }


    public int[] getPlayerCoordinates(String username) {
        String sql = "SELECT x_pos, y_pos FROM PLAYERS WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new int[]{rs.getInt("x_pos"), rs.getInt("y_pos")};
            }
            return new int[]{0, 0};
        } catch (SQLException e) {
            GameLogger.error("Error retrieving coordinates: " + e.getMessage());
            return new int[]{0, 0};
        }
    }
}
