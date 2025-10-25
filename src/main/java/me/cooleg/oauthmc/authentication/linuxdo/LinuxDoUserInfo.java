package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.annotations.SerializedName;

public class LinuxDoUserInfo {
    @SerializedName("id")
    public String id;
    
    @SerializedName("username")
    public String username;
    
    @SerializedName("name")
    public String name;
    
    @SerializedName("email")
    public String email;
    
    @SerializedName("trust_level")
    public int trustLevel;
    
    @SerializedName("active")
    public boolean active;
    
    @SerializedName("silenced")
    public boolean silenced;
    
    @Override
    public String toString() {
        return String.format(
            "LinuxDoUserInfo{id='%s', username='%s', name='%s', email='%s', trustLevel=%d, active=%b, silenced=%b}",
            id, username, name, email, trustLevel, active, silenced
        );
    }
}
