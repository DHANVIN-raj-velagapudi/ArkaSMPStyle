package com.arka.smp.style;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gives and takes the LuckPerms group behind a rank. This is the only class that touches the LuckPerms API, and it
 * is only created when LuckPerms is installed.
 */
final class RankGrants {
    /** Newest expiry of the group on the user after the change, or null for none (taken, or no expiry). */
    record Result(Instant expiry) {
    }

    /** Adds {@code days} to the user's temporary group. Renewing early adds to the time that is left. */
    CompletableFuture<Result> grant(UUID id, String group, int days) {
        LuckPerms lp = lp();
        AtomicReference<Instant> expiry = new AtomicReference<>();
        InheritanceNode node = InheritanceNode.builder(group).expiry(Duration.ofDays(days)).build();
        return lp.getUserManager().modifyUser(id, user -> {
            user.data().add(node, TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING);
            for (InheritanceNode n : user.getNodes(NodeType.INHERITANCE)) {
                if (!n.getGroupName().equalsIgnoreCase(group) || !n.hasExpiry()) continue;
                Instant e = n.getExpiry();
                if (e != null && (expiry.get() == null || e.isAfter(expiry.get()))) expiry.set(e);
            }
        }).thenApply(v -> new Result(expiry.get()));
    }

    /** Gives the group with no expiry (staff ranks). Giving it again changes nothing. */
    CompletableFuture<Result> grantForever(UUID id, String group) {
        InheritanceNode node = InheritanceNode.builder(group).build();
        return lp().getUserManager().modifyUser(id, user -> user.data().add(node)).thenApply(v -> new Result(null));
    }

    /** True when the user is in the group right now (loads the user from LuckPerms, also when offline). */
    CompletableFuture<Boolean> hasGroup(UUID id, String group) {
        return lp().getUserManager().loadUser(id).thenApply(user -> user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(n -> n.getGroupName().equalsIgnoreCase(group) && !n.hasExpired()));
    }

    /** True when the user holds any of these permissions right now (loads the user from LuckPerms, also when offline). */
    CompletableFuture<Boolean> hasAnyPermission(UUID id, String... nodes) {
        return lp().getUserManager().loadUser(id).thenApply(user -> {
            var data = user.getCachedData().getPermissionData();
            for (String n : nodes) {
                if (data.checkPermission(n).asBoolean()) return true;
            }
            return false;
        });
    }

    /** Removes the group from the user completely (temporary and permanent). */
    CompletableFuture<Result> take(UUID id, String group) {
        return lp().getUserManager().modifyUser(id, user -> user.data().clear(
                n -> n instanceof InheritanceNode g && g.getGroupName().equalsIgnoreCase(group)))
                .thenApply(v -> new Result(null));
    }

    /** True when LuckPerms has a group with this name. Giving a group that does not exist would do nothing. */
    boolean groupExists(String group) {
        return lp().getGroupManager().getGroup(group) != null;
    }

    private static LuckPerms lp() {
        RegisteredServiceProvider<LuckPerms> rsp = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (rsp == null) throw new IllegalStateException("LuckPerms is not ready");
        return rsp.getProvider();
    }
}
