package me.cooleg.oauthmc.persistence;

import java.util.UUID;

public interface IDatabaseHook {

    boolean hasLoggedIn(UUID uuid);

    String getEmail(UUID uuid);

    boolean isInUse(String email);

    void setLink(UUID id, String email);


}
