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
    private static OfflinePlayer find(String arg) {
        UUID id = GrantArgs.uuid(arg);
        OfflinePlayer p = id != null ? Bukkit.getOfflinePlayer(id)
                : Bukkit.getPlayerExact(arg) != null ? Bukkit.getPlayerExact(arg) : Bukkit.getOfflinePlayerIfCached(arg);
        return p != null && (p.isOnline() || p.hasPlayedBefore()) ? p : null;
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
            plugin.getLogger().warning("Could not change the rank of " + name + ": " + e.getMessage());
            msg.send(sender, "grant-failed");
            return;
        }
        final int daysGiven = days;
        change.whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().warning("LuckPerms change for " + name + " failed: " + err);
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
            if (sender.hasPermission("arkastyle.admin")) out.addAll(List.of("reload", "rank", "give", "remove"));
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
