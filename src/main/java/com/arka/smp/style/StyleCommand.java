package com.arka.smp.style;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            if (sender.hasPermission("arkastyle.admin")) out.addAll(List.of("reload", "rank"));
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
