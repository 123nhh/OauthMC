package me.cooleg.oauthmc.listeners;

import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

public class AsyncPreLoginListener implements Listener {

    private final IDatabaseHook db;
    private final IOauth auth;
    private final OauthMCConfig config;

    public AsyncPreLoginListener(IDatabaseHook db, IOauth auth, OauthMCConfig config) {
        this.db = db;
        this.auth = auth;
        this.config = config;
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        UUID id = event.getUniqueId();
        if (config.getWhitelistedUuids().contains(id)) return;
        if (db.hasLoggedIn(id)) return;

        try {
            CodeAndLinkResponse response = auth.beginLogin(event.getUniqueId());

            String kickText = config.getKickMessage().replace("%code%", response.userCode).replace("%link%", response.loginLink);
            Component message = config.getServerName()
                    .append(MiniMessage.miniMessage().deserialize("\n\n" + kickText));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, message);
        } catch (Exception ex) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Oauth failure occurred. Please contact the admin."));
            ex.printStackTrace();
        }
    }

}
