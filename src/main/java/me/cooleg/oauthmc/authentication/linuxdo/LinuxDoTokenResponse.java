package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.*;

import java.lang.reflect.Type;

public class LinuxDoTokenResponse {

    public String accessToken;
    public String tokenType;
    public int expiresIn;
    public String refreshToken;
    public String scope;

    public static class Deserializer implements JsonDeserializer<LinuxDoTokenResponse> {

        @Override
        public LinuxDoTokenResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            LinuxDoTokenResponse response = new LinuxDoTokenResponse();
            JsonObject object = json.getAsJsonObject();

            if (object.has("error")) {
                throw new RuntimeException("Token exchange failed: " + object.get("error").getAsString());
            }

            response.accessToken = object.get("access_token").getAsString();
            response.tokenType = object.get("token_type").getAsString();
            response.expiresIn = object.get("expires_in").getAsInt();

            if (object.has("refresh_token")) {
                response.refreshToken = object.get("refresh_token").getAsString();
            }

            if (object.has("scope")) {
                response.scope = object.get("scope").getAsString();
            }

            return response;
        }
    }

}
