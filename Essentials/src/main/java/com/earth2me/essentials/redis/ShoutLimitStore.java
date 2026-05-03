package com.earth2me.essentials.redis;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ShoutLimitStore extends AutoCloseable {
    CompletableFuture<ShoutLimitResult> tryConsume(UUID playerId, long now, int limit, long windowMillis);

    @Override
    void close();
}
