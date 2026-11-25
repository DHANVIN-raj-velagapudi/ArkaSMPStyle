package com.arka.smp.style;

import com.arka.smp.core.api.ClaimBlockBonus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.YearMonth;
import java.util.UUID;

/**
 * The monthly claim block bonus of the paid ranks: once per calendar month, the first time the player is online
 * with an active rank. A player who upgrades in the middle of the month gets the difference. Missed months are lost.
 */
final class MonthlyBonus implements Listener {
    enum Result { GIVEN, ALREADY, NO_RANK, DISABLED, ERROR }

    private final ArkaSMPStyle plugin;

    MonthlyBonus(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    static String monthKey() {
        return YearMonth.now().toString();
    }

    /** Pure rule: how many blocks to give now. */
    static long due(long rankBlocks, String lastMonth, long givenThisMonth, String currentMonth) {
        long already = currentMonth.equals(lastMonth) ? givenThisMonth : 0;
        return Math.max(0, rankBlocks - already);
    }

    Result claim(Player p) {
        if (!plugin.getConfig().getBoolean("bonus.enabled", true)) return Result.DISABLED;
        Ranks.Rank rank = plugin.rankOf(p);
        if (rank == null || rank.monthlyBlocks() <= 0) return Result.NO_RANK;
        UUID id = p.getUniqueId();
        String month = monthKey();
        long give = due(rank.monthlyBlocks(), plugin.profiles().bonusMonth(id), plugin.profiles().bonusGiven(id), month);
        if (give <= 0) return Result.ALREADY;
        ClaimBlockBonus service = Bukkit.getServicesManager().load(ClaimBlockBonus.class);
        if (service == null) return Result.ERROR;
        service.add(id, give);
        long total = (month.equals(plugin.profiles().bonusMonth(id)) ? plugin.profiles().bonusGiven(id) : 0) + give;
        plugin.profiles().setBonus(id, month, total);
        plugin.messages().send(p, "bonus-given", "rank!", rank.name(), "blocks", String.format("%,d", give));
        return Result.GIVEN;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("bonus.enabled", true)) return;
        long delay = Math.max(1, plugin.getConfig().getInt("bonus.announce-delay-seconds", 5)) * 20L;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            claim(p);
            noteExpired(p);
        }, delay);
    }

    /** A player whose rank ended still has their tag or colour saved; tell them it is switched off until renewal. */
    private void noteExpired(Player p) {
        Ranks ranks = plugin.ranks();
        Ranks.Rank rank = plugin.rankOf(p);
        UUID id = p.getUniqueId();
        String tag = plugin.profiles().tag(id);
        String color = plugin.profiles().color(id);
        boolean lostTag = tag != null && ranks.tags().containsKey(tag) && ranks.usableTag(tag, rank) == null;
        boolean lostColor = color != null && ranks.colors().containsKey(color) && ranks.usableColor(color, rank) == null;
        if (lostTag || lostColor) plugin.messages().send(p, "expired-note");
    }
}
