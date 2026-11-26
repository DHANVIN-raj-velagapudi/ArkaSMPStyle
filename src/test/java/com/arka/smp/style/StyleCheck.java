package com.arka.smp.style;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plain checks (no server needed). Run: java -cp target/classes;target/test-classes com.arka.smp.style.StyleCheck */
public final class StyleCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    private static RolePlan plan(String yaml) {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            y.loadFromString("roles:\n" + yaml.indent(2));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return RolePlan.parse(y.getConfigurationSection("roles"));
    }

    private static int indexOf(RolePlan p, String name) {
        for (int i = 0; i < p.groups().size(); i++) if (p.groups().get(i).name().equals(name)) return i;
        return -1;
    }

    private static void roleChecks() {
        // the config that ships with the plugin must be valid and must not allow anything console-only
        RolePlan shipped = null;
        try {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.File("src/main/resources/config.yml"));
            shipped = RolePlan.parse(y.getConfigurationSection("roles"));
        } catch (RuntimeException e) {
            System.out.println("  (could not read src/main/resources/config.yml: " + e + ")");
        }
        check("shipped roles section is valid", shipped != null && shipped.errors().isEmpty());
        if (shipped != null) {
            check("shipped roles: 12 groups", shipped.groups().size() == 12);
            check("shipped roles: parents come before children", indexOf(shipped, "pro") < indexOf(shipped, "ultra")
                    && indexOf(shipped, "ultra") < indexOf(shipped, "youtuber") && indexOf(shipped, "helper") < indexOf(shipped, "mod")
                    && indexOf(shipped, "mod") < indexOf(shipped, "srmod") && indexOf(shipped, "ultra") < indexOf(shipped, "srmod")
                    && indexOf(shipped, "srmod") < indexOf(shipped, "admin"));
            boolean clean = true;
            for (RolePlan.Group g : shipped.groups()) for (String n : g.allow()) if (RoleGuard.forbiddenToAllow(n) != null) clean = false;
            check("shipped roles allow nothing console-only and no unsafe wildcard", clean);
            RolePlan.Group def = shipped.groups().stream().filter(g -> g.name().equals("default")).findFirst().orElseThrow();
            check("default blocks UltraCosmetics (deny, which may use a wildcard)", def.deny().contains("ultracosmetics.*")
                    && def.allow().isEmpty());
            check("default denies the console-only commands", def.deny().contains("minecraft.command.op")
                    && def.deny().contains("luckperms.editor") && def.deny().contains("minecraft.command.give")
                    && def.deny().contains("bukkit.command.plugins"));
            RolePlan.Group admin = shipped.groups().stream().filter(g -> g.name().equals("admin")).findFirst().orElseThrow();
            check("admin keeps the single economy nodes but not the umbrella or import",
                    admin.allow().contains("arkaeco.admin.give") && !admin.allow().contains("arkaeco.admin")
                            && !admin.allow().contains("arkaeco.admin.import") && admin.deny().contains("arkaeco.admin.import")
                            && !admin.deny().contains("arkaeco.admin"));
            check("admin cleans up the old wide grants", admin.remove().containsAll(List.of("minecraft.command.*", "luckperms.*", "arkaeco.admin.*", "*")));
            // Staff: ban up to 3 days, mute, vanish, tp and fly; no creative, no op, no permanent ban
            RolePlan.Group staff = shipped.groups().stream().filter(g -> g.name().equals("staff")).findFirst().orElseThrow();
            check("staff may use /staff ban, mute, vanish, fly and tp", staff.allow().containsAll(List.of(
                    "arkastyle.staffcmd.ban", "arkastyle.staffcmd.mute", "arkastyle.staffcmd.vanish", "arkastyle.staffcmd.fly",
                    "arkastyle.staffcmd.tp")));
            check("staff has no direct Essentials power: tempban, mute, tp and fly are denied and not allowed",
                    staff.deny().containsAll(List.of("essentials.tempban", "essentials.tempban.offline", "essentials.mute",
                            "essentials.tp", "essentials.fly", "minecraft.command.teleport"))
                            && staff.allow().stream().noneMatch(n -> n.equals("essentials.tempban") || n.equals("essentials.mute")
                            || n.equals("essentials.tp") || n.equals("essentials.fly") || n.equals("minecraft.command.teleport")));
            check("staff keeps essentials.vanish only for Essentials' own checks (a typed /vanish is cancelled by the plugin)",
                    staff.allow().contains("essentials.vanish"));
            check("staff is not exempt-less: other staff cannot ban or mute staff",
                    staff.allow().containsAll(List.of("essentials.tempban.exempt", "essentials.mute.exempt")));
            check("staff has nothing else (no ban, kick, unban, give or game mode in its allow list)",
                    staff.allow().stream().noneMatch(n -> n.equals("essentials.ban") || n.equals("essentials.banip")
                            || n.equals("essentials.unban") || n.startsWith("essentials.gamemode") || n.equals("minecraft.command.gamemode")
                            || n.equals("minecraft.command.ban") || n.equals("minecraft.command.op") || n.equals("essentials.tempban.unlimited")
                            || n.equals("essentials.mute.unlimited") || n.contains("*")));
            check("staff is denied permanent bans, unbanning and the unlimited tempban",
                    staff.deny().containsAll(List.of("essentials.ban", "essentials.banip", "essentials.unban", "essentials.unbanip",
                            "minecraft.command.ban", "minecraft.command.ban-ip", "essentials.tempban.unlimited")));
            check("staff is denied every game mode, so no creative",
                    staff.deny().containsAll(List.of("essentials.gamemode", "essentials.gamemode.creative", "essentials.gamemode.survival",
                            "essentials.gamemode.adventure", "essentials.gamemode.spectator", "minecraft.command.gamemode",
                            "minecraft.command.defaultgamemode")));
            check("staff is denied op and every console-only command, and give, kill and the other staff-only ones",
                    staff.deny().containsAll(RoleGuard.CONSOLE_ONLY) && staff.deny().contains("minecraft.command.op")
                            && staff.deny().containsAll(RoleGuard.STAFF_ONLY));
            check("staff sits between Builder and Mod and inherits nothing",
                    staff.weight() > 55 && staff.weight() < 60 && staff.parents().isEmpty());
            RolePlan.Group srm = shipped.groups().stream().filter(g -> g.name().equals("srmod")).findFirst().orElseThrow();
            check("only SrMod (and so Admin) may ban for longer than the staff limit", srm.allow().contains("essentials.tempban.unlimited")
                    && indexOf(shipped, "srmod") < indexOf(shipped, "admin"));
            RolePlan.Group modg = shipped.groups().stream().filter(g -> g.name().equals("mod")).findFirst().orElseThrow();
            check("staff cannot ban or mute Mod (and so SrMod and Admin): they are exempt", modg.allow().containsAll(List.of(
                    "essentials.tempban.exempt", "essentials.mute.exempt")));
        }
        // admin: gamemode and vanish (Essentials owns these commands), rank giving only through /style staff
        if (shipped != null) {
            RolePlan.Group adm = shipped.groups().stream().filter(g -> g.name().equals("admin")).findFirst().orElseThrow();
            check("admin may use gamemode and vanish (vanilla and Essentials)", adm.allow().containsAll(List.of(
                    "minecraft.command.gamemode", "essentials.gamemode", "essentials.gamemode.survival", "essentials.gamemode.creative",
                    "essentials.gamemode.adventure", "essentials.gamemode.spectator", "essentials.vanish")));
            check("admin is warned about staff bans and mutes and is exempt from them",
                    adm.allow().containsAll(List.of("arkastyle.staffalert", "essentials.tempban.exempt", "essentials.mute.exempt",
                            "essentials.ban.exempt")));
            check("staff is not given the admin alert permission", shipped.groups().stream()
                    .filter(g -> g.name().equals("staff")).noneMatch(g -> g.allow().contains("arkastyle.staffalert")));
            check("admin gives ranks through /style staff, not with raw LuckPerms parent commands",
                    adm.allow().contains("arkastyle.staff") && !adm.allow().contains("luckperms.user.parent.add")
                            && adm.remove().containsAll(List.of("luckperms.user.parent.add", "luckperms.user.parent.remove",
                            "luckperms.user.parent.addtemp", "luckperms.user.parent.removetemp")));
            try {
                var yy = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File("src/main/resources/config.yml"));
                List<String> grantable = yy.getStringList("staff-ranks.grantable");
                check("/style staff may give the staff rank", grantable.contains("staff"));
                check("shipped staff-ranks list has staff groups and never admin or default",
                        grantable.containsAll(List.of("helper", "mod", "builder", "developer")) && !grantable.contains("admin")
                                && !grantable.contains("default"));
            } catch (RuntimeException e) {
                check("shipped staff-ranks list readable", false);
            }
        }
        List<String> okGroups = List.of("helper", "Mod", "admin", "default", "", "builder");
        check("/style staff gives only listed groups, case ignored", StaffRanks.mayChange("helper", okGroups)
                && StaffRanks.mayChange("MOD", okGroups) && StaffRanks.mayChange(" builder ", okGroups));
        check("/style staff never handles admin or default, even when listed", !StaffRanks.mayChange("admin", okGroups)
                && !StaffRanks.mayChange("ADMIN", okGroups) && !StaffRanks.mayChange("default", okGroups));
        check("/style staff refuses unlisted, empty and null groups", !StaffRanks.mayChange("srmod", okGroups)
                && !StaffRanks.mayChange("", okGroups) && !StaffRanks.mayChange(null, okGroups));
        check("the tab-completion list drops admin, default and blanks", StaffRanks.clean(okGroups).equals(List.of("helper", "mod", "builder")));
        // admin: everything except the dangerous few
        if (shipped != null) {
            RolePlan.Group a2 = shipped.groups().stream().filter(g -> g.name().equals("admin")).findFirst().orElseThrow();
            RolePlan.Group d2 = shipped.groups().stream().filter(g -> g.name().equals("default")).findFirst().orElseThrow();
            check("admin has give, kill, summon, effect, xp, gamerule and teleport commands", a2.allow().containsAll(List.of(
                    "minecraft.command.give", "minecraft.command.kill", "minecraft.command.summon", "minecraft.command.effect",
                    "minecraft.command.xp", "minecraft.command.gamerule", "essentials.tp", "essentials.fly", "essentials.invsee",
                    "essentials.mute", "essentials.tempban", "essentials.heal")));
            boolean noDanger = true;
            for (String n : RoleGuard.CONSOLE_ONLY) if (a2.allow().contains(n)) noDanger = false;
            check("admin has none of the console-only permissions (op, stop, execute, fill, sudo, LuckPerms editing)", noDanger
                    && !a2.allow().contains("essentials.sudo") && !a2.allow().contains("minecraft.command.op")
                    && !a2.allow().contains("minecraft.command.execute"));
            check("admin cannot give the admin rank with LuckPerms commands", !a2.allow().contains("luckperms.user.parent.add")
                    && !a2.allow().contains("luckperms.user.parent.set") && !a2.allow().contains("luckperms.group.parent.add"));
            check("the default group still denies give, kill, summon and the other staff-only commands",
                    d2.deny().containsAll(RoleGuard.STAFF_ONLY));
            check("staff-only commands can be given to a group, console-only ones cannot", RoleGuard.forbiddenToAllow("minecraft.command.give") == null
                    && RoleGuard.forbiddenToAllow("minecraft.command.op") != null && RoleGuard.forbiddenToAllow("essentials.sudo") != null
                    && RoleGuard.forbiddenToAllow("essentials.eco") != null);
            boolean noOverlap = true;
            for (String n : RoleGuard.STAFF_ONLY) if (RoleGuard.CONSOLE_ONLY.contains(n)) noOverlap = false;
            check("the console-only and staff-only lists do not overlap", noOverlap);
            boolean wild = true;
            for (String n : a2.allow()) if (n.contains("*")) wild = false;
            check("admin allow list has no wildcard", wild);
        }
        check("@staff-only expands to give and kill but not to op", RoleGuard.expand(List.of("@staff-only")).contains("minecraft.command.give")
                && !RoleGuard.expand(List.of("@staff-only")).contains("minecraft.command.op")
                && !RoleGuard.expand(List.of("@console-only")).contains("minecraft.command.give"));
        check("op cannot be allowed", !plan("g:\n  allow: [minecraft.command.op]").errors().isEmpty());
        check("a wildcard cannot be allowed", !plan("g:\n  allow: [arka.*]").errors().isEmpty());
        check("ultracosmetics.* and gadget wildcards cannot be allowed", !plan("g:\n  allow: [ultracosmetics.*]").errors().isEmpty()
                && !plan("g:\n  allow: [ultracosmetics.gadgets.*]").errors().isEmpty()
                && !plan("g:\n  allow: [ultracosmetics.pets.*]").errors().isEmpty());
        check("the safe cosmetic wildcards can be allowed", plan("g:\n  allow: [ultracosmetics.hats.*, ultracosmetics.emotes.*, ultracosmetics.suits.*]").errors().isEmpty());
        if (shipped != null) {
            RolePlan.Group ultraG = shipped.groups().stream().filter(g -> g.name().equals("ultra")).findFirst().orElseThrow();
            boolean noOtherUc = true;
            for (RolePlan.Group g : shipped.groups()) {
                if (g.name().equals("ultra")) continue;
                for (String n : g.allow()) if (n.startsWith("ultracosmetics.")) noOtherUc = false;
            }
            check("ultra gets the menu and hats, emotes and suits", ultraG.allow().containsAll(List.of("ultracosmetics.openmenu",
                    "ultracosmetics.hats.*", "ultracosmetics.emotes.*", "ultracosmetics.suits.*")));
            check("only ultra is given UltraCosmetics permissions directly (others inherit)", noOtherUc);
        }
        check("the star cannot be allowed", !plan("g:\n  allow: [\"*\"]").errors().isEmpty());
        check("LuckPerms permission editing cannot be allowed", !plan("g:\n  allow: [luckperms.user.permission.set]").errors().isEmpty());
        check("the economy umbrella cannot be allowed", !plan("g:\n  allow: [arkaeco.admin]").errors().isEmpty());
        check("a normal permission is fine", plan("g:\n  allow: [arka.stats]").errors().isEmpty());
        check("allow and deny of the same node is refused", !plan("g:\n  allow: [arka.stats]\n  deny: [arka.stats]").errors().isEmpty());
        check("a parent loop is refused", !plan("a:\n  parents: [b]\nb:\n  parents: [a]").errors().isEmpty());
        check("a group cannot be its own parent", !plan("a:\n  parents: [a]").errors().isEmpty());
        check("a bad group name is refused", !plan("\"bad name\":\n  weight: 1").errors().isEmpty());
        check("a bad weight is refused", !plan("a:\n  weight: -1").errors().isEmpty() && !plan("a:\n  weight: x").errors().isEmpty());
        check("LuckPerms special nodes are refused", !plan("a:\n  allow: [group.admin]").errors().isEmpty()
                && !plan("a:\n  allow: [weight.99]").errors().isEmpty());
        check("remove may list a wildcard", plan("a:\n  remove: [\"luckperms.*\"]").errors().isEmpty());
        check("a missing roles section is an error", RolePlan.parse(null).errors().size() == 1);
        check("@console-only expands and keeps other entries", RoleGuard.expand(List.of("@console-only", "Arka.Stats", "arka.stats"))
                .contains("minecraft.command.stop") && RoleGuard.expand(List.of("Arka.Stats", "arka.stats")).size() == 1);
        check("a parent outside the section is reported for LuckPerms to check",
                plan("a:\n  parents: [somethingelse]").externalParents().equals(List.of("somethingelse")));
    }

    private static StaffActions.Action act(String label, String... args) {
        return StaffActions.parse(label, args);
    }

    private static void staffActionChecks() {
        var tb = act("tempban", "Griefer", "2d", "griefing", "the", "spawn");
        check("tempban: target, time and reason are read", tb != null && tb.kind() == StaffActions.Kind.BAN && tb.target().equals("Griefer")
                && tb.duration().equals("2d") && tb.reason().equals("griefing the spawn"));
        check("tempban text reads well", tb.text().equals("temp-banned Griefer for 2d: griefing the spawn"));
        check("tempban with no reason has an empty reason", act("tempban", "Bob", "1d").reason().isEmpty());
        check("aliases are folded in (etempban, essentials:tempban, /v, /pardon, /s)",
                act("etempban", "A", "1d", "x").command().equals("tempban") && act("essentials:tempban", "A", "1d", "x") != null
                        && act("v").kind() == StaffActions.Kind.VANISH && act("pardon", "A").command().equals("unban")
                        && act("s", "A").command().equals("tphere"));
        check("a plain ban is called permanent", act("ban", "A", "cheating").text().equals("banned A permanently: cheating"));
        check("mute with a time and a reason", act("mute", "Spammer", "10m", "spam").text().equals("muted Spammer for 10m: spam")
                && act("mute", "Spammer", "10m", "spam").duration().equals("10m"));
        check("mute with only a reason: the second word is the reason", act("mute", "Spammer", "caps").reason().equals("caps"));
        check("mute on its own is a toggle with no reason", act("mute", "Spammer").text().equals("toggled mute on Spammer")
                && act("mute", "Spammer").reason().isEmpty());
        check("vanish and fly are read", act("vanish").text().equals("toggled vanish") && act("fly", "off").text().equals("turned fly off")
                && act("fly", "Bob").text().equals("toggled fly for Bob") && act("fly", "Bob", "on").text().equals("turned fly on for Bob"));
        check("teleports are read", act("tp", "Bob").text().equals("teleported to Bob") && act("tp", "Bob", "Sue").text().equals("teleported Bob to Sue")
                && act("tp", "100", "64", "-20").text().equals("teleported to coordinates 100 64 -20")
                && act("tphere", "Bob").text().equals("teleported Bob to themselves") && act("tp", "~", "~5", "~").text().startsWith("teleported to coordinates"));
        check("other commands are not tracked", act("home") == null && act("gamemode", "creative") == null && act("msg", "a", "b") == null);
        check("permission nodes for a typed command", java.util.Arrays.equals(StaffActions.permissionNodes("/Tempban"),
                new String[]{"essentials.tempban", "minecraft.command.tempban"})
                && StaffActions.permissionNodes("v")[0].equals("essentials.vanish"));

        check("/staff ban rules: time needed, 3 days at most, reason needed",
                "staff-ban-usage".equals(StaffActions.problem(true, "", "griefing", 3, 3))
                        && "staff-ban-usage".equals(StaffActions.problem(true, "soon", "griefing", 3, 3))
                        && "staff-ban-too-long".equals(StaffActions.problem(true, "73h", "griefing", 3, 3))
                        && "staff-ban-too-long".equals(StaffActions.problem(true, "1mo", "griefing", 3, 3))
                        && "staff-need-reason".equals(StaffActions.problem(true, "3d", "", 3, 3))
                        && "staff-need-reason".equals(StaffActions.problem(true, "3d", "ab", 3, 3))
                        && StaffActions.problem(true, "3d", "griefing", 3, 3) == null
                        && StaffActions.problem(true, "30m", "bad", 3, 3) == null);
        check("/staff mute rules: only a reason is needed",
                "staff-need-reason".equals(StaffActions.problem(false, "", "  ", 3, 3))
                        && StaffActions.problem(false, "", "spam", 3, 3) == null
                        && StaffActions.problem(false, "10m", "spam", 3, 3) == null);
        check("ban times: 3d, 72h, 1d12h, 90m", StaffActions.seconds("3d") == 259200 && StaffActions.seconds("72h") == 259200
                && StaffActions.seconds("1d12h") == 129600 && StaffActions.seconds("90m") == 5400);
        check("ban times: 1mo is 30 days, 1w is 7, 1y is 365, upper case works",
                StaffActions.seconds("1mo") == 30 * 86400 && StaffActions.seconds("1w") == 7 * 86400
                        && StaffActions.seconds("1y") == 365 * 86400 && StaffActions.seconds("2D") == 172800);
        check("ban times: junk and empty are rejected", StaffActions.seconds("") == -1 && StaffActions.seconds(null) == -1
                && StaffActions.seconds("soon") == -1 && StaffActions.seconds("3") == -1 && StaffActions.seconds("d3") == -1
                && StaffActions.seconds("3d x") == -1 && StaffActions.seconds("99999999999d") == -1);
        check("3 days is allowed, 3 days and a second is not", StaffActions.seconds("3d") <= 3 * 86400 && StaffActions.seconds("72h1s") > 3 * 86400);

        var home = StaffActions.generic("/Home", new String[]{"base"}, false);
        check("any other command is logged with its arguments", home.kind() == StaffActions.Kind.COMMAND
                && home.text().equals("typed /home base") && home.command().equals("home"));
        check("a command with no arguments reads cleanly", StaffActions.generic("spawn", new String[0], false).text().equals("typed /spawn"));
        var login = StaffActions.generic("login", new String[]{"hunter2"}, true);
        check("a login command is logged without the password", login.text().equals("typed /login (arguments hidden)")
                && !login.text().contains("hunter2") && StaffActions.HIDDEN.containsAll(List.of("login", "register", "changepassword", "l")));
        check("namespaced commands are named plainly", StaffActions.generic("essentials:home", new String[0], false).command().equals("home"));
        check("joining and leaving are logged", StaffActions.session(true).text().equals("joined the server")
                && StaffActions.session(false).text().equals("left the server") && StaffActions.session(true).kind() == StaffActions.Kind.SESSION);
        check("the forbidden list holds the commands the Staff rank is denied",
                StaffActions.FORBIDDEN.containsAll(List.of("gamemode", "gm", "op", "give", "ban", "unban", "stop", "execute", "lp")));
        check("kinds have the ids used in the config", StaffActions.Kind.COMMAND.id().equals("command")
                && StaffActions.Kind.SESSION.id().equals("session"));
        check("line breaks cannot split one action over two log lines",
                !StaffActions.clean("a\nb\r\nc").contains("\n") && StaffActions.clean("a\nb").equals("a b"));
        String line = StaffActions.line("2026-11-26 14:03:11", tb, "Steve", "uuid-1", "/tempban Griefer 2d griefing", "world 120 64 -30");
        check("log line has time, kind, who, what, typed command, place and uuid", line.equals(
                "2026-11-26 14:03:11 | BAN | Steve | temp-banned Griefer for 2d: griefing the spawn | /tempban Griefer 2d griefing | world 120 64 -30 | uuid-1"));

        try {
            java.io.File dir = java.nio.file.Files.createTempDirectory("stafflog").toFile();
            StaffLog log = new StaffLog(dir);
            log.write("first line");
            log.write("second line");
            log.close();
            var written = java.nio.file.Files.readAllLines(log.file().toPath());
            check("the staff log is its own file, logs/staff-actions.log, one line per action",
                    log.file().getName().equals("staff-actions.log") && log.file().getParentFile().getName().equals("logs")
                            && written.equals(List.of("first line", "second line")));
        } catch (java.io.IOException e) {
            check("the staff log can be written: " + e, false);
        }
    }

    public static void main(String[] args) {
        Ranks.Rank pro = new Ranks.Rank("pro", "pro", "Pro", "[Pro] ", "{name}", 2000, List.of("craft", "trash", "anvil"));
        Ranks.Rank ultra = new Ranks.Rank("ultra", "ultra", "Ultra", "[Ultra] ", "{name}", 5000,
                List.of("enchant", "ec", "grindstone"));
        Map<String, Ranks.Choice> colors = Map.of(
                "white", new Ranks.Choice("white", "White", "<white>", "WHITE_DYE", "free"),
                "gold", new Ranks.Choice("gold", "Gold", "<gold>", "ORANGE_DYE", "pro"),
                "rainbow", new Ranks.Choice("rainbow", "Rainbow", "<rainbow>", "NETHER_STAR", "ultra"),
                "broken", new Ranks.Choice("broken", "Broken", "<red>", "PAPER", "gold-card"));
        Map<String, Ranks.Choice> tags = Map.of("star", new Ranks.Choice("star", "Star", "[*] ", "GLOWSTONE_DUST", "pro"),
                "raja", new Ranks.Choice("raja", "Raja", "[R] ", "GOLDEN_HELMET", "ultra"));
        Ranks r = new Ranks(List.of(pro, ultra), colors, tags);

        check("no group means a free player", r.highest(g -> false) == null);
        check("pro group gives the pro rank", r.highest(g -> g.equals("pro")) == pro);
        check("both groups: the highest rank wins", r.highest(g -> true) == ultra);
        check("ultra includes the pro commands", r.commands(ultra).containsAll(Set.of("craft", "trash", "anvil", "enchant", "ec")));
        check("pro does not get the ultra commands", !r.commands(pro).contains("ec") && !r.commands(pro).contains("enchant"));
        check("a free player has no rank commands", r.commands(null).isEmpty());
        check("lowest rank with /ec is ultra", r.lowestWithCommand("ec") == ultra);
        check("lowest rank with /craft is pro", r.lowestWithCommand("CRAFT") == pro);
        check("nobody gives an unknown command", r.lowestWithCommand("fly") == null);

        check("free colour is usable by everyone", r.usableColor("white", null) != null);
        check("pro colour needs pro", r.usableColor("gold", null) == null && r.usableColor("gold", pro) != null);
        check("ultra colour is locked for pro", r.usableColor("rainbow", pro) == null && r.usableColor("rainbow", ultra) != null);
        check("ultra can use pro things", r.usableTag("star", ultra) != null);
        check("a colour with an unknown rank is usable by nobody", r.usableColor("broken", ultra) == null);
        check("an unknown id gives nothing", r.usableColor("nope", ultra) == null && r.usableTag(null, ultra) == null);
        check("a rank that ended switches the tag off", r.usableTag("raja", pro) == null && r.usableTag("raja", null) == null);

        check("monthly bonus: first time gives the full amount", MonthlyBonus.due(2000, null, 0, "2026-11") == 2000);
        check("monthly bonus: same month gives nothing again", MonthlyBonus.due(2000, "2026-11", 2000, "2026-11") == 0);
        check("monthly bonus: new month gives the full amount", MonthlyBonus.due(2000, "2026-11", 2000, "2026-12") == 2000);
        check("monthly bonus: upgrade in the month gives the difference", MonthlyBonus.due(5000, "2026-11", 2000, "2026-11") == 3000);
        check("monthly bonus: downgrade never takes anything", MonthlyBonus.due(2000, "2026-11", 5000, "2026-11") == 0);
        check("monthly bonus: missed months are not saved up", MonthlyBonus.due(2000, "2026-08", 2000, "2026-11") == 2000);

        check("a Floodgate UUID is Bedrock", ArkaSMPStyle.isBedrock(new UUID(0L, 123456789L)));
        check("a normal UUID is Java", !ArkaSMPStyle.isBedrock(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")));

        UUID u = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        check("uuid with dashes is read", u.equals(GrantArgs.uuid("069a79f4-44e9-4726-a5be-fca90e38aaf5")));
        check("uuid without dashes is read", u.equals(GrantArgs.uuid("069a79f444e94726a5befca90e38aaf5")));
        check("a player name is not a uuid", GrantArgs.uuid("Notch") == null && GrantArgs.uuid("") == null && GrantArgs.uuid(null) == null);
        check("a 36 character non-uuid is rejected", GrantArgs.uuid("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz") == null);
        check("days 30 is fine", GrantArgs.days("30", 366) == 30);
        check("days 0, -5, 367 and text are rejected", GrantArgs.days("0", 366) == -1 && GrantArgs.days("-5", 366) == -1
                && GrantArgs.days("367", 366) == -1 && GrantArgs.days("thirty", 366) == -1);

        roleChecks();
        staffActionChecks();

        check("Essentials limit: -1 and 0 are a problem", EssentialsLimits.problem(-1) != null && EssentialsLimits.problem(0) != null);
        check("Essentials limit: more than 3 days is a problem, 3 days and less are fine",
                EssentialsLimits.problem(259201) != null && EssentialsLimits.problem(259200) == null && EssentialsLimits.problem(86400) == null);
        check("Essentials limit: the message names the exact setting and value",
                EssentialsLimits.problem(-1).contains("max-tempban-time: 259200"));
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
