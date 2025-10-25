package me.cooleg.oauthmc.authentication.linuxdo;

public class AuthResult {
    
    public enum Status {
        PENDING,
        SUCCESS,
        EXPIRED,
        INVALID
    }
    
    private final Status status;
    private final LinuxDoUserInfo userInfo;
    
    private AuthResult(Status status, LinuxDoUserInfo userInfo) {
        this.status = status;
        this.userInfo = userInfo;
    }
    
    public static AuthResult pending() {
        return new AuthResult(Status.PENDING, null);
    }
    
    public static AuthResult success(LinuxDoUserInfo userInfo) {
        return new AuthResult(Status.SUCCESS, userInfo);
    }
    
    public static AuthResult expired() {
        return new AuthResult(Status.EXPIRED, null);
    }
    
    public static AuthResult invalid() {
        return new AuthResult(Status.INVALID, null);
    }
    
    public Status getStatus() {
        return status;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isExpired() {
        return status == Status.EXPIRED;
    }
    
    public boolean isPending() {
        return status == Status.PENDING;
    }
    
    public LinuxDoUserInfo getUserInfo() {
        return userInfo;
    }
}
