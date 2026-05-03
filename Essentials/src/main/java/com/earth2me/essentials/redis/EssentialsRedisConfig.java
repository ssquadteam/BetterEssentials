package com.earth2me.essentials.redis;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class EssentialsRedisConfig {
    private final boolean enabled;
    private final String uri;
    private final String keyPrefix;

    private EssentialsRedisConfig(final boolean enabled, final String uri, final String keyPrefix) {
        this.enabled = enabled;
        this.uri = uri;
        this.keyPrefix = keyPrefix;
    }

    public static EssentialsRedisConfig load(final JavaPlugin plugin) {
        final FileConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        final FileConfiguration credentials = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "credentials.yml"));

        String redisUri = credentials.getString("redis.uri", "");
        if (isBlank(redisUri)) {
            redisUri = config.getString("redis.uri", "");
        }

        return new EssentialsRedisConfig(
                config.getBoolean("redis.enabled", true),
                trim(redisUri),
                nonBlank(config.getString("redis.key-prefix"), "essentials:"));
    }

    public boolean isAvailable() {
        return enabled && !isBlank(uri);
    }

    String getUri() {
        return uri;
    }

    String getKeyPrefix() {
        return keyPrefix;
    }

    private static String nonBlank(final String value, final String fallback) {
        final String trimmed = trim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String trim(final String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
