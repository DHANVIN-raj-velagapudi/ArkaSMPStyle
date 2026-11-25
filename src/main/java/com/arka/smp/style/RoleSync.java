package com.arka.smp.style;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Makes LuckPerms match the "roles" section of the config: creates missing groups, sets weights and parents, puts
 * allowed permissions on, sets denied ones to false and takes listed ones off. It only touches what the config lists
 * (global context, not temporary): everything else on a group is left alone, and groups are never deleted.
 * Only class (with {@link RankGrants}) that uses the LuckPerms API.
 */
final class RoleSync {
    /** What would change (preview) or did change (apply). {@code lines} is short, {@code full} lists every change. */
    record Report(List<String> lines, String full, int changes, List<String> errors, File backup) {
    }

    private record Change(String kind, String text) {
    }

    private final ArkaSMPStyle plugin;

    RoleSync(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    private static LuckPerms lp() {
        RegisteredServiceProvider<LuckPerms> rsp = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (rsp == null) throw new IllegalStateException("LuckPerms is not ready");
        return rsp.getProvider();
    }

    // ---- what a group has now ------------------------------------------------------------------

    private static final class View {
        final Map<String, Node> perms = new HashMap<>();
        final List<Node> weights = new ArrayList<>();
        final Map<String, Node> parents = new HashMap<>();

        static View of(Group g) {
            View v = new View();
            if (g == null) return v;
            for (Node n : g.getNodes()) {
                if (!n.getContexts().isEmpty() || n.hasExpiry()) continue;   // only plain, permanent, global nodes
                if (NodeType.WEIGHT.matches(n)) v.weights.add(n);
                else if (NodeType.INHERITANCE.matches(n)) {
                    v.parents.put(NodeType.INHERITANCE.cast(n).getGroupName().toLowerCase(Locale.ROOT), n);
                } else if (NodeType.PERMISSION.matches(n)) v.perms.put(n.getKey().toLowerCase(Locale.ROOT), n);
            }
            return v;
        }
    }

    /** The changes needed to make {@code g} true; when {@code target} is given they are also made. */
    private static List<Change> diff(View v, RolePlan.Group g, Group target) {
        List<Change> out = new ArrayList<>();
        if (g.weight() != null) {
            boolean same = v.weights.size() == 1 && NodeType.WEIGHT.cast(v.weights.get(0)).getWeight() == g.weight();
            if (!same) {
                out.add(new Change("weight", "weight " + g.weight()));
                if (target != null) {
                    for (Node w : v.weights) target.data().remove(w);
                    target.data().add(WeightNode.builder(g.weight()).build());
                }
            }
        }
        for (String p : g.parents()) {
            if (v.parents.containsKey(p)) continue;
            out.add(new Change("parent", "parent +" + p));
            if (target != null) target.data().add(InheritanceNode.builder(p).build());
        }
        for (String n : g.allow()) {
            Node have = v.perms.get(n);
            if (have != null && have.getValue()) continue;
            out.add(new Change("allow", "allow +" + n));
            if (target != null) {
                if (have != null) target.data().remove(have);
                target.data().add(Node.builder(n).value(true).build());
            }
        }
        for (String n : g.deny()) {
            Node have = v.perms.get(n);
            if (have != null && !have.getValue()) continue;
            out.add(new Change("deny", "deny +" + n));
            if (target != null) {
                if (have != null) target.data().remove(have);
                target.data().add(Node.builder(n).value(false).build());
            }
        }
        for (String n : g.remove()) {
            Node have = v.perms.get(n);
            if (have == null) continue;
            out.add(new Change("remove", "remove -" + n));
            if (target != null) target.data().remove(have);
        }
        return out;
    }

    /** "+6 allow, +35 deny" style short text. */
    private static String compact(List<Change> changes) {
        if (changes.isEmpty()) return "nothing to change";
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Change c : changes) counts.merge(c.kind(), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        for (var e : counts.entrySet()) {
            String k = e.getKey();
            if (k.equals("weight")) parts.add("weight");
            else parts.add((k.equals("remove") ? "-" : "+") + e.getValue() + " " + k);
        }
        return String.join(", ", parts);
    }

    private List<String> externalParentErrors(RolePlan plan, LuckPerms lp) {
        List<String> errors = new ArrayList<>();
        for (String p : plan.externalParents()) {
            if (lp.getGroupManager().getGroup(p) == null) {
                errors.add("Parent group '" + p + "' is not in 'roles' and does not exist in LuckPerms.");
            }
        }
        return errors;
    }

    // ---- preview: changes nothing --------------------------------------------------------------

    Report preview(RolePlan plan) {
        LuckPerms lp = lp();
        List<String> errors = externalParentErrors(plan, lp);
        List<String> lines = new ArrayList<>();
        StringBuilder full = new StringBuilder();
        int total = 0;
        for (RolePlan.Group g : plan.groups()) {
            Group existing = lp.getGroupManager().getGroup(g.name());
            List<Change> changes = diff(View.of(existing), g, null);
            if (existing == null) changes.add(0, new Change("create", "create group"));
            total += changes.size();
            lines.add(g.name() + ": " + (existing == null ? "create group, " : "") + compact(
                    changes.stream().filter(c -> !c.kind().equals("create")).toList()));
            full.append(g.name()).append(existing == null ? " (new group)" : "").append('\n');
            for (Change c : changes) full.append("  ").append(c.text()).append('\n');
        }
        return new Report(lines, full.toString(), total, errors, null);
    }

    // ---- apply ----------------------------------------------------------------------------------

    CompletableFuture<Report> apply(RolePlan plan) {
        LuckPerms lp = lp();
        List<String> errors = externalParentErrors(plan, lp);
        if (!errors.isEmpty()) return CompletableFuture.completedFuture(new Report(List.of(), "", 0, errors, null));
        File backup;
        try {
            backup = snapshot(plan, lp);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(new Report(List.of(), "", 0,
                    List.of("Could not save the backup of the groups (" + e.getMessage() + "). Nothing was changed."), null));
        }
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        StringBuffer full = new StringBuffer();
        int[] total = {0};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (RolePlan.Group g : plan.groups()) {
            chain = chain.thenCompose(v -> {
                boolean existed = lp.getGroupManager().getGroup(g.name()) != null;
                return lp.getGroupManager().createAndLoadGroup(g.name())
                        .thenCompose(created -> lp.getGroupManager().modifyGroup(g.name(), group -> {
                            List<Change> changes = diff(View.of(group), g, group);
                            synchronized (total) {
                                total[0] += changes.size() + (existed ? 0 : 1);
                            }
                            lines.add(g.name() + ": " + (existed ? "" : "created, ") + compact(changes));
                            full.append(g.name()).append(existed ? "" : " (created)").append('\n');
                            for (Change c : changes) full.append("  ").append(c.text()).append('\n');
                        }));
            });
        }
        File saved = backup;
        return chain.thenApply(v -> new Report(lines, full.toString(), total[0], List.of(), saved));
    }

    /** Writes every node of the groups about to change, so a mistake can be put back by hand. */
    private File snapshot(RolePlan plan, LuckPerms lp) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("note", "Nodes each group had BEFORE /style setup apply. 'key=value' (expiry if temporary).");
        for (RolePlan.Group g : plan.groups()) {
            Group existing = lp.getGroupManager().getGroup(g.name());
            if (existing == null) {
                yaml.set("groups." + g.name(), List.of("(group did not exist)"));
                continue;
            }
            List<String> nodes = new ArrayList<>();
            for (Node n : existing.getNodes()) {
                nodes.add(n.getKey() + "=" + n.getValue() + (n.hasExpiry() ? " expiry=" + n.getExpiry() : "")
                        + (n.getContexts().isEmpty() ? "" : " contexts=" + n.getContexts().toMap()));
            }
            yaml.set("groups." + g.name(), nodes);
        }
        File dir = new File(plugin.getDataFolder(), "setup-backups");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File file = new File(dir, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".yml");
        yaml.save(file);
        return file;
    }
}
