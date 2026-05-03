package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.earth2me.essentials.redis.ShoutLimitResult;
import com.earth2me.essentials.redis.ShoutLimitStore;
import com.earth2me.essentials.utils.DateUtil;
import org.bukkit.Server;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;

public class Commandshout extends EssentialsCommand {
    private static final int MEDIA_SHOUT_LIMIT = 3;
    private static final long MEDIA_SHOUT_WINDOW_MILLIS = Duration.ofHours(24).toMillis();
    private static final String BROADCAST_PERMISSION = "essentials.broadcast";
    private static final String BYPASS_PERMISSION = "essentials.shout.cooldown.bypass";

    public Commandshout() {
        super("shout");
    }

    @Override
    public void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        if (args.length < 1) {
            throw new NotEnoughArgumentsException();
        }

        final String message = getFinalArg(args, 0);
        final String displayName = user.getDisplayName();
        if (user.isAuthorized(BROADCAST_PERMISSION) || user.isAuthorized(BYPASS_PERMISSION)) {
            Commandbroadcast.broadcast(ess, message, displayName);
            return;
        }

        final Essentials plugin = (Essentials) ess;
        final ShoutLimitStore store = plugin.getShoutLimitStore();
        final long now = System.currentTimeMillis();
        if (store == null) {
            consumeLocalAndBroadcast(user, message, displayName, now);
            return;
        }

        store.tryConsume(user.getUUID(), now, MEDIA_SHOUT_LIMIT, MEDIA_SHOUT_WINDOW_MILLIS)
                .thenAccept(result -> handleRedisResult(plugin, user, message, displayName, now, result))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to check Redis /shout limit for " + user.getName() + ": " + unwrap(ex).getMessage());
                    plugin.scheduleEntityDelayedTask(user.getBase(), () -> consumeLocalAndBroadcast(user, message, displayName, now));
                    return null;
                });
    }

    @Override
    public void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        if (args.length < 1) {
            throw new NotEnoughArgumentsException();
        }

        Commandbroadcast.broadcast(ess, getFinalArg(args, 0), sender.getDisplayName());
    }

    private void handleRedisResult(final Essentials plugin, final User user, final String message, final String displayName, final long now, final ShoutLimitResult result) {
        plugin.scheduleEntityDelayedTask(user.getBase(), () -> {
            if (!user.isReachable()) {
                return;
            }

            if (!result.isAllowed()) {
                user.sendTl("commandCooldown", DateUtil.formatDateDiff(result.getResetAt()));
                return;
            }

            user.addMediaShoutTimestamp(now);
            scheduleBroadcast(message, displayName);
        });
    }

    private void consumeLocalAndBroadcast(final User user, final String message, final String displayName, final long now) {
        if (isLocallyLimited(user)) {
            return;
        }

        user.addMediaShoutTimestamp(now);
        scheduleBroadcast(message, displayName);
    }

    private void scheduleBroadcast(final String message, final String displayName) {
        ess.scheduleGlobalDelayedTask(() -> Commandbroadcast.broadcast(ess, message, displayName));
    }

    private boolean isLocallyLimited(final User user) {
        final long now = System.currentTimeMillis();
        final long windowStart = now - MEDIA_SHOUT_WINDOW_MILLIS;
        final List<Long> timestamps = user.getMediaShoutTimestamps();
        final boolean removedExpired = timestamps.removeIf(timestamp -> timestamp == null || timestamp <= windowStart);

        if (timestamps.size() < MEDIA_SHOUT_LIMIT) {
            if (removedExpired) {
                user.setMediaShoutTimestamps(timestamps);
            }
            return false;
        }

        final long resetAt = timestamps.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(now) + MEDIA_SHOUT_WINDOW_MILLIS;
        if (removedExpired) {
            user.setMediaShoutTimestamps(timestamps);
        }
        user.sendTl("commandCooldown", DateUtil.formatDateDiff(resetAt));
        return true;
    }

    private Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
