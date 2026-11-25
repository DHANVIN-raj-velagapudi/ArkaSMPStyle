package com.arka.smp.style;

import com.arka.smp.core.api.CombatStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArkaSMPStyle extends JavaPlugin {
    /** Commands a rank may hand out. */
    static final List<String> RANK_COMMANDS = List.of("craft", "trash", "anvil", "enchant", "grindstone", "stonecutter",
            "loom", "smithing", "cartography", "ec");

    private Messages messages;
    private volatile Ranks ranks;
    private Profiles profiles;
    private MonthlyBonus bonus;
    private RankGrants grants;   // null without LuckPerms
    private RoleSync roleSync;   // null without LuckPerms
    private boolean started;

    @Override
    public void onEnable() {
        try {
            if (!com.arka.smp.core.DataGuard.check(getDataFolder(), getPluginMeta().getVersion(), 1, getLogger()).proceed()) {
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            com.arka.smp.core.ConfigUpdater.update(getDataFolder(), () -> getResource("config.yml"),
                    getPluginMeta().getVersion(), getLogger(), Set.of("ranks", "chat-colors", "tags", "roles"));
            // the three services this plugin needs exist from ArkaSMPCore 1.3.0
            Class.forName("com.arka.smp.core.api.CombatStatus");
            Class.forName("com.arka.smp.core.api.ClaimBlockBonus");
        } catch (LinkageError | ClassNotFoundException e) {
            getLogger().severe("ArkaSMPCore is too old for this ArkaSMPStyle (needs 1.3.0 or newer). Update both plugins.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        started = true;
        messages = new Messages(this);
        profiles = new Profiles(this);
        ranks = loadRanks();
        bonus = new MonthlyBonus(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(bonus, this);
        RankCommands rankCommands = new RankCommands(this);
        pm.registerEvents(rankCommands, this);
        for (String name : RANK_COMMANDS) bind(name, rankCommands, rankCommands);
        StyleCommand style = new StyleCommand(this);
        bind("style", style, style);

        if (pm.getPlugin("PlaceholderAPI") != null) {
            new StyleExpansion(this).register();
            getLogger().info("PlaceholderAPI found: %style_...% placeholders registered.");
        }
        if (pm.getPlugin("LuckPerms") == null) {
            getLogger().warning("LuckPerms was not found. Ranks are read from permission groups ('group.<name>'), "
                    + "so another permission plugin must provide them. /style give and /style remove need LuckPerms.");
        } else {
            grants = new RankGrants();
            roleSync = new RoleSync(this);
        }
        getLogger().info("ArkaSMPStyle enabled: " + ranks.all().size() + " ranks, " + ranks.colors().size()
                + " chat colours, " + ranks.tags().size() + " tags.");
    }

    private void bind(String name, CommandExecutor ex, TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(ex);
            cmd.setTabCompleter(tab);
        }
    }

    @Override
    public void onDisable() {
        if (!started) return;
        if (profiles != null) profiles.save();
    }

    // ---- accessors ---------------------------------------------------------------------------

    Messages messages() {
        return messages;
    }

    Ranks ranks() {
        return ranks;
    }

    Profiles profiles() {
        return profiles;
    }

    MonthlyBonus bonus() {
        return bonus;
    }

    /** Null when LuckPerms is not installed. */
    RankGrants grants() {
        return grants;
    }

    /** Null when LuckPerms is not installed. */
    RoleSync roleSync() {
        return roleSync;
    }

    /** The player's paid rank, or null for a free player. A LuckPerms group gives the node "group.&lt;name&gt;". */
    Ranks.Rank rankOf(Player p) {
        return ranks.highest(group -> p.hasPermission("group." + group));
    }

    boolean inCombat(UUID id) {
        CombatStatus combat = Bukkit.getServicesManager().load(CombatStatus.class);
        return combat != null && combat.inCombat(id);
    }

    /** Floodgate (Bedrock) players have a UUID whose first half is zero. */
    static boolean isBedrock(UUID id) {
        return id.getMostSignificantBits() == 0L;
    }

    void reloadStyle() {
        reloadConfig();
        ranks = loadRanks();
    }

    // ---- chat --------------------------------------------------------------------------------

    /** The full chat line for one message. Name and message are escaped; badge, tag and colour come from the config. */
    Component renderChat(Player p, String text) {
        Ranks r = ranks;
        Ranks.Rank rank = rankOf(p);
        UUID id = p.getUniqueId();
        Ranks.Choice tag = r.usableTag(profiles.tag(id), rank);
        Ranks.Choice color = r.usableColor(profiles.color(id), rank);
        String name = Messages.MM.escapeTags(p.getName());
        if (rank != null) name = rank.nameFormat().replace("{name}", name);
        String colorTag = color != null ? color.format() : getConfig().getString("chat.default-message-color", "<white>");
        String format = getConfig().getString("chat.format", "{badge}{tag}{name}<dark_gray> » </dark_gray>{message}");
        String line = format
                .replace("{badge}", rank == null ? "" : rank.badge())
                .replace("{tag}", tag == null ? "" : tag.format())
                .replace("{name}", name)
                .replace("{message}", colorTag + Messages.MM.escapeTags(text));   // last: the message is never scanned again
        return Messages.MM.deserialize(line);
    }

    // ---- config -> Ranks ---------------------------------------------------------------------

    private Ranks loadRanks() {
        var cfg = getConfig();
        List<Ranks.Rank> list = new ArrayList<>();
        ConfigurationSection rs = cfg.getConfigurationSection("ranks");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                List<String> cmds = new ArrayList<>();
                for (String c : s.getStringList("commands")) {
                    String lower = c.toLowerCase(Locale.ROOT);
                    if (RANK_COMMANDS.contains(lower)) cmds.add(lower);
                    else getLogger().warning("ranks." + id + ".commands: unknown command '" + c + "' ignored.");
                }
                list.add(new Ranks.Rank(id.toLowerCase(Locale.ROOT), s.getString("group", id), s.getString("name", id),
                        s.getString("badge", ""), s.getString("name-format", "{name}"),
                        Math.max(0, s.getLong("monthly-claim-blocks", 0)), cmds));
            }
        }
        Map<String, Ranks.Choice> colors = loadChoices("chat-colors", list);
        Map<String, Ranks.Choice> tags = loadChoices("tags", list);
        return new Ranks(list, colors, tags);
    }

    private Map<String, Ranks.Choice> loadChoices(String section, List<Ranks.Rank> rankList) {
        Map<String, Ranks.Choice> out = new LinkedHashMap<>();
        ConfigurationSection cs = getConfig().getConfigurationSection(section);
        if (cs == null) return out;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(id);
            if (s == null) continue;
            String min = s.getString("rank", "free").toLowerCase(Locale.ROOT);
            boolean known = min.equals("free") || rankList.stream().anyMatch(r -> r.id().equals(min));
            if (!known) getLogger().warning(section + "." + id + ": rank '" + min + "' does not exist, nobody can use it.");
            out.put(id.toLowerCase(Locale.ROOT), new Ranks.Choice(id.toLowerCase(Locale.ROOT), s.getString("name", id),
                    s.getString("format", ""), s.getString("material", "PAPER"), min));
        }
        return out;
    }
}
