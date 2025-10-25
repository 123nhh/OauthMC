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
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LinuxDoOauth implements IOauth {

    private final URL authorizeUrl;
    private final URL tokenUrl;
    private final URL userInfoUrl;
    private final Gson gson;
    private final String clientId;
    private final String clientSecret;
    private final String callbackUrl;
    private final int callbackPort;
    private final IDatabaseHook db;
    private final OauthMCConfig config;
    private final Map<String, PendingAuth> pendingAuths;
    private final Map<UUID, String> uuidToState;
    private CallbackServer callbackServer;

    public LinuxDoOauth(String clientId, String clientSecret, IDatabaseHook db, OauthMCConfig config) {
        this.gson = new Gson();
        this.pendingAuths = new ConcurrentHashMap<>();
        this.uuidToState = new ConcurrentHashMap<>();

        try {
            authorizeUrl = new URL("https://connect.linux.do/oauth2/authorize");
            tokenUrl = new URL("https://connect.linux.do/oauth2/token");
            userInfoUrl = new URL("https://connect.linux.do/api/user");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUrl = config.getLinuxDoCallbackUrl();
        this.callbackPort = config.getLinuxDoCallbackPort();
        this.db = db;
        this.config = config;
    }

    public void startCallbackServer() {
        if (callbackServer != null) {
            Bukkit.getLogger().warning("OauthMC: Callback server already started");
            return;
        }
        
        callbackServer = new CallbackServer(callbackPort, this);
        try {
            callbackServer.start();
        } catch (IOException e) {
            Bukkit.getLogger().severe("OauthMC: Failed to start callback server on port " + callbackPort);
            e.printStackTrace();
            throw new RuntimeException("Failed to start LinuxDo callback server", e);
        }
    }

    public void stopCallbackServer() {
        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer = null;
        }
        pendingAuths.clear();
        uuidToState.clear();
    }

    @Override
    public CodeAndLinkResponse beginLogin(UUID uuid) {
        String existingState = uuidToState.get(uuid);
        if (existingState != null && pendingAuths.containsKey(existingState)) {
            PendingAuth pending = pendingAuths.get(existingState);
            if (!pending.isExpired()) {
                return createResponse(existingState);
            } else {
                pendingAuths.remove(existingState);
                uuidToState.remove(uuid);
            }
        }

        String state = UUID.randomUUID().toString();
        PendingAuth pending = new PendingAuth(uuid, 10);
        
        pendingAuths.put(state, pending);
        uuidToState.put(uuid, state);
        
        return createResponse(state);
    }

    private CodeAndLinkResponse createResponse(String state) {
        try {
            String authUrl = authorizeUrl.toString() + 
                "?client_id=" + encode(clientId) +
                "&redirect_uri=" + encode(callbackUrl) +
                "&response_type=code" +
                "&state=" + encode(state) +
                "&scope=" + encode("openid profile email");
            
            return new LinuxDoAuthResponse(authUrl, state);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create authorization URL", e);
        }
    }

    public void handleCallback(String code, String state) {
        PendingAuth pending = pendingAuths.get(state);
        if (pending == null) {
            Bukkit.getLogger().warning("OauthMC: Received callback with unknown state: " + state);
            return;
        }

        if (pending.isExpired()) {
            Bukkit.getLogger().warning("OauthMC: Received callback with expired state: " + state);
            pendingAuths.remove(state);
            uuidToState.remove(pending.getPlayerUuid());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(
            Bukkit.getPluginManager().getPlugin("OauthMC"),
            () -> processCallback(code, state, pending)
        );
    }

    private void processCallback(String code, String state, PendingAuth pending) {
        try {
            String accessToken = exchangeCodeForToken(code);
            if (accessToken == null) {
                Bukkit.getLogger().warning("OauthMC: Failed to exchange code for token");
                return;
            }

            LinuxDoUserInfo userInfo = getUserInfo(accessToken);
            if (userInfo == null) {
                Bukkit.getLogger().warning("OauthMC: Failed to get user info");
                return;
            }

            int minTrustLevel = config.getLinuxDoMinTrustLevel();
            Bukkit.getLogger().info(String.format(
                "OauthMC: Validating trust level for user %s: has %d, requires %d",
                userInfo.username, userInfo.trustLevel, minTrustLevel
            ));

            if (minTrustLevel > 0 && userInfo.trustLevel < minTrustLevel) {
                Bukkit.getLogger().warning(String.format(
                    "OauthMC: User %s has insufficient trust level: %d (requires %d)",
                    userInfo.username, userInfo.trustLevel, minTrustLevel
                ));
                return;
            }

            if (config.isLinuxDoRequireActive() && !userInfo.active) {
                Bukkit.getLogger().warning("OauthMC: User " + userInfo.username + " is not active");
                return;
            }

            UUID uuid = pending.getPlayerUuid();
            if (db.isInUse(userInfo.email)) {
                String existingEmail = db.getEmail(uuid);
                if (existingEmail == null || !existingEmail.equals(userInfo.email)) {
                    Bukkit.getLogger().info("OauthMC: Email " + userInfo.email + " is already in use by another player");
                    return;
                }
            }

            db.setLink(uuid, userInfo.email);
            db.setLinuxDoBinding(uuid, userInfo.id, userInfo.username);
            db.updateLastAuthTime(uuid);
            
            pending.setAuthorized(userInfo);
            
            Bukkit.getLogger().info("OauthMC: Successfully authenticated " + userInfo.username + " (UUID: " + uuid + ")");
            
        } catch (Exception e) {
            Bukkit.getLogger().severe("OauthMC: Error processing callback for state: " + state);
            e.printStackTrace();
        }
    }

    public AuthResult pollAuthStatus(UUID uuid) {
        String state = uuidToState.get(uuid);
        if (state == null) {
            return AuthResult.invalid();
        }

        PendingAuth pending = pendingAuths.get(state);
        if (pending == null) {
            uuidToState.remove(uuid);
            return AuthResult.invalid();
        }

        if (pending.isExpired()) {
            pendingAuths.remove(state);
            uuidToState.remove(uuid);
            return AuthResult.expired();
        }

        if (pending.isAuthorized()) {
            pendingAuths.remove(state);
            uuidToState.remove(uuid);
            return AuthResult.success(pending.getUserInfo());
        }

        return AuthResult.pending();
    }

    private String exchangeCodeForToken(String code) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) tokenUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setUseCaches(false);
            connection.setDoOutput(true);

            String body = "grant_type=authorization_code" +
                "&code=" + encode(code) +
                "&redirect_uri=" + encode(callbackUrl) +
                "&client_id=" + encode(clientId) +
                "&client_secret=" + encode(clientSecret);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                ? connection.getInputStream() 
                : connection.getErrorStream();

            String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();

            if (responseCode != 200) {
                Bukkit.getLogger().warning("OauthMC: Token exchange failed with code " + responseCode + ": " + response);
                return null;
            }

            LinuxDoTokenResponse tokenResponse = gson.fromJson(response, LinuxDoTokenResponse.class);
            return tokenResponse.accessToken;
            
        } catch (IOException e) {
            Bukkit.getLogger().severe("OauthMC: Error exchanging code for token");
            e.printStackTrace();
            return null;
        }
    }

    private LinuxDoUserInfo getUserInfo(String accessToken) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) userInfoUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();

            String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();

            if (responseCode != 200) {
                Bukkit.getLogger().warning("OauthMC: User info request failed with code " + responseCode + ": " + response);
                return null;
            }

            Bukkit.getLogger().info("OauthMC: Raw user info JSON: " + response);
            
            LinuxDoUserInfo userInfo = gson.fromJson(response, LinuxDoUserInfo.class);
            
            if (userInfo != null) {
                Bukkit.getLogger().info("OauthMC: Parsed user info: " + userInfo.toString());
                Bukkit.getLogger().info("OauthMC: Trust level: " + userInfo.trustLevel);
            } else {
                Bukkit.getLogger().warning("OauthMC: Failed to parse user info from JSON");
            }
            
            return userInfo;
            
        } catch (IOException e) {
            Bukkit.getLogger().severe("OauthMC: Error getting user info");
            e.printStackTrace();
            return null;
        }
    }

    private static class LinuxDoAuthResponse extends CodeAndLinkResponse {
        public LinuxDoAuthResponse(String loginLink, String userCode) {
            this.loginLink = loginLink;
            this.userCode = userCode;
        }
    }
}
