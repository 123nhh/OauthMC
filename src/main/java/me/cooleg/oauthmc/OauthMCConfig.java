package me.cooleg.oauthmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class OauthMCConfig {

    // Global Settings
    private LoginMode loginMode;
    private DatabaseMode dbMode;
    private String clientId;
    private Component serverName;
    private String kickMessage;
    private String emailSuffix;
    private boolean emailSuffixEnabled;

    // Microsoft-Specific Settings
    private String tenant;

    // Google-Specific Settings
    private String clientSecret;

    // Remote Database Settings
    private String hostName;
    private int port;
    private String username;
    private String password;
    private String databaseName;

    public OauthMCConfig(FileConfiguration configuration) {
        String stringServerName = configuration.getString("server-name");
        if (stringServerName == null) {
            throw new RuntimeException("No server name provided to OauthMC!");
        }
        serverName = MiniMessage.miniMessage().deserialize(stringServerName);

        kickMessage = configuration.getString("kick-message");
        if (kickMessage == null) {
            throw new RuntimeException("No kick message provided to OauthMC!");
        }

        emailSuffix = configuration.getString("email-suffix");
        emailSuffixEnabled = emailSuffix != null && !emailSuffix.trim().isEmpty();

        String loginModeString = configuration.getString("login-mode").toUpperCase().trim();
        try {
            loginMode = LoginMode.valueOf(loginModeString);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Bad login mode supplied to OauthMC!");
        }

        String dbModeString = configuration.getString("db-mode").toUpperCase().trim();
        try {
            dbMode = DatabaseMode.valueOf(dbModeString);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Bad database mode supplied to OauthMC!");
        }

        if (loginMode == LoginMode.MICROSOFT) {
            ConfigurationSection section = getConfigSection(configuration, "microsoft-settings");
            clientId = section.getString("client-id");
            tenant = section.getString("tenant");
        } else {
            ConfigurationSection section = getConfigSection(configuration, "google-settings");
            clientId = section.getString("client-id");
            clientSecret = section.getString("client-secret");
        }

        if (dbMode == DatabaseMode.MYSQL) {
            ConfigurationSection section = getConfigSection(configuration, "mysql-settings");
            readDbSettings(section);
            databaseName = section.getString("database-name");
            if (databaseName == null) {
                throw new RuntimeException("No database name provided!");
            }
        }
    }

    public enum LoginMode {
        GOOGLE,
        MICROSOFT;
    }

    public enum DatabaseMode {
        SQLITE,
        MYSQL;
    }

    private ConfigurationSection getConfigSection(FileConfiguration configuration, String sectionName) {
        ConfigurationSection section = configuration.getConfigurationSection(sectionName);

        if (section == null) throw new RuntimeException("OauthMC: Bad configuration section! Missing section " + sectionName + "!");
        return section;
    }

    private void readDbSettings(ConfigurationSection section) {
        if (section == null) {
            throw new RuntimeException("OauthMC: Database configuration is improperly formatted!");
        }

        hostName = section.getString("hostname");
        port = section.getInt("port");
        username = section.getString("username");
        password = section.getString("password");
    }

    public LoginMode getLoginMode() {
        return loginMode;
    }

    public DatabaseMode getDbMode() {
        return dbMode;
    }

    public String getClientId() {
        return clientId;
    }

    public Component getServerName() {
        return serverName;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public String getEmailSuffix() {
        return emailSuffix;
    }

    public boolean isEmailSuffixEnabled() {
        return emailSuffixEnabled;
    }

    public String getTenant() {
        return tenant;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
