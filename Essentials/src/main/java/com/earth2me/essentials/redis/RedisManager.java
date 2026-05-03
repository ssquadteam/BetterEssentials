package com.earth2me.essentials.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

public final class RedisManager implements AutoCloseable {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final String keyPrefix;

    public RedisManager(final EssentialsRedisConfig config) {
        this.client = RedisClient.create(config.getUri());
        this.connection = client.connect();
        this.keyPrefix = config.getKeyPrefix();
    }

    public RedisAsyncCommands<String, String> async() {
        return connection.async();
    }

    public String key(final String namespace, final String id) {
        return keyPrefix + namespace + ":" + id;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } finally {
            client.shutdown();
        }
    }
}
