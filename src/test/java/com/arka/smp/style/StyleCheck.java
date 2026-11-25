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
            check("shipped roles: 11 groups", shipped.groups().size() == 11);
            check("shipped roles: parents come before children", indexOf(shipped, "pro") < indexOf(shipped, "ultra")
                    && indexOf(shipped, "ultra") < indexOf(shipped, "youtuber") && indexOf(shipped, "helper") < indexOf(shipped, "mod")
                    && indexOf(shipped, "mod") < indexOf(shipped, "srmod") && indexOf(shipped, "ultra") < indexOf(shipped, "srmod")
                    && indexOf(shipped, "srmod") < indexOf(shipped, "admin"));
            boolean clean = true;
            for (RolePlan.Group g : shipped.groups()) for (String n : g.allow()) if (RoleGuard.forbiddenToAllow(n) != null) clean = false;
            check("shipped roles allow nothing console-only and no unsafe wildcard", clean);
            RolePlan.Group def = shipped.groups().stream().filter(g -> g.name().equals("default")).findFirst().orElseThrow();
            check("default denies the console-only commands", def.deny().contains("minecraft.command.op")
                    && def.deny().contains("luckperms.editor") && def.deny().contains("minecraft.command.give")
                    && def.deny().contains("bukkit.command.plugins"));
            RolePlan.Group admin = shipped.groups().stream().filter(g -> g.name().equals("admin")).findFirst().orElseThrow();
            check("admin keeps the single economy nodes but not the umbrella or import",
                    admin.allow().contains("arkaeco.admin.give") && !admin.allow().contains("arkaeco.admin")
                            && !admin.allow().contains("arkaeco.admin.import") && admin.deny().contains("arkaeco.admin.import")
                            && !admin.deny().contains("arkaeco.admin"));
            check("admin cleans up the old wide grants", admin.remove().containsAll(List.of("minecraft.command.*", "luckperms.*", "arkaeco.admin.*", "*")));
        }
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

        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
