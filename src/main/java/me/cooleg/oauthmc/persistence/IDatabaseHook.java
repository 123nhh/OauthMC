package me.cooleg.oauthmc.persistence;

import java.util.UUID;

public interface IDatabaseHook {

    boolean hasLoggedIn(UUID uuid);

    String getEmail(UUID uuid);

    boolean isInUse(String email);

    void setLink(UUID id, String email);

    // LinuxDo OAuth specific methods
    void setLinuxDoBinding(UUID uuid, String linuxdoId, String linuxdoUsername);

    String getLinuxDoId(UUID uuid);

    String getLinuxDoUsername(UUID uuid);

    void updateLastAuthTime(UUID uuid);

    long getLastAuthTime(UUID uuid);

    void removeBinding(UUID uuid);

}
