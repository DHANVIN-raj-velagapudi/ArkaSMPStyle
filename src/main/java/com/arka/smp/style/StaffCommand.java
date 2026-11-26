package com.arka.smp.style;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * /staff ban, mute, unmute, vanish, fly, tp. The Staff rank has no direct Essentials commands: everything goes through
 * here, so the plugin checks the rules (a short reason, bans of 3 days at most, nobody can ban or mute an admin or
 * another staff member), does the work, writes the staff log and warns the admins.
 *
 * Bans and mutes are carried out by running Essentials' own /tempban and /mute as the console, so they show up in
 * Essentials, can be undone by admins in the usual way and survive restarts. Fly and teleport are done directly.
 */
final class StaffCommand implements CommandExecutor, TabCompleter {
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.]{1,20}$");
    private static final List<String> SUBS = List.of("ban", "mute", "unmute", "vanish", "fly", "tp");

    private final ArkaSMPStyle plugin;
    private final StaffAudit audit;

    StaffCommand(ArkaSMPStyle plugin, StaffAudit audit) {
        this.plugin = plugin;
        this.audit = audit;
    }

    private static String permission(String sub) {
        return switch (sub) {
            case "ban" -> "arkastyle.staffcmd.ban";
            case "mute", "unmute" -> "arkastyle.staffcmd.mute";
            case "vanish" -> "arkastyle.staffcmd.vanish";
            case "fly" -> "arkastyle.staffcmd.fly";
            case "tp" -> "arkastyle.staffcmd.tp";
            default -> null;
        };
    }

    private int maxBanDays() {
        return Math.max(1, plugin.getConfig().getInt("staff-audit.max-ban-days", 3));
    }

    private int minReason() {
        return Math.max(1, plugin.getConfig().getInt("staff-audit.reason-min-length", 3));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "players-only");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String perm = permission(sub);
        if (perm == null) {
            List<String> mine = new ArrayList<>();
            for (String s : SUBS) {
                if (p.hasPermission(permission(s))) mine.add(s);
            }
            if (mine.isEmpty()) msg.send(p, "no-permission");
            else msg.send(p, "staff-help", "commands", String.join(", ", mine));
            return true;
        }
        if (!p.hasPermission(perm)) {
            msg.send(p, "no-permission");
            return true;
        }
        switch (sub) {
            case "ban" -> ban(p, args);
            case "mute" -> mute(p, args);
            case "unmute" -> unmute(p, args);
            case "vanish" -> vanish(p, args);
            case "fly" -> fly(p, args);
            default -> teleport(p, args);
        }
        return true;
    }

    // ---- ban and mute ---------------------------------------------------------------------------

    private void ban(Player p, String[] args) {
        Messages msg = plugin.messages();
        if (args.length < 3) {
            msg.send(p, "staff-usage-ban", "max", String.valueOf(maxBanDays()));
            return;
        }
        String time = args[2];
        String reason = StaffActions.clean(join(args, 3));
        String problem = StaffActions.problem(true, time, reason, maxBanDays(), minReason());
        if (problem != null) {
            msg.send(p, problem, "max", String.valueOf(maxBanDays()), "min", String.valueOf(minReason()));
            return;
        }
        OfflinePlayer target = target(p, args[1]);
        if (target == null) return;
        String name = target.getName();
        StaffActions.Action attempted = StaffActions.parse("tempban", words(name, time, reason));
        protectedCheck(p, target, "essentials.tempban.exempt", attempted, typed(args), () -> {
            if (!console("tempban " + name + " " + time + " " + reason)) {
                unavailable(p, "tempban");
                return;
            }
            audit.record(p, attempted, typed(args), true);
            msg.send(p, "staff-banned", "player", name, "time", time, "reason", reason);
        });
    }

    private void mute(Player p, String[] args) {
        Messages msg = plugin.messages();
        if (args.length < 3) {
            msg.send(p, "staff-usage-mute");
            return;
        }
        String time = StaffActions.seconds(args[2]) > 0 ? args[2] : "";
        String reason = StaffActions.clean(join(args, time.isEmpty() ? 2 : 3));
        String problem = StaffActions.problem(false, time, reason, maxBanDays(), minReason());
        if (problem != null) {
            msg.send(p, problem, "max", String.valueOf(maxBanDays()), "min", String.valueOf(minReason()));
            return;
        }
        OfflinePlayer target = target(p, args[1]);
        if (target == null) return;
        String name = target.getName();
        Boolean muted = EssentialsLink.isMuted(target.getUniqueId());
        if (muted == null) {
            unavailable(p, "mute");
            return;
        }
        if (muted) {
            msg.send(p, "staff-already-muted", "player", name);
            return;
        }
        StaffActions.Action attempted = time.isEmpty() ? StaffActions.parse("mute", words(name, reason))
                : StaffActions.parse("mute", words(name, time, reason));
        protectedCheck(p, target, "essentials.mute.exempt", attempted, typed(args), () -> {
            if (!console("mute " + name + (time.isEmpty() ? "" : " " + time) + " " + reason)) {
                unavailable(p, "mute");
                return;
            }
            audit.record(p, attempted, typed(args), true);
            msg.send(p, "staff-muted", "player", name, "until", time.isEmpty() ? "until someone unmutes them" : "for " + time,
                    "reason", reason);
        });
    }

    private void unmute(Player p, String[] args) {
        Messages msg = plugin.messages();
        if (args.length < 2) {
            msg.send(p, "staff-usage-unmute");
            return;
        }
        OfflinePlayer target = target(p, args[1]);
        if (target == null) return;
        String name = target.getName();
        Boolean muted = EssentialsLink.isMuted(target.getUniqueId());
        if (muted == null) {
            unavailable(p, "mute");
            return;
        }
        if (!muted) {
            msg.send(p, "staff-not-muted", "player", name);
            return;
        }
        if (!console("mute " + name)) {          // Essentials' /mute is a toggle: this turns the mute off
            unavailable(p, "mute");
            return;
        }
        String reason = StaffActions.clean(join(args, 2));
        audit.record(p, new StaffActions.Action(StaffActions.Kind.MUTE, "mute", "unmuted " + name + (reason.isEmpty() ? "" : ": " + reason),
                name, "", reason), typed(args), true);
        msg.send(p, "staff-unmuted", "player", name);
    }

    /** The player to act on: must have joined before, have a normal name, and not be the staff member. Tells them if not. */
    private OfflinePlayer target(Player p, String arg) {
        OfflinePlayer t = StyleCommand.find(arg);
        if (t == null || t.getName() == null || !NAME.matcher(t.getName()).matches()) {
            plugin.messages().send(p, "unknown-player", "player", arg);
            return null;
        }
        if (t.getUniqueId().equals(p.getUniqueId())) {
            plugin.messages().send(p, "staff-self-target");
            return null;
        }
        return t;
    }

    /**
     * Refuses when the target is an admin or has the exempt permission (every Mod, SrMod, Admin and Staff has it). An
     * online player is checked at once; an offline one through LuckPerms. If LuckPerms cannot answer the command is refused.
     */
    private void protectedCheck(Player staff, OfflinePlayer t, String exemptNode, StaffActions.Action attempted, String typed, Runnable go) {
        Player online = t.getPlayer();
        if (online != null) {
            if (online.hasPermission(StaffAudit.ALERT_PERMISSION) || online.hasPermission(exemptNode)) deny(staff, attempted, typed);
            else go.run();
            return;
        }
        RankGrants grants = plugin.grants();
        if (grants == null) {
            go.run();
            return;
        }
        grants.hasAnyPermission(t.getUniqueId(), StaffAudit.ALERT_PERMISSION, exemptNode).whenComplete((isProtected, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        plugin.errors().error("staff.command", "Could not check whether a player may be banned or muted", err,
                                "staff", staff.getName(), "target", String.valueOf(t.getName()));
                        plugin.messages().send(staff, "grant-failed");
                    } else if (isProtected) {
                        deny(staff, attempted, typed);
                    } else {
                        go.run();
                    }
                }));
    }

    private void deny(Player staff, StaffActions.Action attempted, String typed) {
        plugin.messages().send(staff, "staff-protected");
        audit.alert(staff, "tried to " + attempted.text() + " (blocked: admins and staff cannot be banned or muted by staff)");
        audit.record(staff, new StaffActions.Action(attempted.kind(), attempted.command(),
                "BLOCKED (target is an admin or staff): " + attempted.text(), attempted.target(), attempted.duration(),
                attempted.reason()), typed, false);
    }

    // ---- vanish, fly, teleport -------------------------------------------------------------------

    private void vanish(Player p, String[] args) {
        String arg = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!arg.isEmpty() && !arg.equals("on") && !arg.equals("off")) {
            plugin.messages().send(p, "staff-usage-switch", "command", "vanish");
            return;
        }
        if (!console("vanish " + p.getName() + (arg.isEmpty() ? "" : " " + arg))) {
            unavailable(p, "vanish");
            return;
        }
        StaffActions.Action a = StaffActions.parse("vanish", arg.isEmpty() ? new String[0] : new String[]{arg});
        audit.record(p, a, typed(args), false);
        plugin.messages().send(p, "staff-done", "text", "You " + a.text() + ".");
    }

    private void fly(Player p, String[] args) {
        String arg = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!arg.isEmpty() && !arg.equals("on") && !arg.equals("off")) {
            plugin.messages().send(p, "staff-usage-switch", "command", "fly");
            return;
        }
        boolean on = arg.isEmpty() ? !p.getAllowFlight() : arg.equals("on");
        p.setAllowFlight(on);
        if (on) p.setFlying(true);
        else p.setFlying(false);
        StaffActions.Action a = StaffActions.parse("fly", new String[]{on ? "on" : "off"});
        audit.record(p, a, typed(args), false);
        plugin.messages().send(p, "staff-done", "text", "Flying is now " + (on ? "on" : "off") + ".");
    }

    private void teleport(Player p, String[] args) {
        Messages msg = plugin.messages();
        StaffActions.Action a;
        if (args.length == 2) {
            Player to = Bukkit.getPlayerExact(args[1]) != null ? Bukkit.getPlayerExact(args[1]) : Bukkit.getPlayer(args[1]);
            if (to == null) {
                msg.send(p, "unknown-player", "player", args[1]);
                return;
            }
            if (to.getUniqueId().equals(p.getUniqueId())) {
                msg.send(p, "staff-self-target");
                return;
            }
            p.teleportAsync(to.getLocation());
            a = StaffActions.parse("tp", new String[]{to.getName()});
        } else if (args.length == 4) {
            double[] xyz = new double[3];
            for (int i = 0; i < 3; i++) {
                try {
                    xyz[i] = Double.parseDouble(args[i + 1]);
                } catch (NumberFormatException e) {
                    xyz[i] = Double.NaN;
                }
            }
            if (!inRange(xyz[0], 30_000_000) || !inRange(xyz[1], 4096) || !inRange(xyz[2], 30_000_000)) {
                msg.send(p, "staff-usage-tp");
                return;
            }
            p.teleportAsync(new Location(p.getWorld(), xyz[0], xyz[1], xyz[2], p.getLocation().getYaw(), p.getLocation().getPitch()));
            a = StaffActions.parse("tp", new String[]{args[1], args[2], args[3]});
        } else {
            msg.send(p, "staff-usage-tp");
            return;
        }
        audit.record(p, a, typed(args), false);
        msg.send(p, "staff-done", "text", "You " + a.text() + ".");
    }

    private static boolean inRange(double v, double limit) {
        return Double.isFinite(v) && Math.abs(v) <= limit;
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private boolean console(String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void unavailable(Player p, String what) {
        plugin.errors().warn("staff.command", "Essentials did not run /" + what + ", or does not answer", "staff", p.getName());
        plugin.messages().send(p, "staff-unavailable");
    }

    private static String typed(String[] args) {
        return "/staff " + String.join(" ", args);
    }

    private static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    /** The first words as given, then the reason split into words (so StaffActions reads them like typed arguments). */
    private static String[] words(String first, String... rest) {
        List<String> out = new ArrayList<>();
        out.add(first);
        for (String r : rest) out.addAll(Arrays.asList(r.split(" ")));
        return out.toArray(String[]::new);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player p)) return out;
        if (args.length == 1) {
            for (String s : SUBS) {
                if (p.hasPermission(permission(s))) out.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!p.hasPermission(String.valueOf(permission(sub)))) return out;
            switch (sub) {
                case "ban", "mute", "unmute", "tp" -> Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
                case "vanish", "fly" -> out.addAll(List.of("on", "off"));
                default -> { }
            }
        } else if (args.length == 3 && p.hasPermission(String.valueOf(permission(args[0].toLowerCase(Locale.ROOT))))) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("ban")) out.addAll(List.of("30m", "1h", "12h", "1d", "2d", "3d"));
            else if (sub.equals("mute")) out.addAll(List.of("10m", "30m", "1h", "1d"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
