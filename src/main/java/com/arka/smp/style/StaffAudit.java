package com.arka.smp.style;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The staff log and the admin warnings, and the rule that watched staff use /staff.
 *
 * Staff do not use the Essentials commands directly. The plugin's /staff command (see {@link StaffCommand}) checks the
 * rules, does the work and calls {@link #record}. If a watched staff member types /tempban, /mute, /vanish, /fly or /tp
 * anyway, it is cancelled here, they are told to use /staff, and the attempt is written to the log.
 * Admins (anyone with arkastyle.staffalert) are never watched: they are the ones being warned.
 */
final class StaffAudit implements Listener {
    static final String ALERT_PERMISSION = "arkastyle.staffalert";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArkaSMPStyle plugin;
    private final StaffLog log;

    StaffAudit(ArkaSMPStyle plugin, StaffLog log) {
        this.plugin = plugin;
        this.log = log;
    }

    /** Runs before the command does (HIGH): a watched staff member's direct Essentials command is cancelled here. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        try {
            handle(e);
        } catch (RuntimeException ex) {
            plugin.errors().error("staff-audit", "Could not check a staff command", ex, "player", e.getPlayer().getName());
        }
    }

    private void handle(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("staff-audit.enabled", true)) return;
        if (!watched(p)) return;                                   // almost every command comes from a normal player: stop before parsing it
        String text = e.getMessage().strip();
        if (text.length() < 2 || text.charAt(0) != '/') return;
        String[] parts = text.substring(1).split("\\s+");
        String bare = StaffActions.bare(parts[0]);
        if (bare.equals("staff")) return;                          // /staff writes its own, fuller log line
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        StaffActions.Action action = StaffActions.parse(parts[0], args);
        if (action != null) {
            e.setCancelled(true);
            plugin.messages().send(p, "staff-use-command", "command", action.kind().id());
            record(p, new StaffActions.Action(action.kind(), action.command(),
                    "BLOCKED (typed the Essentials command, not /staff): " + action.text(), action.target(), action.duration(),
                    action.reason()), text, false);
            return;
        }
        // every other command is written to the log too; secret arguments (passwords) are left out
        boolean hide = hidden().contains(bare);
        record(p, StaffActions.generic(parts[0], args, hide), hide ? "/" + bare + " ***" : text, false);
        if (forbidden().contains(bare)) {
            alert(p, "typed " + (hide ? "/" + bare : StaffActions.clean(text)) + " (a command Staff must not use)");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        session(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        session(e.getPlayer(), false);
    }

    private void session(Player p, boolean joined) {
        try {
            if (plugin.getConfig().getBoolean("staff-audit.enabled", true) && watched(p)) {
                record(p, StaffActions.session(joined), "-", false);
            }
        } catch (RuntimeException ex) {
            plugin.errors().error("staff-audit", "Could not record a staff join or leave", ex, "player", p.getName());
        }
    }

    private Set<String> hidden() {
        List<String> configured = plugin.getConfig().isList("staff-audit.hide-arguments")
                ? plugin.getConfig().getStringList("staff-audit.hide-arguments") : List.copyOf(StaffActions.HIDDEN);
        return configured.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private Set<String> forbidden() {
        List<String> configured = plugin.getConfig().isList("staff-audit.alert-commands")
                ? plugin.getConfig().getStringList("staff-audit.alert-commands") : StaffActions.FORBIDDEN;
        return configured.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /** A member of one of the staff groups who is not an admin. */
    boolean watched(Player p) {
        if (p.hasPermission(ALERT_PERMISSION)) return false;
        List<String> groups = plugin.getConfig().getStringList("staff-audit.groups");
        if (groups.isEmpty()) groups = List.of("staff");
        for (String g : groups) {
            if (p.hasPermission("group." + g.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /**
     * Writes one line to the staff log and, for the kinds in "staff-audit.alert" (bans and mutes), warns the online
     * admins. {@code typed} is the command line the staff member typed.
     */
    void record(Player p, StaffActions.Action action, String typed, boolean alertAdmins) {
        if (kinds("staff-audit.log", List.of("ban", "mute", "vanish", "fly", "tp", "command", "session")).contains(action.kind().id())) {
            Location l = p.getLocation();
            String place = l.getWorld() == null ? "?"
                    : l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
            log.write(StaffActions.line(LocalDateTime.now().format(TIME), action, p.getName(), p.getUniqueId().toString(), typed, place));
        }
        if (alertAdmins && kinds("staff-audit.alert", List.of("ban", "mute")).contains(action.kind().id())) alert(p, action.text());
    }

    /** Tells every online admin, and the console, what the staff member did. */
    void alert(Player staff, String what) {
        var line = plugin.messages().plain("staff-alert", "staff", staff.getName(), "text", what);
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(ALERT_PERMISSION)) admin.sendMessage(line);
        }
        plugin.getLogger().info("[staff] " + staff.getName() + " " + what);
    }

    private Set<String> kinds(String path, List<String> fallback) {
        List<String> configured = plugin.getConfig().isList(path) ? plugin.getConfig().getStringList(path) : fallback;
        return configured.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
