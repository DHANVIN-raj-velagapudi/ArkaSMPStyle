package com.arka.smp.style;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Works out what a staff member did from the command they typed: which kind of action it is (ban, mute, vanish, fly,
 * teleport), who it was used on, for how long and why, and a readable sentence for the log and the admin alert.
 * Pure text handling: no server needed.
 */
final class StaffActions {
    private StaffActions() {
    }

    enum Kind {
        BAN, MUTE, VANISH, FLY, TP,
        /** Any other command a staff member typed. */
        COMMAND,
        /** Joining or leaving the server. */
        SESSION;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What was done. {@code text} reads like "banned Griefer for 2d: griefing". */
    record Action(Kind kind, String command, String text, String target, String duration, String reason) {
    }

    /** Command names (after aliases are folded in) and the kind of action each one is. */
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("ban", Kind.BAN), Map.entry("tempban", Kind.BAN), Map.entry("banip", Kind.BAN),
            Map.entry("unban", Kind.BAN), Map.entry("unbanip", Kind.BAN),
            Map.entry("mute", Kind.MUTE),
            Map.entry("vanish", Kind.VANISH),
            Map.entry("fly", Kind.FLY),
            Map.entry("tp", Kind.TP), Map.entry("tpo", Kind.TP), Map.entry("tphere", Kind.TP), Map.entry("tpohere", Kind.TP),
            Map.entry("tppos", Kind.TP), Map.entry("tpall", Kind.TP));

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ban-ip", "banip"), Map.entry("ipban", "banip"), Map.entry("pardon", "unban"),
            Map.entry("pardon-ip", "unbanip"), Map.entry("v", "vanish"), Map.entry("tele", "tp"),
            Map.entry("tp2p", "tp"), Map.entry("teleport", "tp"), Map.entry("s", "tphere"));

    private static final Pattern DURATION = Pattern.compile("(?i)^(\\d+[a-z]+)+$");
    private static final Pattern COORD = Pattern.compile("^[~^]?-?\\d*(\\.\\d+)?$");

    /** The command name without a leading slash or plugin prefix ("essentials:tempban" gives "tempban"), lower case. */
    static String bare(String label) {
        String n = label.toLowerCase(Locale.ROOT);
        if (n.startsWith("/")) n = n.substring(1);
        int colon = n.indexOf(':');
        return colon >= 0 ? n.substring(colon + 1) : n;
    }

    /** The one name a command is known by: "v" is "vanish", "pardon" is "unban", "etempban" is "tempban". */
    static String canonical(String label) {
        String n = bare(label);
        String a = ALIASES.get(n);
        if (a != null) return a;
        if (!KINDS.containsKey(n) && n.startsWith("e")) {          // Essentials also answers to "e" + name
            String rest = n.substring(1);
            a = ALIASES.get(rest);
            if (a != null) return a;
            if (KINDS.containsKey(rest)) return rest;
        }
        return n;
    }

    /** The permissions that let the typed command run: the player must hold one of them for it to count as used. */
    static String[] permissionNodes(String label) {
        return new String[]{"essentials." + canonical(label), "minecraft.command." + bare(label)};
    }

    /** Commands whose arguments are secret (passwords, codes). They are logged without the arguments. */
    static final java.util.Set<String> HIDDEN = java.util.Set.of("login", "l", "log", "register", "reg", "unregister",
            "changepassword", "cp", "passwd", "password", "2fa", "totp", "captcha", "email", "setemail", "recovery", "authme");

    /** Commands no staff member should ever type (their permissions are denied): typing one warns the admins. */
    static final java.util.List<String> FORBIDDEN = java.util.List.of("gamemode", "gm", "gmc", "gms", "gma", "gmsp",
            "defaultgamemode", "op", "deop", "give", "ban", "banip", "ban-ip", "ipban", "pardon", "unban", "unbanip", "stop",
            "execute", "lp", "luckperms", "kill", "summon", "effect", "fill", "setblock", "clone", "whitelist", "reload", "rl");

    /** Any other command: "typed /home spawn". With {@code hide} the arguments are left out. */
    static Action generic(String label, String[] args, boolean hide) {
        String name = bare(label);
        String rest = hide ? "" : rest(args, 0);
        String text = "typed /" + name + (hide ? " (arguments hidden)" : rest.isEmpty() ? "" : " " + rest);
        return new Action(Kind.COMMAND, name, text, "", "", "");
    }

    /** "joined the server" or "left the server". */
    static Action session(boolean joined) {
        return new Action(Kind.SESSION, joined ? "join" : "quit", joined ? "joined the server" : "left the server", "", "", "");
    }

    /** What the command is, or null when it is not one the staff log tracks. */
    static Action parse(String label, String[] args) {
        String c = canonical(label);
        Kind kind = KINDS.get(c);
        if (kind == null) return null;
        String a0 = args.length > 0 ? clean(args[0]) : "";
        return switch (kind) {
            case BAN -> ban(c, args, a0);
            case MUTE -> mute(c, args, a0);
            case VANISH, FLY -> toggle(kind, c, args, a0);
            case TP -> teleport(c, args, a0);
            case COMMAND, SESSION -> null;       // those two are made by generic() and session(), never found by name
        };
    }

    private static Action ban(String c, String[] args, String target) {
        if (target.isEmpty()) return new Action(Kind.BAN, c, "used /" + c + " without naming a player", "", "", "");
        boolean un = c.startsWith("un");
        String duration = c.equals("tempban") && args.length > 1 ? clean(args[1]) : "";
        String reason = rest(args, c.equals("tempban") ? 2 : 1);
        String verb = un ? "unbanned" : c.equals("tempban") ? "temp-banned" : c.equals("banip") ? "IP-banned" : "banned";
        StringBuilder t = new StringBuilder(verb).append(' ').append(target);
        if (!duration.isEmpty()) t.append(" for ").append(duration);
        else if (!un && !c.equals("tempban")) t.append(" permanently");
        if (!reason.isEmpty()) t.append(": ").append(reason);
        return new Action(Kind.BAN, c, t.toString(), target, duration, reason);
    }

    private static Action mute(String c, String[] args, String target) {
        if (target.isEmpty()) return new Action(Kind.MUTE, c, "used /mute without naming a player", "", "", "");
        String duration = args.length > 1 && DURATION.matcher(args[1]).matches() ? clean(args[1]) : "";
        String reason = rest(args, duration.isEmpty() ? 1 : 2);
        // /mute is a toggle in Essentials: with a time or a reason it is a mute, on its own it can be either
        StringBuilder t = new StringBuilder(duration.isEmpty() && reason.isEmpty() ? "toggled mute on " : "muted ").append(target);
        if (!duration.isEmpty()) t.append(" for ").append(duration);
        if (!reason.isEmpty()) t.append(": ").append(reason);
        return new Action(Kind.MUTE, c, t.toString(), target, duration, reason);
    }

    private static Action toggle(Kind kind, String c, String[] args, String a0) {
        if (a0.isEmpty()) return new Action(kind, c, "toggled " + c, "", "", "");
        String switchWord = isSwitch(a0) ? a0.toLowerCase(Locale.ROOT) : "";
        if (!switchWord.isEmpty()) return new Action(kind, c, "turned " + c + " " + switchWord, "", "", "");
        String second = args.length > 1 && isSwitch(args[1]) ? clean(args[1]).toLowerCase(Locale.ROOT) : "";
        String text = (second.isEmpty() ? "toggled " + c + " for " : "turned " + c + " " + second + " for ") + a0;
        return new Action(kind, c, text, a0, "", "");
    }

    private static Action teleport(String c, String[] args, String a0) {
        String text;
        String target = "";
        switch (c) {
            case "tphere", "tpohere" -> {
                text = a0.isEmpty() ? "used /" + c : "teleported " + a0 + " to themselves";
                target = a0;
            }
            case "tpall" -> text = "teleported everyone to themselves";
            case "tppos" -> text = "teleported to coordinates " + rest(args, 0);
            case "tpo" -> {
                text = a0.isEmpty() ? "used /tpo" : "teleported (override) to " + a0;
                target = a0;
            }
            default -> {
                if (a0.isEmpty()) text = "used /tp";
                else if (isCoord(a0)) text = "teleported to coordinates " + rest(args, 0);
                else if (args.length == 1) {
                    text = "teleported to " + a0;
                    target = a0;
                } else if (isCoord(args[1])) {
                    text = "teleported " + a0 + " to coordinates " + rest(args, 1);
                    target = a0;
                } else {
                    text = "teleported " + a0 + " to " + clean(args[1]);
                    target = a0;
                }
            }
        }
        return new Action(Kind.TP, c, text, target, "", "");
    }

    private static final Pattern PART = Pattern.compile("(\\d+)(y|mo|w|d|h|m|s)");

    /**
     * A time like "3d", "72h" or "1d12h" in seconds (units y, mo, w, d, h, m, s as Essentials writes them; a month counts
     * 30 days, a year 365). -1 when it is empty, not a time, or absurdly long.
     */
    static long seconds(String duration) {
        if (duration == null || duration.isBlank()) return -1;
        String s = duration.toLowerCase(Locale.ROOT).strip();
        var m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
            long unit = switch (m.group(2)) {
                case "y" -> 365L * 86400;
                case "mo" -> 30L * 86400;
                case "w" -> 7L * 86400;
                case "d" -> 86400L;
                case "h" -> 3600L;
                case "m" -> 60L;
                default -> 1L;
            };
            if (n > 100_000L) return -1;
            total += n * unit;
            if (total > 100L * 365 * 86400) return -1;
        }
        return end == s.length() && end > 0 ? total : -1;
    }

    /**
     * The staff rules for a ban or mute: the key of the message to show when it breaks one, or null when it is fine.
     * A ban needs a time of at most {@code maxDays}; a ban or a mute needs a reason of at least {@code minReason} letters.
     */
    static String problem(boolean ban, String duration, String reason, int maxDays, int minReason) {
        if (ban) {
            long s = seconds(duration);
            if (s <= 0) return "staff-ban-usage";
            if (s > maxDays * 86400L) return "staff-ban-too-long";
        }
        return reason == null || reason.strip().length() < minReason ? "staff-need-reason" : null;
    }

    private static boolean isSwitch(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "on", "off", "enable", "disable", "true", "false" -> true;
            default -> false;
        };
    }

    private static boolean isCoord(String s) {
        return !s.isEmpty() && COORD.matcher(s).matches() && !s.equals("-") && !s.equals("~") || s.equals("~");
    }

    /** Arguments from {@code from} on, joined with spaces. */
    private static String rest(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(clean(args[i]));
        }
        return sb.toString();
    }

    /** No line breaks or control characters, so one action is always one line in the file. */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) sb.append(Character.isISOControl(ch) ? ' ' : ch);
        String out = sb.toString().strip();
        return out.length() > 300 ? out.substring(0, 300) + "..." : out;
    }

    /**
     * One line of the staff log: {@code time | KIND | staff | what | typed | place | uuid}. The pieces are separated
     * by " | " so the file is easy to read and to search.
     */
    static String line(String time, Action a, String staff, String uuid, String typed, String place) {
        return time + " | " + a.kind().name() + " | " + clean(staff) + " | " + a.text() + " | " + clean(typed)
                + " | " + place + " | " + uuid;
    }
}
