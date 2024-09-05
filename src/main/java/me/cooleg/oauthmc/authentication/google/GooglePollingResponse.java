package me.cooleg.oauthmc.authentication.google;

import com.google.gson.*;

import java.lang.reflect.Type;

public class GooglePollingResponse {
    public String accessToken;
    public String idToken;
    public int expiresIn;
    public boolean denied = false;
    public boolean waiting = true;

    public static class Deserializer implements JsonDeserializer<GooglePollingResponse> {

        @Override
        public GooglePollingResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            GooglePollingResponse response = new GooglePollingResponse();
            JsonObject object = json.getAsJsonObject();

            if (object.has("error")) {
                if (object.get("error").getAsString().equals("authorization_pending")) {
                    return response;
                }

                response.denied = true;
                return response;
            }

            String scopes = object.get("scope").getAsString();
            if (!scopes.contains("https://www.googleapis.com/auth/userinfo.email")) {
                response.denied = true;
                return response;
            }

            response.waiting = false;
            response.accessToken = object.get("access_token").getAsString();
            response.expiresIn = object.get("expires_in").getAsInt();
            response.idToken = object.get("id_token").getAsString();
            return response;
        }

    }

}
