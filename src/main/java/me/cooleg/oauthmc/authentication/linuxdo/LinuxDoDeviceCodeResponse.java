package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.*;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;

import java.lang.reflect.Type;

public class LinuxDoDeviceCodeResponse extends CodeAndLinkResponse {

    public String deviceCode;
    public int interval;
    public int expiresIn;

    public static class Deserializer implements JsonDeserializer<LinuxDoDeviceCodeResponse> {

        @Override
        public LinuxDoDeviceCodeResponse deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject object = jsonElement.getAsJsonObject();
            LinuxDoDeviceCodeResponse response = new LinuxDoDeviceCodeResponse();

            response.deviceCode = object.get("device_code").getAsString();
            response.userCode = object.get("user_code").getAsString();
            response.loginLink = object.get("verification_uri").getAsString();
            response.interval = object.get("interval").getAsInt();
            response.expiresIn = object.get("expires_in").getAsInt();

            return response;
        }
    }
}
