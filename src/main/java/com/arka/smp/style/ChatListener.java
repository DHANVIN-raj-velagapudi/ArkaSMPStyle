package com.arka.smp.style;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Puts the rank badge, tag, name style and chat colour into chat. Switch off with chat.enabled: false. */
final class ChatListener implements Listener {
    private final ArkaSMPStyle plugin;

    ChatListener(ArkaSMPStyle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;
        // plain text only: a player can never smuggle formatting or click events into chat
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) -> plugin.renderChat(source, text)));
    }
}
