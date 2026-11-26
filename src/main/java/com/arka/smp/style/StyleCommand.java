package com.arka.smp.style;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** /style [tag|color|bonus|info|reload|rank]. With no argument the tap-only menu opens. */
final class StyleCommand implements CommandExecutor, TabCompleter {
    private final ArkaSMPStyle plugin;
    private final StyleMenus menus;

    StyleCommand(ArkaSMPStyle plugin) {
        this.plugin = plugin;
        this.menus = new StyleMenus(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("arkastyle.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            plugin.reloadStyle();
            msg.send(sender, "reloaded", "ranks", String.valueOf(plugin.ranks().all().size()),
                    "colors", String.valueOf(plugin.ranks().colors().size()), "tags", String.valueOf(plugin.ranks().tags().size()));
            return true;
        }
        if (sub.equals("rank")) {
            if (!sender.hasPermission("arkastyle.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            if (args.length < 2) return false;
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg.send(sender, "unknown-choice");
                return true;
            }
            Ranks.Rank r = plugin.rankOf(target);
            msg.send(sender, "info-player", "player", target.getName(), "rank!", r == null ? "<white>Free</white>" : r.name());
            return true;
        }
        if (sub.equals("setup")) {
            if (!canSetup(sender)) {
                msg.send(sender, "no-permission");
                return true;
            }
            boolean apply = args.length > 1 && args[1].equalsIgnoreCase("apply");
            if (args.length > 1 && !apply) {
                msg.send(sender, "setup-usage");
                return true;
            }
            setupLuckPerms(sender, apply);
            return true;
        }
        if (sub.equals("errors")) {
            if (!sender.hasPermission("arkastyle.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            var elog = plugin.errors();
            if (args.length > 1 && args[1].equalsIgnoreCase("test")) {
                String id = elog.warn("errors.test", "Test entry written by /style errors test", "by", sender.getName());
                msg.send(sender, "setup-line", "line", "Wrote test entry " + id + " to plugins/ArkaSMPStyle/logs/errors.log");
                return true;
            }
            var list = elog.recent(8);
            msg.send(sender, "setup-line", "line", "Error log (" + list.size() + " recent), file: plugins/ArkaSMPStyle/logs/errors.log");
            if (list.isEmpty()) msg.send(sender, "setup-line", "line", "No problems recorded since the server started.");
            for (String line : list) msg.send(sender, "setup-line", "line", line);
            return true;
        }
        if (sub.equals("staff")) {
            if (!sender.hasPermission("arkastyle.staff") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                msg.send(sender, "no-permission");
                return true;
            }
            staff(sender, args);
            return true;
        }
        if (sub.equals("give") || sub.equals("remove")) {
            if (!sender.hasPermission("arkastyle.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            giveOrTake(sender, sub.equals("give"), args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            msg.send(sender, "players-only");
            return true;
        }
        switch (sub) {
            case "" -> menus.main(p);
            case "info" -> {
                Ranks.Rank r = plugin.rankOf(p);
                msg.send(p, "info-rank", "rank!", r == null ? "<white>Free</white>" : r.name());
            }
            case "bonus" -> {
                switch (plugin.bonus().claim(p)) {
                    case ALREADY -> msg.send(p, "bonus-already");
                    case NO_RANK, DISABLED -> msg.send(p, "bonus-none");
                    case ERROR -> msg.send(p, "no-permission");
                    default -> { }
                }
            }
            case "tag" -> pick(p, args, true);
            case "color", "colour" -> pick(p, args, false);
            default -> menus.main(p);
        }
        return true;
    }

    // ---- /style give and /style remove (what the Tebex package runs) -------------------------

    private static final int MAX_DAYS = 366;
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** A player by UUID (with or without dashes) or by name. They must have joined the server before. */
    static OfflinePlayer find(String arg) {
        UUID id = GrantArgs.uuid(arg);
        OfflinePlayer p = id != null ? Bukkit.getOfflinePlayer(id)
                : Bukkit.getPlayerExact(arg) != null ? Bukkit.getPlayerExact(arg) : Bukkit.getOfflinePlayerIfCached(arg);
        return p != null && (p.isOnline() || p.hasPlayedBefore()) ? p : null;
    }

    /**
     * Only the server console, or someone explicitly given arkastyle.setup in LuckPerms. The permission's default is
     * false, and Bukkit treats a false default as false for everyone including the console, so the console is allowed
     * by name here.
     */
    private static boolean canSetup(CommandSender sender) {
        return sender instanceof org.bukkit.command.ConsoleCommandSender || sender.hasPermission("arkastyle.setup");
    }

    /**
     * /style setup shows what would change in LuckPerms and changes nothing. /style setup apply does it.
     * Both read the "roles" section of the config and refuse to go on if it has a mistake.
     */
    private void setupLuckPerms(CommandSender sender, boolean apply) {
        Messages msg = plugin.messages();
        RoleSync sync = plugin.roleSync();
        if (sync == null) {
            msg.send(sender, "no-luckperms");
            return;
        }
        RolePlan plan = RolePlan.parse(plugin.getConfig().getConfigurationSection("roles"));
        if (!plan.errors().isEmpty()) {
            msg.send(sender, "setup-errors");
            for (String e : plan.errors()) msg.send(sender, "setup-line", "line", e);
            return;
        }
        if (!apply) {
            RoleSync.Report r;
            try {
                r = sync.preview(plan);
            } catch (RuntimeException e) {
                plugin.errors().error("style.setup", "LuckPerms setup preview failed", e, "by", sender.getName());
                msg.send(sender, "grant-failed");
                return;
            }
            if (!r.errors().isEmpty()) {
                msg.send(sender, "setup-errors");
                for (String e : r.errors()) msg.send(sender, "setup-line", "line", e);
                return;
            }
            msg.send(sender, "setup-preview", "count", String.valueOf(r.changes()));
            for (String line : r.lines()) msg.send(sender, "setup-line", "line", line);
            msg.send(sender, "setup-file", "file", writeReport("setup-preview.txt", r.full()));
            msg.send(sender, r.changes() == 0 ? "setup-nothing" : "setup-next");
            return;
        }
        CompletableFuture<RoleSync.Report> run;
        try {
            run = sync.apply(plan);
        } catch (RuntimeException e) {
            plugin.errors().error("style.setup", "LuckPerms setup failed", e, "by", sender.getName());
            msg.send(sender, "grant-failed");
            return;
        }
        run.whenComplete((r, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.errors().error("style.setup", "LuckPerms setup failed", err, "by", sender.getName());
                msg.send(sender, "grant-failed");
                return;
            }
            if (!r.errors().isEmpty()) {
                msg.send(sender, "setup-errors");
                for (String e : r.errors()) msg.send(sender, "setup-line", "line", e);
                return;
            }
            plugin.getLogger().info(sender.getName() + " ran /style setup apply: " + r.changes() + " change(s). Backup: "
                    + (r.backup() == null ? "-" : r.backup().getName()));
            msg.send(sender, "setup-done", "count", String.valueOf(r.changes()));
            for (String line : r.lines()) msg.send(sender, "setup-line", "line", line);
            msg.send(sender, "setup-file", "file", writeReport("setup-applied.txt", r.full()));
            if (r.backup() != null) msg.send(sender, "setup-backup", "file", "setup-backups/" + r.backup().getName());
        }));
    }

    /** Saves the long report next to the config and returns its name for the chat message. */
    private String writeReport(String name, String text) {
        try {
            java.nio.file.Files.writeString(new java.io.File(plugin.getDataFolder(), name).toPath(), text);
            return "plugins/ArkaSMPStyle/" + name;
        } catch (java.io.IOException e) {
            plugin.errors().error("style.files", "Could not write " + name, e);
            return "(could not be saved)";
        }
    }

    // ---- /style staff give|remove <player> <group> ----------------------------------------------

    /**
     * Gives or takes a staff or creator group (helper, mod, builder, developer, srmod, youtuber, media by default).
     * The groups come from "staff-ranks.grantable" in the config; "admin" and "default" are refused whatever the config
     * says, nobody can change the groups of an Admin with it, and nobody can change their own.
     */
    private void staff(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        boolean give = args.length > 1 && args[1].equalsIgnoreCase("give");
        boolean take = args.length > 1 && args[1].equalsIgnoreCase("remove");
        if (args.length != 4 || (!give && !take)) {
            msg.send(sender, "staff-usage", "groups", String.join(", ", staffGroups()));
            return;
        }
        RankGrants grants = plugin.grants();
        if (grants == null) {
            msg.send(sender, "no-luckperms");
            return;
        }
        String group = args[3].toLowerCase(Locale.ROOT);
        if (StaffRanks.NEVER.contains(group)) {
            msg.send(sender, "staff-never", "group", group);
            return;
        }
        if (!StaffRanks.mayChange(group, plugin.getConfig().getStringList("staff-ranks.grantable"))) {
            msg.send(sender, "staff-not-allowed", "group", group, "groups", String.join(", ", staffGroups()));
            return;
        }
        if (!grants.groupExists(group)) {
            msg.send(sender, "group-missing", "group", group);
            return;
        }
        OfflinePlayer target = find(args[2]);
        if (target == null) {
            msg.send(sender, "unknown-player", "player", args[2]);
            return;
        }
        UUID id = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[2];
        boolean console = sender instanceof org.bukkit.command.ConsoleCommandSender;
        if (!console && sender instanceof Player self && self.getUniqueId().equals(id)) {
            msg.send(sender, "staff-self");
            return;
        }
        grants.hasGroup(id, "admin").thenCompose(isAdmin -> {
            if (isAdmin && !console) return java.util.concurrent.CompletableFuture.completedFuture((RankGrants.Result) null);
            return give ? grants.grantForever(id, group) : grants.take(id, group);
        }).whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.errors().error("style.staff", "Could not change a LuckPerms group", err, "player", name, "group", group, "by", sender.getName());
                msg.send(sender, "grant-failed");
                return;
            }
            if (res == null) {
                msg.send(sender, "staff-target-admin", "player", name);
                return;
            }
            plugin.getLogger().info((give ? "Gave" : "Took") + " group " + group + (give ? " to " : " from ") + name
                    + " (" + id + ") by " + sender.getName() + " with /style staff");
            msg.send(sender, give ? "staff-given" : "staff-removed", "player", name, "group", group);
        }));
    }

    private List<String> staffGroups() {
        return StaffRanks.clean(plugin.getConfig().getStringList("staff-ranks.grantable"));
    }

    private void giveOrTake(CommandSender sender, boolean give, String[] args) {
        Messages msg = plugin.messages();
        if (args.length < 3 || args.length > (give ? 4 : 3)) {
            msg.send(sender, give ? "give-usage" : "remove-usage");
            return;
        }
        RankGrants grants = plugin.grants();
        if (grants == null) {
            msg.send(sender, "no-luckperms");
            return;
        }
        Ranks.Rank rank = plugin.ranks().byId(args[2]);
        if (rank == null) {
            msg.send(sender, "unknown-rank");
            return;
        }
        if (give && !grants.groupExists(rank.group())) {
            msg.send(sender, "group-missing", "group", rank.group());
            return;
        }
        int days = 0;
        if (give) {
            days = args.length == 4 ? GrantArgs.days(args[3], MAX_DAYS)
                    : Math.min(MAX_DAYS, plugin.getConfig().getInt("purchase.default-days", 30));
            if (days < 1) {
                msg.send(sender, "bad-days", "max", String.valueOf(MAX_DAYS));
                return;
            }
        }
        OfflinePlayer target = find(args[1]);
        if (target == null) {
            msg.send(sender, "unknown-player", "player", args[1]);
            return;
        }
        UUID id = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[1];
        CompletableFuture<RankGrants.Result> change;
        try {
            change = give ? grants.grant(id, rank.group(), days) : grants.take(id, rank.group());
        } catch (RuntimeException e) {
            plugin.errors().error("style.rank", "Could not change a rank", e, "player", name, "by", sender.getName());
            msg.send(sender, "grant-failed");
            return;
        }
        final int daysGiven = days;
        change.whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.errors().error("style.rank", "LuckPerms rank change failed", err, "player", name, "by", sender.getName());
                msg.send(sender, "grant-failed");
                return;
            }
            if (!give) {
                plugin.getLogger().info("Removed rank " + rank.id() + " from " + name + " (" + id + ") by " + sender.getName());
                msg.send(sender, "remove-ok", "player", name, "rank!", rank.name());
                return;
            }
            String until = res.expiry() == null ? "-" : UNTIL.format(res.expiry().atZone(ZoneId.systemDefault()));
            plugin.getLogger().info("Gave rank " + rank.id() + " to " + name + " (" + id + ") for " + daysGiven
                    + " days, until " + until + ", by " + sender.getName());
            msg.send(sender, "give-ok", "player", name, "rank!", rank.name(), "until", until);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                // a moment later, so LuckPerms has refreshed the player's permissions
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!online.isOnline()) return;
                    msg.send(online, "rank-granted", "rank!", rank.name(), "until", until);
                    plugin.bonus().claim(online);
                }, 2L);
            }
            if (plugin.getConfig().getBoolean("purchase.broadcast", true)) {
                Bukkit.broadcast(msg.get("purchase-broadcast", "player", name, "rank!", rank.name()));
            }
        }));
    }

    private void pick(Player p, String[] args, boolean tag) {
        if (args.length < 2) {
            if (tag) menus.tags(p);
            else menus.colors(p);
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("none") || id.equals("off") || id.equals("clear")) {
            StyleMenus.choose(plugin, p, null, tag);
            return;
        }
        Ranks.Choice c = (tag ? plugin.ranks().tags() : plugin.ranks().colors()).get(id);
        if (c == null) plugin.messages().send(p, "unknown-choice");
        else StyleMenus.choose(plugin, p, c, tag);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("tag", "color", "bonus", "info"));
            if (sender.hasPermission("arkastyle.admin")) out.addAll(List.of("reload", "rank", "give", "remove", "errors"));
            if (canSetup(sender)) out.add("setup");
            if (sender.hasPermission("arkastyle.staff")) out.add("staff");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setup") && canSetup(sender)) {
            out.add("apply");
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("staff") && sender.hasPermission("arkastyle.staff")) {
            if (args.length == 2) out.addAll(List.of("give", "remove"));
            else if (args.length == 3) Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            else if (args.length == 4) out.addAll(staffGroups());
        } else if (args.length >= 2 && sender.hasPermission("arkastyle.admin")
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))) {
            if (args.length == 2) Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            else if (args.length == 3) plugin.ranks().all().forEach(r -> out.add(r.id()));
            else if (args.length == 4 && args[0].equalsIgnoreCase("give")) out.add("30");
        } else if (args.length == 2 && sender instanceof Player p) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            Ranks.Rank rank = plugin.rankOf(p);
            if (sub.equals("tag") || sub.equals("color") || sub.equals("colour")) {
                var map = sub.equals("tag") ? plugin.ranks().tags() : plugin.ranks().colors();
                map.values().stream().filter(c -> plugin.ranks().canUse(c, rank)).forEach(c -> out.add(c.id()));
                out.add("none");
            } else if (sub.equals("rank")) {
                Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
