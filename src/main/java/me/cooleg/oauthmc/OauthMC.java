package me.cooleg.oauthmc;

import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.authentication.google.GoogleOauth;
import me.cooleg.oauthmc.authentication.linuxdo.LinuxDoOauth;
import me.cooleg.oauthmc.authentication.microsoft.MicrosoftOauth;
import me.cooleg.oauthmc.commands.OauthCommand;
import me.cooleg.oauthmc.listeners.AsyncPreLoginListener;
import me.cooleg.oauthmc.listeners.AuthMeIntegrationListener;
import me.cooleg.oauthmc.listeners.PlayerCommandListener;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import me.cooleg.oauthmc.persistence.impl.MySQLHook;
import me.cooleg.oauthmc.persistence.impl.SqliteHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class OauthMC extends JavaPlugin {

    private static OauthMC instance;
    private OauthMCConfig config;
    private IDatabaseHook databaseHook;
    private IOauth oauthProvider;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = new OauthMCConfig(this);

        if (config.getDbMode() == OauthMCConfig.DatabaseMode.MYSQL) {
            databaseHook = new MySQLHook(config);
        } else {
            databaseHook = new SqliteHook(this);
        }

        if (config.getLoginMode() == OauthMCConfig.LoginMode.MICROSOFT) {
            oauthProvider = new MicrosoftOauth(config.getClientId(), config.getTenant(), databaseHook, config);
        } else if (config.getLoginMode() == OauthMCConfig.LoginMode.GOOGLE) {
            oauthProvider = new GoogleOauth(config.getClientId(), config.getClientSecret(), databaseHook, config);
        } else if (config.getLoginMode() == OauthMCConfig.LoginMode.LINUXDO) {
            LinuxDoOauth linuxDoOauth = new LinuxDoOauth(config.getClientId(), config.getClientSecret(), databaseHook, config);
            linuxDoOauth.startCallbackServer();
            oauthProvider = linuxDoOauth;
        }

        if (config.isAuthmeEnabled() && Bukkit.getPluginManager().getPlugin("AuthMe") != null) {
            Bukkit.getLogger().info("OauthMC: AuthMe integration enabled");
            Bukkit.getPluginManager().registerEvents(new AuthMeIntegrationListener(databaseHook, oauthProvider, config), this);
            
            if (config.isDisablePasswordLogin()) {
                Bukkit.getPluginManager().registerEvents(new PlayerCommandListener(config), this);
            }
        } else {
            Bukkit.getPluginManager().registerEvents(new AsyncPreLoginListener(databaseHook, oauthProvider, config), this);
        }

        getCommand("oauth").setExecutor(new OauthCommand(databaseHook, config));
    }

    @Override
    public void onDisable() {
        if (oauthProvider instanceof LinuxDoOauth) {
            ((LinuxDoOauth) oauthProvider).stopCallbackServer();
        }
    }

    public static OauthMC getInstance() {
        return instance;
    }

    public OauthMCConfig getOauthConfig() {
        return config;
    }

    public IDatabaseHook getDatabaseHook() {
        return databaseHook;
    }

    public IOauth getOauthProvider() {
        return oauthProvider;
    }

}
