package me.cooleg.oauthmc.authentication.microsoft;

import com.google.gson.*;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;

import java.lang.reflect.Type;

public class MicrosoftDeviceCodeResponse extends CodeAndLinkResponse {

    public String deviceCode;
    public int expiresIn;
    public int interval;



    public static class Deserializer implements JsonDeserializer<MicrosoftDeviceCodeResponse> {

        @Override
        public MicrosoftDeviceCodeResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            MicrosoftDeviceCodeResponse response = new MicrosoftDeviceCodeResponse();
            JsonObject object = json.getAsJsonObject();
            if (object.has("error_code")) throw new RuntimeException("Ratelimited");
            response.deviceCode = object.get("device_code").getAsString();
            response.userCode = object.get("user_code").getAsString();
            response.loginLink = object.get("verification_uri").getAsString();
            response.expiresIn = object.get("expires_in").getAsInt();
            response.interval = object.get("interval").getAsInt();

            return response;
        }

    }


}
