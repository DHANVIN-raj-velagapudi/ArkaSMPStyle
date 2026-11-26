package com.arka.smp.style;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * The little this plugin needs to ask Essentials: whether a player is muted right now (Essentials' /mute is a toggle,
 * so /staff mute and /staff unmute must know). Done by reflection so Essentials is not needed to build or to start, and
 * an Essentials update that renames something gives "unknown" instead of an error.
 */
final class EssentialsLink {
    private EssentialsLink() {
    }

    /** True or false when Essentials answers, null when Essentials is missing or does not know the player. */
    static Boolean isMuted(UUID id) {
        try {
            Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess == null) return null;
            Object user = ess.getClass().getMethod("getUser", UUID.class).invoke(ess, id);
            if (user == null) return null;
            Object muted = user.getClass().getMethod("isMuted").invoke(user);
            return muted instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
