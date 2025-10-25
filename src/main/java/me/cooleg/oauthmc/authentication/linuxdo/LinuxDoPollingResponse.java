package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.*;

import java.lang.reflect.Type;

public class LinuxDoPollingResponse {

    public boolean waiting;
    public boolean denied;
    public String accessToken;

    public static class Deserializer implements JsonDeserializer<LinuxDoPollingResponse> {

        @Override
        public LinuxDoPollingResponse deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject object = jsonElement.getAsJsonObject();
            LinuxDoPollingResponse response = new LinuxDoPollingResponse();

            if (object.has("error")) {
                String error = object.get("error").getAsString();
                if (error.equals("authorization_pending") || error.equals("slow_down")) {
                    response.waiting = true;
                } else if (error.equals("access_denied") || error.equals("expired_token")) {
                    response.denied = true;
                }
            } else {
                response.waiting = false;
                response.denied = false;
                response.accessToken = object.get("access_token").getAsString();
            }

            return response;
        }
    }
}
