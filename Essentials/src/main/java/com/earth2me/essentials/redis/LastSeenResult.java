package com.earth2me.essentials.redis;

public final class LastSeenResult {
    private final String name;
    private final String displayName;
    private final String serverId;
    private final long lastLogin;

    public LastSeenResult(final String name, final String displayName, final String serverId, final long lastLogin) {
        this.name = name;
        this.displayName = displayName;
        this.serverId = serverId;
        this.lastLogin = lastLogin;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName == null || displayName.trim().isEmpty() ? name : displayName;
    }

    public String getServerId() {
        return serverId;
    }

    public long getLastLogin() {
        return lastLogin;
    }
}
