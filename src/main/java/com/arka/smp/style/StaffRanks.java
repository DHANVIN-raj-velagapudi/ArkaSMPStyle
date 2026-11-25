package com.arka.smp.style;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rules for /style staff: which LuckPerms groups an Admin may give or take. The admin group (and default) can never
 * be handled by the command, whatever the config says, so an Admin cannot make another Admin. That stays with the
 * server console ({@code lp user <name> parent add admin}).
 */
final class StaffRanks {
    private StaffRanks() {
    }

    /** Never handled by /style staff. */
    static final Set<String> NEVER = Set.of("admin", "default");

    /** True when the group may be given or taken with /style staff: it is in the config list and is not a NEVER group. */
    static boolean mayChange(String group, List<String> allowed) {
        if (group == null) return false;
        String g = group.trim().toLowerCase(Locale.ROOT);
        if (g.isEmpty() || NEVER.contains(g)) return false;
        for (String a : allowed) {
            if (a != null && a.trim().toLowerCase(Locale.ROOT).equals(g)) return true;
        }
        return false;
    }

    /** The configured list, lower case, with the NEVER groups removed (what is shown in tab completion). */
    static List<String> clean(List<String> allowed) {
        return allowed.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !NEVER.contains(s)).distinct().toList();
    }
}
