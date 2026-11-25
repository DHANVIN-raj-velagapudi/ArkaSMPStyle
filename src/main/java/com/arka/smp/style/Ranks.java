package com.arka.smp.style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The paid ranks (lowest first) and the things players can pick. Plain logic, no server code, so it can be tested
 * on its own. A higher rank includes everything of the lower ones.
 */
final class Ranks {
    /** One paid rank. {@code nameFormat} contains {name}. */
    record Rank(String id, String group, String name, String badge, String nameFormat, long monthlyBlocks,
                List<String> commands) {
    }

    /** A chat colour or a tag. {@code minRank} is "free" or a rank id. */
    record Choice(String id, String name, String format, String material, String minRank) {
    }

    private final List<Rank> ranks;
    private final Map<String, Choice> colors;
    private final Map<String, Choice> tags;

    Ranks(List<Rank> ranks, Map<String, Choice> colors, Map<String, Choice> tags) {
        this.ranks = List.copyOf(ranks);
        this.colors = new LinkedHashMap<>(colors);
        this.tags = new LinkedHashMap<>(tags);
    }

    List<Rank> all() {
        return ranks;
    }

    Map<String, Choice> colors() {
        return colors;
    }

    Map<String, Choice> tags() {
        return tags;
    }

    Rank byId(String id) {
        if (id == null) return null;
        for (Rank r : ranks) if (r.id().equalsIgnoreCase(id)) return r;
        return null;
    }

    /** 0 = free, 1 = first rank, 2 = second rank ... */
    int level(Rank r) {
        return r == null ? 0 : ranks.indexOf(r) + 1;
    }

    /** Level needed for a choice ("free" = 0; an unknown rank id means nobody can use it: Integer.MAX_VALUE). */
    int levelOf(String minRank) {
        if (minRank == null || minRank.equalsIgnoreCase("free")) return 0;
        Rank r = byId(minRank);
        return r == null ? Integer.MAX_VALUE : level(r);
    }

    /** The highest rank whose group the player is in, or null (free). */
    Rank highest(Predicate<String> inGroup) {
        Rank best = null;
        for (Rank r : ranks) if (inGroup.test(r.group())) best = r;
        return best;
    }

    /** Commands of this rank and every lower rank. */
    Set<String> commands(Rank rank) {
        Set<String> out = new LinkedHashSet<>();
        if (rank == null) return out;
        for (int i = 0; i < level(rank); i++) out.addAll(ranks.get(i).commands());
        return out;
    }

    /** The lowest rank that gives this command, or null if no rank does. */
    Rank lowestWithCommand(String command) {
        String c = command.toLowerCase(Locale.ROOT);
        for (Rank r : ranks) if (r.commands().contains(c)) return r;
        return null;
    }

    boolean canUse(Choice c, Rank player) {
        return level(player) >= levelOf(c.minRank());
    }

    /** Choice the player may still use, or null (not set, unknown, or the rank ended). */
    Choice usableColor(String id, Rank player) {
        return usable(colors, id, player);
    }

    Choice usableTag(String id, Rank player) {
        return usable(tags, id, player);
    }

    private Choice usable(Map<String, Choice> map, String id, Rank player) {
        if (id == null) return null;
        Choice c = map.get(id);
        return c != null && canUse(c, player) ? c : null;
    }

    /** Rank to show in "needs the X rank" messages for a choice. */
    Rank rankFor(Choice c) {
        return byId(c.minRank());
    }

    static List<String> ids(Map<String, Choice> map) {
        return new ArrayList<>(map.keySet());
    }
}
