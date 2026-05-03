package com.earth2me.essentials.redis;

public final class ShoutLimitResult {
    private final boolean allowed;
    private final long resetAt;

    private ShoutLimitResult(final boolean allowed, final long resetAt) {
        this.allowed = allowed;
        this.resetAt = resetAt;
    }

    public static ShoutLimitResult allowed() {
        return new ShoutLimitResult(true, 0L);
    }

    public static ShoutLimitResult limited(final long resetAt) {
        return new ShoutLimitResult(false, resetAt);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getResetAt() {
        return resetAt;
    }
}
