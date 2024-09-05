package me.cooleg.oauthmc.authentication.microsoft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cooleg.oauthmc.OauthMCConfig;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;
import me.cooleg.oauthmc.authentication.IOauth;
import me.cooleg.oauthmc.persistence.IDatabaseHook;
import org.bukkit.Bukkit;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MicrosoftOauth implements IOauth {

    private final URL requestCodeUrl;
    private final Gson gson;
    private final String clientId;
    private final String tenant;
    private final IDatabaseHook db;
    private final OauthMCConfig config;

    public MicrosoftOauth(String clientId, String tenant, IDatabaseHook db, OauthMCConfig config) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(MicrosoftDeviceCodeResponse.class, new MicrosoftDeviceCodeResponse.Deserializer());
        builder.registerTypeAdapter(MicrosoftPollingResponse.class, new MicrosoftPollingResponse.Deserializer());
        gson = builder.create();

        try {
            requestCodeUrl = new URL("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/devicecode");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        this.clientId = clientId;
        this.tenant = tenant;
        this.db = db;
        this.config = config;
    }

    @Override
    public CodeAndLinkResponse beginLogin(UUID uuid) {
        if (currentlyAuthenticating.containsKey(uuid)) return currentlyAuthenticating.get(uuid);
        String text = urlToResponse(requestCodeUrl, "POST", "client_id=" + encode(clientId) + "&scope=email%20openid%20profile");
        System.out.println(text);
        MicrosoftDeviceCodeResponse response = gson.fromJson(text, MicrosoftDeviceCodeResponse.class);

        currentlyAuthenticating.put(uuid, response);
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < response.expiresIn; i += response.interval) {
                try {
                    Thread.sleep(response.interval * 1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                String authResponse = stringUrlToResponse("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token", "POST",
                        "client_id=" + encode(clientId) +
                                "&device_code=" + encode(response.deviceCode) +
                                "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code"));

                System.out.println(authResponse);
                MicrosoftPollingResponse pollingResponse = gson.fromJson(authResponse, MicrosoftPollingResponse.class);

                if (!pollingResponse.waiting) {
                    String[] chunks = pollingResponse.idToken.split("\\.");

                    Base64.Decoder decoder = Base64.getUrlDecoder();

                    //String header = new String(decoder.decode(chunks[0]));
                    String payload = new String(decoder.decode(chunks[1]));

                    JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
                    System.out.println(payload);
                    if (!object.has("email")) {return;}
                    String email = object.get("email").getAsString();

                    System.out.println(email);
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
