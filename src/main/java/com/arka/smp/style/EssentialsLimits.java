package com.arka.smp.style;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * The Staff rank may ban for 3 days at most, and that limit lives in Essentials (max-tempban-time, in seconds), not in
 * LuckPerms. This reads Essentials' config.yml and says so when the limit is missing or too long. Pure: no server needed.
 */
final class EssentialsLimits {
    private EssentialsLimits() {
    }

    /** 3 days in seconds. */
    static final long STAFF_BAN_SECONDS = 3L * 24 * 3600;

    /** A message when the Essentials limit does not hold Staff to {@link #STAFF_BAN_SECONDS}, or null when it is fine or unknown. */
    static String problem(File essentialsConfig) {
        if (essentialsConfig == null || !essentialsConfig.isFile()) return null;       // Essentials not installed: nothing to check
        long limit;
        try {
            limit = YamlConfiguration.loadConfiguration(essentialsConfig).getLong("max-tempban-time", -1);
        } catch (RuntimeException e) {
            return null;
        }
        return problem(limit);
    }

    static String problem(long limitSeconds) {
        if (limitSeconds <= 0) {
            return "Essentials has no tempban limit (max-tempban-time: -1), so the Staff rank can ban for any time. "
                    + "Set max-tempban-time: " + STAFF_BAN_SECONDS + " in plugins/Essentials/config.yml and restart.";
        }
        if (limitSeconds > STAFF_BAN_SECONDS) {
            return "Essentials max-tempban-time is " + limitSeconds + " seconds, longer than 3 days, so the Staff rank can ban for "
                    + "longer than 3 days. Set it to " + STAFF_BAN_SECONDS + " in plugins/Essentials/config.yml and restart.";
        }
        return null;
    }
}
