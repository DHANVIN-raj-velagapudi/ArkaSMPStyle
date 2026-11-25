package com.arka.smp.style;

import java.util.UUID;

/** What each player picked (tag, chat colour) and what monthly bonus they already got. Saved in profiles.yml. */
final class Profiles {
    private final YamlStore store;

    Profiles(ArkaSMPStyle plugin) {
        this.store = new YamlStore(plugin, "profiles.yml");
    }

    private static String path(UUID id, String key) {
        return "players." + id + "." + key;
    }

    synchronized String tag(UUID id) {
        return store.yaml().getString(path(id, "tag"));
    }

    synchronized void setTag(UUID id, String tag) {
        store.yaml().set(path(id, "tag"), tag);
        store.markDirty();
    }

    synchronized String color(UUID id) {
        return store.yaml().getString(path(id, "color"));
    }

    synchronized void setColor(UUID id, String color) {
        store.yaml().set(path(id, "color"), color);
        store.markDirty();
    }

    /** Calendar month of the last bonus, "2026-11", or null. */
    synchronized String bonusMonth(UUID id) {
        return store.yaml().getString(path(id, "bonus-month"));
    }

    /** Claim blocks already given in {@link #bonusMonth}. */
    synchronized long bonusGiven(UUID id) {
        return store.yaml().getLong(path(id, "bonus-given"), 0);
    }

    synchronized void setBonus(UUID id, String month, long given) {
        store.yaml().set(path(id, "bonus-month"), month);
        store.yaml().set(path(id, "bonus-given"), given);
        store.markDirty();
    }

    synchronized void save() {
        store.saveNow();
    }
}
