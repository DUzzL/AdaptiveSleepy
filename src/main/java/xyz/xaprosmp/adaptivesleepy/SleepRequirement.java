package xyz.xaprosmp.adaptivesleepy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** The requirement formula: the highest of the base and every active, enabled condition. */
public final class SleepRequirement {
    private SleepRequirement() {
    }

    public static int currentRequirement(ServerLevel level) {
        return effective(RequirementSync.base(), level.isThundering(), VetoState.isActive(), AdaptiveSleepy.config());
    }

    /** Pure function behind the gamerule value: max(base, thunder% if thundering, veto% if a veto is active). */
    public static int effective(int base, boolean thundering, boolean vetoActive, SleepConfig config) {
        int percentage = base;
        if (thundering && config.thunderstormEnabled()) {
            percentage = Math.max(percentage, config.thunderstormSleepingPercentage());
        }

        if (vetoActive && config.vetoEnabled()) {
            percentage = Math.max(percentage, config.vetoSleepingPercentage());
        }

        return percentage;
    }

    /** Human-readable reason for the current value, e.g. "thunderstorm" or "base + sleep veto". */
    public static String describeConditions(ServerLevel level) {
        SleepConfig config = AdaptiveSleepy.config();
        int base = RequirementSync.base();
        int effective = effective(base, level.isThundering(), VetoState.isActive(), config);
        List<String> parts = new ArrayList<>(3);
        if (base == effective) {
            parts.add("base");
        }

        if (config.thunderstormEnabled() && level.isThundering() && config.thunderstormSleepingPercentage() == effective) {
            parts.add("thunderstorm");
        }

        if (config.vetoEnabled() && VetoState.isActive() && config.vetoSleepingPercentage() == effective) {
            parts.add("sleep veto");
        }

        return parts.isEmpty() ? "base" : String.join(" + ", parts);
    }
}
