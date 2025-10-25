package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LinuxDoOauth implements IOauth {

    private final Gson gson;
    private final String clientId;
    private final String callbackUrl;
    private final int tokenExpiryMinutes;
    private final int minTrustLevel;
    private final boolean requireActive;
    private final IDatabaseHook db;
    private final OauthMCConfig config;
    private final LinuxDoCallbackServer callbackServer;
    private final Map<String, UUID> stateToUuid;

    public LinuxDoOauth(String clientId, String clientSecret, String callbackUrl, int callbackPort,
                        int tokenExpiryMinutes, int minTrustLevel, boolean requireActive,
                        IDatabaseHook db, OauthMCConfig config) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LinuxDoTokenResponse.class, new LinuxDoTokenResponse.Deserializer());
        builder.registerTypeAdapter(LinuxDoUserInfo.class, new LinuxDoUserInfo.Deserializer());
        gson = builder.create();

        this.clientId = clientId;
        this.callbackUrl = callbackUrl;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
        this.minTrustLevel = minTrustLevel;
        this.requireActive = requireActive;
        this.db = db;
        this.config = config;
        this.stateToUuid = new ConcurrentHashMap<>();

        try {
            this.callbackServer = new LinuxDoCallbackServer(callbackPort, clientId, clientSecret, callbackUrl, gson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start LinuxDo OAuth callback server", e);
        }
    }

    @Override
    public CodeAndLinkResponse beginLogin(UUID uuid) {
        if (currentlyAuthenticating.containsKey(uuid)) {
            return currentlyAuthenticating.get(uuid);
        }

        String state = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + (tokenExpiryMinutes * 60 * 1000L);

        String authUrl = "https://connect.linux.do/oauth2/authorize"
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callbackUrl)
                + "&response_type=code"
                + "&state=" + encode(state);

        LinuxDoCodeAndLinkResponse response = new LinuxDoCodeAndLinkResponse();
        response.state = state;
        response.expiresAt = expiresAt;
        response.userCode = state;
        response.loginLink = authUrl;

        stateToUuid.put(state, uuid);
        currentlyAuthenticating.put(uuid, response);

        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            long pollInterval = 2000;
            long maxAttempts = (tokenExpiryMinutes * 60 * 1000L) / pollInterval;

            for (int i = 0; i < maxAttempts; i++) {
                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException e) {
                    Bukkit.getLogger().warning("LinuxDo OAuth polling interrupted: " + e.getMessage());
                    return;
                }

                if (System.currentTimeMillis() > expiresAt) {
                    Bukkit.getLogger().info("LinuxDo OAuth state expired for UUID: " + uuid);
                    return;
                }

                LinuxDoUserInfo userInfo = callbackServer.getAuthResult(state);
                if (userInfo != null) {
                    if (requireActive && !userInfo.active) {
                        Bukkit.getLogger().warning("LinuxDo OAuth: User " + userInfo.username + " is not active");
                        callbackServer.removeAuthResult(state);
                        return;
                    }

                    if (userInfo.trustLevel < minTrustLevel) {
                        Bukkit.getLogger().warning("LinuxDo OAuth: User " + userInfo.username + " does not meet minimum trust level");
                        callbackServer.removeAuthResult(state);
                        return;
                    }

                    String identifier = "linuxdo:" + userInfo.id;

                    if (db.isInUse(identifier)) {
                        Bukkit.getLogger().warning("LinuxDo OAuth: User " + userInfo.username + " is already linked to another account");
                        callbackServer.removeAuthResult(state);
                        return;
                    }

                    if (config.isEmailSuffixEnabled()) {
                        String email = userInfo.username + "@linux.do";
                        if (!email.endsWith(config.getEmailSuffix())) {
                            Bukkit.getLogger().warning("LinuxDo OAuth: User " + userInfo.username + " does not match email suffix");
                            callbackServer.removeAuthResult(state);
                            return;
                        }
                    }

                    db.setLink(uuid, identifier);
                    callbackServer.removeAuthResult(state);
                    Bukkit.getLogger().info("LinuxDo OAuth: Successfully authenticated user " + userInfo.username + " (ID: " + userInfo.id + ")");
                    return;
                }
            }

            Bukkit.getLogger().info("LinuxDo OAuth: Polling timeout for UUID: " + uuid);
        });

        future.thenRunAsync(() -> {
            currentlyAuthenticating.remove(uuid);
            stateToUuid.remove(state);
        });

        future.exceptionally((throwable) -> {
            currentlyAuthenticating.remove(uuid);
            stateToUuid.remove(state);
            Bukkit.getLogger().severe("LinuxDo OAuth error for UUID " + uuid + ": " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });

        return response;
    }

    public void shutdown() {
        if (callbackServer != null) {
            callbackServer.stop();
        }
    }

}
