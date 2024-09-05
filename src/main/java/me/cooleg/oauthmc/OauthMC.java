package me.cooleg.oauthmc;

import me.cooleg.oauthmc.authentication.microsoft.MicrosoftOauth;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class OauthMC extends JavaPlugin {

    @Override
    public void onEnable() {
        MicrosoftOauth auth = new MicrosoftOauth("729a413f-7839-4108-b789-045dfad5a40a", "44467e6f-462c-4ea2-823f-7800de5434e3");
        auth.beginLogin(UUID.randomUUID());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
