package me.cooleg.oauthmc;

import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.authentication.google.GoogleOauth;
import me.cooleg.oauthmc.authentication.microsoft.MicrosoftOauth;
import me.cooleg.oauthmc.listeners.AsyncPreLoginListener;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import me.cooleg.oauthmc.persistence.impl.SqliteHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class OauthMC extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        OauthMCConfig config = new OauthMCConfig(getConfig());

        IDatabaseHook hook = new SqliteHook(this);;
        if (config.getDbMode() == OauthMCConfig.DatabaseMode.SQLITE) {
            hook = new SqliteHook(this);
        } else if (config.getDbMode() == OauthMCConfig.DatabaseMode.REDIS) {
            //hook = new RedisHook();
        } else {
            //hook = new MySQLHook();
        }

        IOauth auth;
        if (config.getLoginMode() == OauthMCConfig.LoginMode.MICROSOFT) {
            auth = new MicrosoftOauth(config.getClientId(), config.getTenant(), hook, config);
        } else {
            auth = new GoogleOauth(config.getClientId(), config.getClientSecret(), hook, config);
        }

        Bukkit.getPluginManager().registerEvents(new AsyncPreLoginListener(hook, auth, config), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
