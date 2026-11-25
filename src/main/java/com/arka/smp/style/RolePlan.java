package com.arka.smp.style;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "roles" section of config.yml, read and checked. Plain Java (no LuckPerms), so every rule can be tested without a
 * server. If {@link #errors()} is not empty nothing may be applied.
 */
final class RolePlan {
    /** One LuckPerms group as the config wants it. {@code weight} is null when the config does not set one. */
    record Group(String name, Integer weight, List<String> parents, List<String> allow, List<String> deny,
                 List<String> remove) {
    }

    private final List<Group> groups;   // parents first
    private final List<String> errors;

    private RolePlan(List<Group> groups, List<String> errors) {
        this.groups = groups;
        this.errors = errors;
    }

    List<Group> groups() {
        return groups;
    }

    List<String> errors() {
        return errors;
    }

    static RolePlan parse(ConfigurationSection roles) {
        List<String> errors = new ArrayList<>();
        Map<String, Group> byName = new LinkedHashMap<>();
        if (roles == null) {
            errors.add("There is no 'roles' section in config.yml.");
            return new RolePlan(List.of(), errors);
        }
        for (String key : roles.getKeys(false)) {
            String name = key.toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_\\-]+")) {
                errors.add("roles." + key + ": the group name may only use letters, numbers, - and _.");
                continue;
            }
            ConfigurationSection s = roles.getConfigurationSection(key);
            if (s == null) {
                errors.add("roles." + key + ": must be a section (use {} for an empty one).");
                continue;
            }
            Integer weight = null;
            if (s.contains("weight")) {
                if (!s.isInt("weight") || s.getInt("weight") < 0 || s.getInt("weight") > 10000) {
                    errors.add("roles." + key + ".weight: must be a whole number from 0 to 10000.");
                } else {
                    weight = s.getInt("weight");
                }
            }
            List<String> parents = new ArrayList<>();
            for (String p : s.getStringList("parents")) {
                String pn = p.trim().toLowerCase(Locale.ROOT);
                if (pn.equals(name)) errors.add("roles." + key + ".parents: a group cannot be its own parent.");
                else if (!pn.matches("[a-z0-9_\\-]+")) errors.add("roles." + key + ".parents: '" + p + "' is not a group name.");
                else if (!parents.contains(pn)) parents.add(pn);
            }
            List<String> allow = RoleGuard.expand(s.getStringList("allow"));
            List<String> deny = RoleGuard.expand(s.getStringList("deny"));
            List<String> remove = RoleGuard.expand(s.getStringList("remove"));
            for (String n : allow) {
                String bad = RoleGuard.badSyntax(n);
                if (bad == null) bad = RoleGuard.forbiddenToAllow(n);
                if (bad != null) errors.add("roles." + key + ".allow: '" + n + "' " + bad + ".");
                if (deny.contains(n)) errors.add("roles." + key + ": '" + n + "' is in both allow and deny.");
            }
            for (String n : deny) {
                String bad = RoleGuard.badSyntax(n);
                if (bad != null) errors.add("roles." + key + ".deny: '" + n + "' " + bad + ".");
            }
            for (String n : remove) {
                String bad = RoleGuard.badSyntax(n);
                if (bad != null) errors.add("roles." + key + ".remove: '" + n + "' " + bad + ".");
                if (allow.contains(n) || deny.contains(n)) {
                    errors.add("roles." + key + ": '" + n + "' is in remove and also in allow or deny.");
                }
            }
            byName.put(name, new Group(name, weight, List.copyOf(parents), List.copyOf(allow), List.copyOf(deny),
                    List.copyOf(remove)));
        }
        // parents first; a parent that is not in the section must already exist in LuckPerms (checked when it runs)
        List<Group> ordered = new ArrayList<>();
        Map<String, Integer> state = new LinkedHashMap<>();   // 1 = visiting, 2 = done
        for (String name : byName.keySet()) visit(name, byName, state, ordered, errors, new ArrayList<>());
        return new RolePlan(List.copyOf(ordered), errors);
    }

    private static void visit(String name, Map<String, Group> byName, Map<String, Integer> state, List<Group> out,
                              List<String> errors, List<String> path) {
        Integer st = state.get(name);
        if (st != null && st == 2) return;
        if (st != null && st == 1) {
            path.add(name);
            errors.add("Parents form a loop: " + String.join(" -> ", path) + ".");
            return;
        }
        state.put(name, 1);
        path.add(name);
        Group g = byName.get(name);
        for (String p : g.parents()) {
            if (byName.containsKey(p)) visit(p, byName, state, out, errors, path);
        }
        path.remove(path.size() - 1);
        state.put(name, 2);
        if (!out.contains(g)) out.add(g);
    }

    /** Parents that are not defined in the section: they must already exist in LuckPerms. */
    List<String> externalParents() {
        List<String> names = new ArrayList<>();
        for (Group g : groups) names.add(g.name());
        List<String> out = new ArrayList<>();
        for (Group g : groups) {
            for (String p : g.parents()) if (!names.contains(p) && !out.contains(p)) out.add(p);
        }
        return out;
    }
}
