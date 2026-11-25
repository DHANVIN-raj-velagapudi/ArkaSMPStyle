package com.arka.smp.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Permissions that no group may ever be given by /style setup, whatever the config says. They are the ones that
 * can end the server, wipe the world, create items or money from nothing, or let an account give itself everything.
 * They stay on the console. The same list is what {@code @console-only} in a deny list expands to.
 */
final class RoleGuard {
    private RoleGuard() {
    }

    /** The token that stands for {@link #CONSOLE_ONLY} inside a deny list. */
    static final String CONSOLE_ONLY_TOKEN = "@console-only";

    static final List<String> CONSOLE_ONLY = List.of(
            // vanilla commands that destroy, create or rewrite things
            "minecraft.command.op", "minecraft.command.deop", "minecraft.command.stop", "minecraft.command.reload",
            "minecraft.command.save-off", "minecraft.command.execute", "minecraft.command.function",
            "minecraft.command.data", "minecraft.command.datapack", "minecraft.command.forceload",
            "minecraft.command.fill", "minecraft.command.clone", "minecraft.command.setblock",
            "minecraft.command.summon", "minecraft.command.kill", "minecraft.command.give", "minecraft.command.loot",
            "minecraft.command.item", "minecraft.command.attribute", "minecraft.command.effect",
            "minecraft.command.xp", "minecraft.command.experience", "minecraft.command.gamerule",
            "minecraft.command.difficulty", "minecraft.command.defaultgamemode", "minecraft.command.worldborder",
            "minecraft.command.setworldspawn", "minecraft.command.publish", "minecraft.command.debug",
            // Bukkit / Paper
            "bukkit.command.op.give", "bukkit.command.op.take", "bukkit.command.reload", "bukkit.command.restart",
            "bukkit.command.stop",
            // our own: the Essentials import overwrites balances
            "arkaeco.admin.import",
            // LuckPerms: whoever can do these can give themselves everything
            "luckperms.user.permission.set", "luckperms.user.permission.unset", "luckperms.user.permission.settemp",
            "luckperms.user.permission.unsettemp", "luckperms.user.permission.clear", "luckperms.user.clear",
            "luckperms.group.permission.set", "luckperms.group.permission.unset", "luckperms.group.permission.settemp",
            "luckperms.group.permission.unsettemp", "luckperms.group.permission.clear", "luckperms.group.clear",
            "luckperms.group.parent.add", "luckperms.group.parent.set", "luckperms.group.parent.remove",
            "luckperms.group.parent.clear", "luckperms.creategroup", "luckperms.deletegroup",
            "luckperms.renamegroup", "luckperms.clonegroup", "luckperms.bulkupdate", "luckperms.import",
            "luckperms.export", "luckperms.editor", "luckperms.applyedits");

    /**
     * Never allowed either, but not put in the deny list: "arkaeco.admin" is an umbrella that includes the Essentials
     * import, and denying it could also switch off the single economy permissions an Admin is given.
     */
    private static final List<String> UMBRELLAS = List.of("arkaeco.admin");

    private static final Set<String> FORBIDDEN;

    static {
        List<String> all = new ArrayList<>(CONSOLE_ONLY);
        all.addAll(UMBRELLAS);
        FORBIDDEN = Set.copyOf(all);
    }

    /** Node text as LuckPerms compares it. */
    static String normalize(String node) {
        return node == null ? "" : node.trim().toLowerCase(Locale.ROOT);
    }

    /** Why a node may not be put on the "allow" list of any group, or null when it is fine. */
    static String forbiddenToAllow(String node) {
        String n = normalize(node);
        if (n.equals("*") || n.endsWith(".*") || n.contains("*")) return "wildcards are never allowed";
        if (FORBIDDEN.contains(n)) return "is a console-only permission";
        return null;
    }

    /** Why a node text is not usable at all (not a plain permission), or null. */
    static String badSyntax(String node) {
        String n = normalize(node);
        if (n.isEmpty()) return "is empty";
        if (!n.matches("[a-z0-9_.*\\-:]+")) return "has characters a permission cannot have";
        if (n.startsWith("group.") || n.startsWith("weight.") || n.startsWith("prefix.") || n.startsWith("suffix.")
                || n.startsWith("meta.") || n.startsWith("displayname.")) {
            return "is a LuckPerms special node (use parents or weight instead)";
        }
        return null;
    }

    /** Expands {@code @console-only} and normalises: lower case, no duplicates, order kept. */
    static List<String> expand(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            if (s.trim().equalsIgnoreCase(CONSOLE_ONLY_TOKEN)) {
                for (String c : CONSOLE_ONLY) if (!out.contains(c)) out.add(c);
            } else {
                String n = normalize(s);
                if (!out.contains(n)) out.add(n);
            }
        }
        return out;
    }
}
