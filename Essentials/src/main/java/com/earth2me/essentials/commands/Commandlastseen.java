package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.earth2me.essentials.adventure.AdventureUtil;
import com.earth2me.essentials.redis.LastSeenResult;
import com.earth2me.essentials.redis.RedisLastSeenStore;
import com.earth2me.essentials.utils.DateUtil;
import org.bukkit.Server;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

public class Commandlastseen extends EssentialsCommand {
    public Commandlastseen() {
        super("lastseen");
    }

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        if (args.length < 1) {
            throw new NotEnoughArgumentsException();
        }

        final Essentials plugin = (Essentials) ess;
        final String serverName = server.getName();
        final RedisLastSeenStore store = plugin.getLastSeenStore();
        if (store == null) {
            sendLocalResultAsync(serverName, sender, args[0]);
            return;
        }

        store.lookupByName(args[0]).thenAccept(result -> scheduleSender(sender, () -> {
            if (result != null) {
                sendRedisResult(sender, result);
            } else {
                sendLocalResultAsync(serverName, sender, args[0]);
            }
        })).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to query Redis /lastseen for " + args[0] + ": " + unwrap(ex).getMessage());
            sendLocalResultAsync(serverName, sender, args[0]);
            return null;
        });
    }

    private void sendRedisResult(final CommandSource sender, final LastSeenResult result) {
        sender.sendTl("lastseenInfo",
                AdventureUtil.parsed(ess.getAdventureFacet().legacyToMini(result.getDisplayName())),
                DateUtil.formatDateDiff(result.getLastLogin()),
                result.getServerId() == null || result.getServerId().trim().isEmpty() ? "unknown" : result.getServerId());
    }

    private void sendLocalResultAsync(final String serverName, final CommandSource sender, final String searchTerm) {
        ess.runTaskAsynchronously(() -> {
            final LastSeenResult result = getLocalResult(serverName, searchTerm);
            scheduleSender(sender, () -> {
                if (result == null) {
                    sender.sendTl("lastseenUnknown", searchTerm);
                    return;
                }

                sendRedisResult(sender, result);
            });
        });
    }

    private LastSeenResult getLocalResult(final String serverName, final String searchTerm) {
        final User user = ess.getOfflineUser(searchTerm);
        if (user == null || user.getLastLogin() <= 0) {
            return null;
        }

        return new LastSeenResult(user.getName(), user.getName(), serverName, user.getLastLogin());
    }

    private void scheduleSender(final CommandSource sender, final Runnable runnable) {
        final Essentials plugin = (Essentials) ess;
        if (sender.isPlayer()) {
            plugin.scheduleEntityDelayedTask(sender.getPlayer(), runnable);
        } else {
            plugin.scheduleGlobalDelayedTask(runnable);
        }
    }

    private Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final CommandSource sender, final String commandLabel, final String[] args) {
        if (args.length == 1) {
            return getPlayers(sender);
        }
        return Collections.emptyList();
    }
}
