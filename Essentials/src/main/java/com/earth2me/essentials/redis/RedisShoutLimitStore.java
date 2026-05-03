package com.earth2me.essentials.redis;

import io.lettuce.core.ScriptOutputType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class RedisShoutLimitStore implements ShoutLimitStore {
    private static final String SCRIPT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window_start = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local window = tonumber(ARGV[4])\n" +
            "local member = ARGV[5]\n" +
            "redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count >= limit then\n" +
            "  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')\n" +
            "  local reset = now + window\n" +
            "  if oldest[2] then reset = tonumber(oldest[2]) + window end\n" +
            "  redis.call('PEXPIRE', key, window + 60000)\n" +
            "  return {0, reset}\n" +
            "end\n" +
            "redis.call('ZADD', key, now, member)\n" +
            "redis.call('PEXPIRE', key, window + 60000)\n" +
            "return {1, 0}\n";

    private static final String KEY_NAMESPACE = "shout";
    private final RedisManager redis;

    public RedisShoutLimitStore(final RedisManager redis) {
        this.redis = redis;
    }

    @Override
    public CompletableFuture<ShoutLimitResult> tryConsume(final UUID playerId, final long now, final int limit, final long windowMillis) {
        final String key = redis.key(KEY_NAMESPACE, playerId.toString());
        final String member = now + ":" + ThreadLocalRandom.current().nextLong();
        final String[] keys = new String[]{key};
        return redis.async().eval(SCRIPT, ScriptOutputType.MULTI, keys,
                Long.toString(now),
                Long.toString(now - windowMillis),
                Integer.toString(limit),
                Long.toString(windowMillis),
                member).toCompletableFuture().thenApply(RedisShoutLimitStore::parseResult);
    }

    private static ShoutLimitResult parseResult(final Object raw) {
        if (!(raw instanceof List)) {
            throw new IllegalStateException("Unexpected Redis shout limiter response: " + raw);
        }

        final List<?> values = (List<?>) raw;
        if (values.isEmpty() || asLong(values.get(0)) != 1L) {
            final long resetAt = values.size() > 1 ? asLong(values.get(1)) : System.currentTimeMillis();
            return ShoutLimitResult.limited(resetAt);
        }
        return ShoutLimitResult.allowed();
    }

    private static long asLong(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Override
    public void close() {
    }
}
