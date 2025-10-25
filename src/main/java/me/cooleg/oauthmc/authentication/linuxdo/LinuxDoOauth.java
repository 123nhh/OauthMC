package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.*;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import org.bukkit.Bukkit;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LinuxDoOauth implements IOauth {

    private final URL requestCodeUrl;
    private final URL tokenUrl;
    private final URL userInfoUrl;
    private final Gson gson;
    private final String clientId;
    private final String clientSecret;
    private final IDatabaseHook db;
    private final OauthMCConfig config;

    public LinuxDoOauth(String clientId, String clientSecret, IDatabaseHook db, OauthMCConfig config) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LinuxDoDeviceCodeResponse.class, new LinuxDoDeviceCodeResponse.Deserializer());
        builder.registerTypeAdapter(LinuxDoPollingResponse.class, new LinuxDoPollingResponse.Deserializer());
        gson = builder.create();

        try {
            requestCodeUrl = new URL("https://connect.linux.do/oauth2/device/authorize");
            tokenUrl = new URL("https://connect.linux.do/oauth2/token");
            userInfoUrl = new URL("https://connect.linux.do/api/user");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.db = db;
        this.config = config;
    }

    @Override
    public CodeAndLinkResponse beginLogin(UUID uuid) {
        if (currentlyAuthenticating.containsKey(uuid)) return currentlyAuthenticating.get(uuid);
        
        String text = urlToResponse(requestCodeUrl, "POST", 
            "client_id=" + encode(clientId) + 
            "&scope=" + encode("read user:email"));
        LinuxDoDeviceCodeResponse response = gson.fromJson(text, LinuxDoDeviceCodeResponse.class);

        currentlyAuthenticating.put(uuid, response);
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < response.expiresIn; i += response.interval) {
                try {
                    Thread.sleep(response.interval * 1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                String authResponse = urlToResponse(tokenUrl, "POST",
                        "client_id=" + encode(clientId) +
                                "&client_secret=" + encode(clientSecret) +
                                "&device_code=" + encode(response.deviceCode) +
                                "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code"));

                LinuxDoPollingResponse pollingResponse = gson.fromJson(authResponse, LinuxDoPollingResponse.class);

                if (!pollingResponse.waiting) {
                    if (pollingResponse.denied) {
                        return;
                    }

                    // Get user info
                    try {
                        LinuxDoUserInfo userInfo = getUserInfo(pollingResponse.accessToken);
                        
                        if (userInfo == null) {
                            Bukkit.getLogger().warning("OauthMC: Failed to get user info for UUID " + uuid);
                            return;
                        }

                        // Check trust level and active status
                        if (config.getLinuxDoMinTrustLevel() > 0 && userInfo.trustLevel < config.getLinuxDoMinTrustLevel()) {
                            Bukkit.getLogger().info("OauthMC: User " + userInfo.username + " has insufficient trust level: " + userInfo.trustLevel);
                            return;
                        }

                        if (config.isLinuxDoRequireActive() && !userInfo.active) {
                            Bukkit.getLogger().info("OauthMC: User " + userInfo.username + " is not active");
                            return;
                        }

                        // Check if email is already in use
                        if (db.isInUse(userInfo.email)) {
                            // Check if it's the same player
                            String existingEmail = db.getEmail(uuid);
                            if (existingEmail == null || !existingEmail.equals(userInfo.email)) {
                                Bukkit.getLogger().info("OauthMC: Email " + userInfo.email + " is already in use by another player");
                                return;
                            }
                        }

                        // Store the binding
                        db.setLink(uuid, userInfo.email);
                        db.setLinuxDoBinding(uuid, userInfo.id, userInfo.username);
                        db.updateLastAuthTime(uuid);
                        
                        Bukkit.getLogger().info("OauthMC: Successfully authenticated " + userInfo.username + " for UUID " + uuid);
                    } catch (Exception e) {
                        Bukkit.getLogger().severe("OauthMC: Error getting user info for UUID " + uuid);
                        e.printStackTrace();
                    }
                    return;
                }
            }
        });

        future.thenRunAsync(() -> {
            currentlyAuthenticating.remove(uuid);
        });

        future.exceptionally((throwable) -> {
            currentlyAuthenticating.remove(uuid);
            throwable.printStackTrace();
            return null;
        });

        return response;
    }

    private LinuxDoUserInfo getUserInfo(String accessToken) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) userInfoUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setUseCaches(false);

            InputStream inputStream;
            if (100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            byte[] bytes = inputStream.readAllBytes();
            inputStream.close();
            String response = new String(bytes);

            return gson.fromJson(response, LinuxDoUserInfo.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
