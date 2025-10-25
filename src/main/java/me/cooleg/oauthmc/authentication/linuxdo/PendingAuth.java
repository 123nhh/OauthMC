package me.cooleg.oauthmc.authentication.linuxdo;

import java.util.UUID;

public class PendingAuth {
    
    private final UUID playerUuid;
    private final long createdAt;
    private final long expiresAt;
    private boolean authorized;
    private LinuxDoUserInfo userInfo;
    
    public PendingAuth(UUID playerUuid, long expiryMinutes) {
        this.playerUuid = playerUuid;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + (expiryMinutes * 60 * 1000);
        this.authorized = false;
    }
    
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
    
    public boolean isAuthorized() {
        return authorized;
    }
    
    public void setAuthorized(LinuxDoUserInfo userInfo) {
        this.authorized = true;
        this.userInfo = userInfo;
    }
    
    public LinuxDoUserInfo getUserInfo() {
        return userInfo;
    }
}
