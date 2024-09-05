package me.cooleg.oauthmc.authentication.google;

import com.google.gson.*;
import me.cooleg.oauthmc.authentication.IOauth;

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

    public GoogleOauth(String clientId, String clientSecret) {
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
    }

    @Override
    public boolean loginPlayer(UUID uuid) {
        return false;
    }

    @Override
    public boolean checkIfCached(UUID uuid) {
        return false;
    }

    @Override
    public String generateCode(UUID uuid) {
        String text = urlToResponse(requestCodeUrl, "POST", "client_id=" + encode(clientId) + "&scope=email%20profile");
        System.out.println(text);
        GoogleDeviceCodeResponse response = gson.fromJson(text, GoogleDeviceCodeResponse.class);

        CompletableFuture.runAsync(() -> {
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

                System.out.println(authResponse);
                GooglePollingResponse pollingResponse = gson.fromJson(authResponse, GooglePollingResponse.class);

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
                    return;
                } else if (pollingResponse.denied) {
                    return;
                }
            }
        }).exceptionally((throwable) -> {throwable.printStackTrace(); return null;});

        return response.userCode;
    }

}
