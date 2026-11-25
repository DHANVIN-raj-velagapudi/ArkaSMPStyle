package com.arka.smp.style;

import com.arka.smp.core.menu.Items;
import com.arka.smp.core.menu.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The /style menus. Tap only (no typing), so they work for Bedrock players too. */
final class StyleMenus {
    private final ArkaSMPStyle plugin;

    StyleMenus(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    private Component title() {
        return Messages.MM.deserialize(plugin.getConfig().getString("menu.title", "Style"));
    }

    private static Component text(String s, NamedTextColor c) {
        return Component.text(s, c);
    }

    private static Material material(String name, Material fallback) {
        Material m = Material.matchMaterial(name == null ? "" : name);
        return m != null && m.isItem() ? m : fallback;
    }

    private static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    // ---- main --------------------------------------------------------------------------------

    void main(Player p) {
        Ranks.Rank rank = plugin.rankOf(p);
        Menu m = new Menu(title(), 3);
        Component rankName = rank == null ? text("Free", NamedTextColor.WHITE) : Messages.MM.deserialize(rank.name());
        List<Component> lore = new ArrayList<>();
        if (rank == null) {
            lore.add(text("Pro and Ultra add tags, colours,", NamedTextColor.GRAY));
            lore.add(text("rank commands and a monthly", NamedTextColor.GRAY));
            lore.add(text("claim block bonus.", NamedTextColor.GRAY));
        } else {
            Ranks ranks = plugin.ranks();
            lore.add(text("Commands: /" + String.join(", /", ranks.commands(rank)), NamedTextColor.GRAY));
            if (rank.monthlyBlocks() > 0) {
                lore.add(text("Monthly bonus: " + String.format("%,d", rank.monthlyBlocks()) + " claim blocks", NamedTextColor.GOLD));
            }
        }
        String iconName = rank == null
                ? plugin.getConfig().getString("menu.free-icon", "IRON_INGOT")
                : plugin.getConfig().getString("ranks." + rank.id() + ".icon", "NETHER_STAR");
        m.set(4, Items.of(material(iconName, Material.NETHER_STAR), Component.text("Your rank: ").append(rankName), lore), null);
        m.set(10, Items.of(Material.NAME_TAG, text("Tags", NamedTextColor.YELLOW),
                List.of(text("Pick the tag shown in chat", NamedTextColor.GRAY))), this::tags);
        m.set(12, Items.of(Material.CYAN_DYE, text("Chat colour", NamedTextColor.AQUA),
                List.of(text("Pick the colour of your messages", NamedTextColor.GRAY))), this::colors);
        m.set(14, Items.of(Material.GRASS_BLOCK, text("Monthly bonus", NamedTextColor.GREEN),
                List.of(text("Claim this months claim blocks", NamedTextColor.GRAY))), pl -> {
            pl.closeInventory();
            pl.performCommand("style bonus");
        });
        if (Bukkit.getPluginManager().getPlugin("UltraCosmetics") != null) {
            m.set(16, Items.of(Material.CHEST, text("Cosmetics", NamedTextColor.LIGHT_PURPLE),
                    List.of(text("Hats, effects, pets and more", NamedTextColor.GRAY))), pl -> {
                pl.closeInventory();
                pl.performCommand("uc menu");
            });
        }
        m.fill(Items.filler()).open(p);
    }

    // ---- tags and colours --------------------------------------------------------------------

    void tags(Player p) {
        list(p, true);
    }

    void colors(Player p) {
        list(p, false);
    }

    private void list(Player p, boolean tag) {
        Ranks ranks = plugin.ranks();
        Ranks.Rank rank = plugin.rankOf(p);
        var map = tag ? ranks.tags() : ranks.colors();
        String current = tag ? plugin.profiles().tag(p.getUniqueId()) : plugin.profiles().color(p.getUniqueId());
        Menu m = new Menu(title().append(text(tag ? " - Tags" : " - Colours", NamedTextColor.GRAY)), 6);
        int slot = 0;
        for (Ranks.Choice c : map.values()) {
            if (slot >= 45) break;
            boolean ok = ranks.canUse(c, rank);
            Ranks.Rank need = ranks.rankFor(c);
            List<Component> lore = new ArrayList<>();
            // preview: how the choice looks
            lore.add(Messages.MM.deserialize(tag ? c.format() + "<white>" + p.getName() : c.format() + "This is how your chat looks"));
            if (!ok) {
                lore.add(Component.text("Needs the ").color(NamedTextColor.RED)
                        .append(need == null ? text("?", NamedTextColor.RED) : Messages.MM.deserialize(need.name()))
                        .append(text(" rank", NamedTextColor.RED)));
            } else {
                boolean selected = c.id().equals(current);
                lore.add(text(selected ? "Selected" : "Tap to use", selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            ItemStack item = Items.of(ok ? material(c.material(), Material.PAPER) : Material.GRAY_DYE,
                    text(c.name(), ok ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY), lore);
            if (ok && c.id().equals(current)) glow(item);
            final Ranks.Choice choice = c;
            m.set(slot++, item, pl -> {
                choose(plugin, pl, choice, tag);
                list(pl, tag);
            });
        }
        m.set(45, Items.of(Material.BARRIER, text("Remove", NamedTextColor.RED),
                List.of(text("Use no " + (tag ? "tag" : "colour"), NamedTextColor.GRAY))), pl -> {
            choose(plugin, pl, null, tag);
            list(pl, tag);
        });
        m.set(49, Items.of(Material.ARROW, text("Back", NamedTextColor.YELLOW), null), this::main);
        m.fill(Items.filler()).open(p);
    }

    /** Applies a pick (null = remove) after checking the rank. Used by the menu and by the typed command. */
    static void choose(ArkaSMPStyle plugin, Player p, Ranks.Choice c, boolean tag) {
        Messages msg = plugin.messages();
        Ranks ranks = plugin.ranks();
        if (c == null) {
            if (tag) plugin.profiles().setTag(p.getUniqueId(), null);
            else plugin.profiles().setColor(p.getUniqueId(), null);
            msg.send(p, tag ? "tag-cleared" : "color-cleared");
            return;
        }
        if (!ranks.canUse(c, plugin.rankOf(p))) {
            Ranks.Rank need = ranks.rankFor(c);
            String needName = need == null ? "?" : need.name();
            if (tag) msg.send(p, "tag-locked", "tag!", c.format().isBlank() ? c.name() : c.format().trim(), "rank!", needName);
            else msg.send(p, "color-locked", "rank!", needName);
            return;
        }
        if (tag) {
            plugin.profiles().setTag(p.getUniqueId(), c.id());
            msg.send(p, "tag-set", "tag!", c.format().trim());
        } else {
            plugin.profiles().setColor(p.getUniqueId(), c.id());
            msg.send(p, "color-set");
        }
    }
}
