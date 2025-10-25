package me.cooleg.oauthmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class OauthMCConfig {

    private final static int CONFIG_VER = 1;

    // Global Settings
    private LoginMode loginMode;
    private DatabaseMode dbMode;
    private String clientId;
    private Component serverName;
    private String kickMessage;
    private String emailSuffix;
    private boolean emailSuffixEnabled;
    private Set<UUID> whitelistedUuids;

    // Microsoft-Specific Settings
    private String tenant;

    // Google-Specific Settings
    private String clientSecret;

    // LinuxDo-Specific Settings
    private String callbackUrl;
    private int callbackPort;
    private int tokenExpiryMinutes;
    private int minTrustLevel;
    private boolean requireActive;

    // Remote Database Settings
    private String hostName;
    private int port;
    private String username;
    private String password;
    private String databaseName;

    public OauthMCConfig(OauthMC main) {
        FileConfiguration configuration = main.getConfig();
        if (configuration.getInt("config-ver", 0) != CONFIG_VER) {
            Bukkit.getLogger().info("OauthMC: Config version is outdated! Updating config now...");
            configuration = updateConfig(main);
        }

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

        Set<UUID> temporarySet = new HashSet<>();
        for (String stringUuid : configuration.getStringList("whitelisted-uuids")) {
            try {
                temporarySet.add(UUID.fromString(stringUuid));
            } catch (IllegalArgumentException ex) {
                Bukkit.getLogger().info("OauthMC: Failed to read whitelisted UUID \"" + stringUuid + "\"!");
            }
        }

        whitelistedUuids = Collections.unmodifiableSet(temporarySet);

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
        } else if (loginMode == LoginMode.GOOGLE) {
            ConfigurationSection section = getConfigSection(configuration, "google-settings");
            clientId = section.getString("client-id");
            clientSecret = section.getString("client-secret");
        } else if (loginMode == LoginMode.LINUXDO) {
            ConfigurationSection section = getConfigSection(configuration, "linuxdo-settings");
            clientId = section.getString("client-id");
            clientSecret = section.getString("client-secret");
            callbackUrl = section.getString("callback-url");
            callbackPort = section.getInt("callback-port", 8080);
            tokenExpiryMinutes = section.getInt("token-expiry-minutes", 10);
            minTrustLevel = section.getInt("min-trust-level", 0);
            requireActive = section.getBoolean("require-active", true);
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
        MICROSOFT,
        LINUXDO;
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

    public Set<UUID> getWhitelistedUuids() {
        return whitelistedUuids;
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

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public int getCallbackPort() {
        return callbackPort;
    }

    public int getTokenExpiryMinutes() {
        return tokenExpiryMinutes;
    }

    public int getMinTrustLevel() {
        return minTrustLevel;
    }

    public boolean isRequireActive() {
        return requireActive;
    }

    private FileConfiguration updateConfig(JavaPlugin plugin) {
        // Fetch all current values
        FileConfiguration config = plugin.getConfig();
        Map<String, Object> values = config.getValues(true);

        // Update file to new file
        try (InputStream stream = OauthMCConfig.class.getResourceAsStream("/config.yml")) {
            if (stream == null) throw new RuntimeException("[" + plugin.getName() + "] Failed to load config file in jar.");

            plugin.getConfig().load(new InputStreamReader(stream));
        } catch (IOException | InvalidConfigurationException ex) {
            throw new RuntimeException("[" + plugin.getName() + "] Failed to load updated config version.");
        }

        // Set values for options to what they were before.
        config = plugin.getConfig();
        for (Map.Entry<String, Object> value : values.entrySet()) {
            // Ignore values that were removed in current config version, and leave new config version as it is.
            if (!config.isSet(value.getKey()) || value.getKey().equals("config-ver")) continue;

            config.set(value.getKey(), value.getValue());
        }

        try {
            config.save(plugin.getDataFolder().getAbsolutePath() + "/config.yml");
        } catch (IOException e) {
            throw new RuntimeException("[" + plugin.getName() + "] Failed to save updated config version.");
        }

        return config;
    }
}
