package com.arka.smp.style;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * %style_rank%         rank id: pro, ultra or free
 * %style_rank_name%    rank name without colours
 * %style_badge%        rank badge, with colour codes (for TAB and scoreboards)
 * %style_badge_mm%     rank badge as MiniMessage text
 * %style_tag%          the tag the player picked (colour codes)
 * %style_tag_mm%       the tag as MiniMessage text
 * %style_prefix%       badge + tag, ready for a TAB prefix
 * %style_is_bedrock%   true for Bedrock (Floodgate) players, false for Java: use it as a TAB condition
 * %style_platform%     bedrock or java
 */
final class StyleExpansion extends PlaceholderExpansion {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('§').hexColors().build();
    private final ArkaSMPStyle plugin;

    StyleExpansion(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "style";
    }

    @Override
    public String getAuthor() {
        return "Arka";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static String legacy(String mm) {
        return mm.isEmpty() ? "" : LEGACY.serialize(Messages.MM.deserialize(mm));
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (offline == null) return "";
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("is_bedrock")) return String.valueOf(ArkaSMPStyle.isBedrock(offline.getUniqueId()));
        if (key.equals("platform")) return ArkaSMPStyle.isBedrock(offline.getUniqueId()) ? "bedrock" : "java";
        if (!(offline instanceof Player p)) return "";
        Ranks.Rank rank = plugin.rankOf(p);
        Ranks.Choice tag = plugin.ranks().usableTag(plugin.profiles().tag(p.getUniqueId()), rank);
        String badge = rank == null ? "" : rank.badge();
        String tagMm = tag == null ? "" : tag.format();
        return switch (key) {
            case "rank" -> rank == null ? "free" : rank.id();
            case "rank_name" -> rank == null ? "Free"
                    : PlainTextComponentSerializer.plainText().serialize(Messages.MM.deserialize(rank.name()));
            case "badge" -> legacy(badge);
            case "badge_mm" -> badge;
            case "tag" -> legacy(tagMm);
            case "tag_mm" -> tagMm;
            case "prefix" -> legacy(badge + tagMm);
            default -> null;
        };
    }
}
