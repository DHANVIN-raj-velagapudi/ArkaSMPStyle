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

        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed > 0) System.exit(1);
    }
}
