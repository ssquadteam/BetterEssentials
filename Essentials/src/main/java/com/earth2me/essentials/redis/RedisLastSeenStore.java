package com.earth2me.essentials.redis;

import com.earth2me.essentials.User;
import io.lettuce.core.ScriptOutputType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RedisLastSeenStore implements AutoCloseable {
    private static final String SCRIPT =
            "local user_key = KEYS[1]\n" +
            "local name_key = KEYS[2]\n" +
            "local names_key = KEYS[3]\n" +
            "local incoming = tonumber(ARGV[5])\n" +
            "local current = tonumber(redis.call('HGET', user_key, 'lastLogin') or '0')\n" +
            "if incoming >= current then\n" +
            "  redis.call('HSET', user_key,\n" +
            "    'uuid', ARGV[1],\n" +
            "    'name', ARGV[2],\n" +
            "    'nameLower', ARGV[3],\n" +
            "    'displayName', ARGV[4],\n" +
            "    'lastLogin', ARGV[5],\n" +
            "    'server', ARGV[6])\n" +
            "  redis.call('SET', name_key, ARGV[1])\n" +
            "  redis.call('SADD', names_key, ARGV[3])\n" +
            "  return 1\n" +
            "end\n" +
            "return 0\n";

    private static final String USER_NAMESPACE = "lastseen";
    private static final String NAME_NAMESPACE = "lastseen-name";
    private static final String NAMES_KEY = "lastseen-names";

    private final RedisManager redis;

    public RedisLastSeenStore(final RedisManager redis) {
        this.redis = redis;
    }

    public CompletableFuture<Void> recordLogin(final User user, final long lastLogin) {
        return recordLogin(user, lastLogin, user.getDisplayName());
    }

    public CompletableFuture<Void> recordLogin(final User user, final long lastLogin, final String displayName) {
        final UUID uuid = user.getUUID();
        final String name = user.getName();
        final String nameLower = normalizeName(name);
        final String[] keys = new String[]{redis.key(USER_NAMESPACE, uuid.toString()), redis.key(NAME_NAMESPACE, nameLower), redis.key(NAMES_KEY)};

        return redis.async().eval(SCRIPT, ScriptOutputType.INTEGER, keys,
                uuid.toString(),
                name,
                nameLower,
                displayName == null ? name : displayName,
                Long.toString(lastLogin),
                redis.getServerId()).toCompletableFuture().thenApply(ignored -> null);
    }

    public CompletableFuture<LastSeenResult> lookupByName(final String name) {
        final String nameLower = normalizeName(name);
        return redis.async().get(redis.key(NAME_NAMESPACE, nameLower)).toCompletableFuture()
                .thenCompose(uuid -> {
                    if (isBlank(uuid)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return lookupByUuid(uuid);
                });
    }

    private CompletableFuture<LastSeenResult> lookupByUuid(final String uuid) {
        return redis.async().hgetall(redis.key(USER_NAMESPACE, uuid)).toCompletableFuture()
                .thenApply(this::parseResult);
    }

    private LastSeenResult parseResult(final Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        final Map<String, String> copy = new HashMap<>(values);
        final String name = copy.get("name");
        final long lastLogin = parseLong(copy.get("lastLogin"));
        if (isBlank(name) || lastLogin <= 0) {
            return null;
        }

        return new LastSeenResult(name, copy.get("displayName"), copy.get("server"), lastLogin);
    }

    private static long parseLong(final String value) {
        if (isBlank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String normalizeName(final String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ENGLISH);
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public void close() {
    }
}
