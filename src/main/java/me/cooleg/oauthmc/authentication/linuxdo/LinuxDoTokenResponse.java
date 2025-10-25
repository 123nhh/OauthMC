package me.cooleg.oauthmc.authentication.linuxdo;

import com.google.gson.annotations.SerializedName;

public class LinuxDoTokenResponse {
    
    @SerializedName("access_token")
    public String accessToken;
    
    @SerializedName("token_type")
    public String tokenType;
    
    @SerializedName("expires_in")
    public int expiresIn;
    
    @SerializedName("refresh_token")
    public String refreshToken;
    
    @SerializedName("scope")
    public String scope;
}
