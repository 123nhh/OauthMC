package me.cooleg.oauthmc.authentication.google;

import com.google.gson.*;
import me.cooleg.oauthmc.authentication.CodeAndLinkResponse;

import java.lang.reflect.Type;

public class GoogleDeviceCodeResponse extends CodeAndLinkResponse {

    public String deviceCode;
    public int expiresIn;
    public int interval;



    public static class Deserializer implements JsonDeserializer<GoogleDeviceCodeResponse> {

        @Override
        public GoogleDeviceCodeResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            GoogleDeviceCodeResponse response = new GoogleDeviceCodeResponse();
            JsonObject object = json.getAsJsonObject();
            if (object.has("error_code")) throw new RuntimeException("Ratelimited");
            response.deviceCode = object.get("device_code").getAsString();
            response.userCode = object.get("user_code").getAsString();
            response.loginLink = object.get("verification_url").getAsString();
            response.expiresIn = object.get("expires_in").getAsInt();
            response.interval = object.get("interval").getAsInt();

            return response;
        }

    }


}
