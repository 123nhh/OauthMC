package me.cooleg.oauthmc.persistence.impl;

import me.cooleg.oauthmc.OauthMC;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SqliteHook implements IDatabaseHook {

    private final File file;

    private Connection connection;

    public SqliteHook(OauthMC main) {
        file = new File(main.getDataFolder().getAbsolutePath() + "/data.db");

        createTable();
    }

    @Override
    public boolean hasLoggedIn(UUID uuid) {
        return getEmail(uuid) != null;
    }

    @Override
    public String getEmail(UUID uuid) {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement("SELECT Email FROM OauthMC WHERE UUID = ?")) {
            statement.setString(1, uuid.toString());
            ResultSet results = statement.executeQuery();

            String email = null;
            if (results.next()) {
                email = results.getString(1);
            }

            return email;
        } catch (SQLException ex) {
            Bukkit.getLogger().severe("OauthMC: FAILED TO GET EMAIL FOR UUID " + uuid.toString());
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean isInUse(String email) {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement("SELECT UUID FROM OauthMC WHERE Email = ?")) {
            statement.setString(1, email);
            ResultSet results = statement.executeQuery();

            return results.next();
        } catch (SQLException ex) {
            Bukkit.getLogger().severe("OauthMC: FAILED TO CHECK IF EMAIL IS IN USE FOR EMAIL " + email);
            ex.printStackTrace();
            return true;
        }
    }

    @Override
    public void setLink(UUID id, String email) {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement("INSERT INTO OauthMC (UUID, Email) VALUES (?, ?) ON CONFLICT(UUID) DO NOTHING ON CONFLICT(Email) DO NOTHING")) {
            statement.setString(1, id.toString());
            statement.setString(2, email);
            statement.executeUpdate();
        }catch (SQLException ex) {
            Bukkit.getLogger().severe("OauthMC: FAILED TO SAVE EMAIL FOR UUID " + id.toString());
            ex.printStackTrace();
        }
    }

    public Connection getConnection() {
        if (connection == null) {return newConnection();}

        try {
            if (connection.isClosed()) {return newConnection();}
            else {return connection;}
        } catch (SQLException ex) {
            return newConnection();
        }
    }

    private Connection newConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            return connection;
        } catch (SQLException ex) {
            Bukkit.getLogger().severe("OauthMC: ERROR CONNECTING TO SQLITE DATABASE");
            ex.printStackTrace();
            throw new RuntimeException();
        } catch (ClassNotFoundException ex) {
            Bukkit.getLogger().severe("OauthMC: SQLITE DEPENDENCY NOT FOUND!");
            throw new RuntimeException();
        }
    }

    public void createTable() {
        CompletableFuture.runAsync(() -> {
            Connection conn = getConnection();
            try (PreparedStatement statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS OauthMC (UUID Char(36) PRIMARY KEY, Email VarChar(254) UNIQUE) WITHOUT ROWID")) {
                statement.executeUpdate();
            } catch (SQLException ex) {
                Bukkit.getLogger().severe("OauthMC: FAILED TO CREATE DATABASE TABLE");
            }
        });
    }
}
