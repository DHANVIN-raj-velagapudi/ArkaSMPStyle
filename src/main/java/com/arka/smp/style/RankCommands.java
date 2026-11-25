package com.arka.smp.style;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * /craft /trash /anvil /enchant /grindstone /stonecutter /loom /smithing /cartography /ec.
 * Which command a rank gets is in the config; arkastyle.cmd.&lt;name&gt; gives one command without a rank (staff).
 * They are refused while the player is in PvP combat.
 */
final class RankCommands implements CommandExecutor, TabCompleter, Listener {
    private final ArkaSMPStyle plugin;

    RankCommands(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    /** Marks the trash window so closing it can be recognised. */
    private static final class TrashHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "players-only");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        Ranks ranks = plugin.ranks();
        Ranks.Rank rank = plugin.rankOf(p);
        boolean allowed = p.hasPermission("arkastyle.cmd." + name) || ranks.commands(rank).contains(name);
        if (!allowed) {
            Ranks.Rank need = ranks.lowestWithCommand(name);
            if (need == null) msg.send(p, "no-permission");
            else msg.send(p, "needs-rank", "command", name, "rank!", need.name());
            return true;
        }
        if (plugin.getConfig().getBoolean("rank-commands.block-in-combat", true)
                && !p.hasPermission("arkastyle.bypass.combat") && plugin.inCombat(p.getUniqueId())) {
            msg.send(p, "in-combat", "command", name);
            return true;
        }
        switch (name) {
            case "craft" -> p.openWorkbench(null, true);
            case "anvil" -> p.openAnvil(null, true);
            case "enchant" -> p.openEnchanting(null, true);
            case "grindstone" -> p.openGrindstone(null, true);
            case "stonecutter" -> p.openStonecutter(null, true);
            case "loom" -> p.openLoom(null, true);
            case "smithing" -> p.openSmithingTable(null, true);
            case "cartography" -> p.openCartographyTable(null, true);
            case "ec" -> p.openInventory(p.getEnderChest());
            case "trash" -> {
                TrashHolder holder = new TrashHolder();
                holder.inventory = Bukkit.createInventory(holder, 54, msg.plain("trash-title"));
                p.openInventory(holder.inventory);
            }
            default -> msg.send(p, "no-permission");
        }
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof TrashHolder)) return;
        int count = 0;
        for (ItemStack it : e.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) count += it.getAmount();
        }
        e.getInventory().clear();
        if (count > 0 && e.getPlayer() instanceof Player p) {
            plugin.messages().send(p, "trash-done", "count", String.valueOf(count));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
