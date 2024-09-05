package me.cooleg.oauthmc.persistence.impl;

import me.cooleg.oauthmc.persistence.IDatabaseHook;

import java.util.UUID;

public class MySQLHook implements IDatabaseHook {
    @Override
    public boolean hasLoggedIn(UUID uuid) {
        return false;
    }

    @Override
    public String getEmail(UUID uuid) {
        return null;
    }

    @Override
    public boolean isInUse(String email) {
        return false;
    }

    @Override
    public void setLink(UUID id, String email) {

    }
}
