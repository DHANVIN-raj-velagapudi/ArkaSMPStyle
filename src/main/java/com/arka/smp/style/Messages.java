package com.arka.smp.style;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Config-driven MiniMessage text. Placeholder values are escaped so players cannot inject tags, except values passed with raw(). */
final class Messages {
    static final MiniMessage MM = MiniMessage.miniMessage();
    private final ArkaSMPStyle plugin;

    Messages(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    /** Replaces {key} with value. Values ending in "!" in the key are inserted as trusted MiniMessage. */
    private static String fill(String raw, String[] kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = kv[i];
            boolean trusted = key.endsWith("!");
            String name = trusted ? key.substring(0, key.length() - 1) : key;
            raw = raw.replace("{" + name + "}", trusted ? kv[i + 1] : MM.escapeTags(kv[i + 1]));
        }
        return raw;
    }

    private String template(String key, String[] kv) {
        String raw = plugin.getConfig().getString("messages." + key);
        return fill(raw == null ? "<red>Missing message: " + key : raw, kv);
    }

    Component mm(String raw, String... kv) {
        return MM.deserialize(fill(raw, kv));
    }

    Component plain(String key, String... kv) {
        return MM.deserialize(template(key, kv));
    }

    Component get(String key, String... kv) {
        return MM.deserialize(plugin.getConfig().getString("messages.prefix", "") + template(key, kv));
    }

    void send(CommandSender to, String key, String... kv) {
        to.sendMessage(get(key, kv));
    }
}
