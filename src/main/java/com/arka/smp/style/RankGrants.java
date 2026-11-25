package com.arka.smp.style;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /** One rank for {@link #setup}: its group, the group it inherits from (or null) and the permissions it holds. */
    record SetupRank(String group, String parent, List<String> permissions) {
    }

    /**
     * Creates the missing groups and adds the missing permissions and parents. It never removes or changes anything
     * that is already there. Returns one report line per group.
     */
    CompletableFuture<List<String>> setup(List<SetupRank> ranks) {
        LuckPerms lp = lp();
        List<String> report = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (SetupRank r : ranks) {
            chain = chain.thenCompose(v -> {
                boolean existed = lp.getGroupManager().getGroup(r.group()) != null;
                return lp.getGroupManager().createAndLoadGroup(r.group())
                        .thenCompose(g -> lp.getGroupManager().modifyGroup(r.group(), group -> {
                            int added = 0;
                            if (r.parent() != null
                                    && group.data().add(InheritanceNode.builder(r.parent()).build()).wasSuccessful()) added++;
                            for (String p : r.permissions()) {
                                if (group.data().add(Node.builder(p).build()).wasSuccessful()) added++;
                            }
                            report.add(r.group() + ": " + (existed ? "already existed" : "created") + ", " + added
                                    + " permission(s) added");
                        }));
            });
        }
        return chain.thenApply(v -> report);
    }

    private static LuckPerms lp() {
        RegisteredServiceProvider<LuckPerms> rsp = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (rsp == null) throw new IllegalStateException("LuckPerms is not ready");
        return rsp.getProvider();
    }
}
