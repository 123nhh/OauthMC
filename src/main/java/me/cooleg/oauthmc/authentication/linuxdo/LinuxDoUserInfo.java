package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.*;

import java.lang.reflect.Type;

public class LinuxDoUserInfo {

    public int id;
    public String username;
    public String name;
    public String avatarTemplate;
    public boolean active;
    public int trustLevel;
    public boolean silenced;

    public static class Deserializer implements JsonDeserializer<LinuxDoUserInfo> {

        @Override
        public LinuxDoUserInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            LinuxDoUserInfo userInfo = new LinuxDoUserInfo();
            JsonObject object = json.getAsJsonObject();

            userInfo.id = object.get("id").getAsInt();
            userInfo.username = object.get("username").getAsString();

            if (object.has("name") && !object.get("name").isJsonNull()) {
                userInfo.name = object.get("name").getAsString();
            } else {
                userInfo.name = userInfo.username;
            }

            if (object.has("avatar_template") && !object.get("avatar_template").isJsonNull()) {
                userInfo.avatarTemplate = object.get("avatar_template").getAsString();
            }

            userInfo.active = object.has("active") && object.get("active").getAsBoolean();
            userInfo.trustLevel = object.has("trust_level") ? object.get("trust_level").getAsInt() : 0;
            userInfo.silenced = object.has("silenced") && object.get("silenced").getAsBoolean();

            return userInfo;
        }
    }

}
