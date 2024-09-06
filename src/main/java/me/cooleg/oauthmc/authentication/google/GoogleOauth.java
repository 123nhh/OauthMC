package me.cooleg.oauthmc.authentication.google;

import com.google.gson.*;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GoogleOauth implements IOauth {

    private final URL requestCodeUrl;
    private final Gson gson;
    private final String clientId;
    private final String clientSecret;
    private final IDatabaseHook db;
    private final OauthMCConfig config;

    public GoogleOauth(String clientId, String clientSecret, IDatabaseHook db, OauthMCConfig config) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(GoogleDeviceCodeResponse.class, new GoogleDeviceCodeResponse.Deserializer());
        builder.registerTypeAdapter(GooglePollingResponse.class, new GooglePollingResponse.Deserializer());
        gson = builder.create();

        try {
            requestCodeUrl = new URL("https://oauth2.googleapis.com/device/code");
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
        String text = urlToResponse(requestCodeUrl, "POST", "client_id=" + encode(clientId) + "&scope=email%20profile");
        GoogleDeviceCodeResponse response = gson.fromJson(text, GoogleDeviceCodeResponse.class);

        currentlyAuthenticating.put(uuid, response);
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < response.expiresIn; i += response.interval) {
                try {
                    Thread.sleep(response.interval * 1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                String authResponse = stringUrlToResponse("https://oauth2.googleapis.com/token", "POST",
                        "client_id=" + encode(clientId) +
                                "&client_secret=" + encode(clientSecret) +
                                "&device_code=" + encode(response.deviceCode) +
                                "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code"));

                GooglePollingResponse pollingResponse = gson.fromJson(authResponse, GooglePollingResponse.class);

                if (!pollingResponse.waiting) {
                    String[] chunks = pollingResponse.idToken.split("\\.");

                    Base64.Decoder decoder = Base64.getUrlDecoder();

                    //String header = new String(decoder.decode(chunks[0]));
                    String payload = new String(decoder.decode(chunks[1]));

                    JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
                    if (!object.has("email")) {return;}
                    String email = object.get("email").getAsString();

                    if (db.isInUse(email)) return;
                    if (config.isEmailSuffixEnabled() && !email.endsWith(config.getEmailSuffix())) return;

                    db.setLink(uuid, email);
                    return;
                } else if (pollingResponse.denied) {
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

}
