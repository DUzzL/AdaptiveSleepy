package xyz.xaprosmp.adaptivesleepy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Owns the {@code players_sleeping_percentage} gamerule. Invariant after every {@link #apply}:
 * gamerule = {@link SleepRequirement#effective}(base, thundering, veto active). Vanilla's
 * night-skip check and datapacks read the gamerule, so nothing else in vanilla is patched.
 *
 * <p>{@code base} is the admin's value: the config's {@code baseSleepingPercentage}, or (when that
 * is -1) the gamerule value at server start and every later {@code /gamerule} set by a player or
 * console. On shutdown the gamerule is reset to the base so a raised value is not saved as the
 * base of the next start.
 */
public final class RequirementSync {
    private static volatile int base = 100;
    /** True while this class writes the gamerule; the gamerule-command mixin ignores those writes. */
    private static volatile boolean applying;

    private RequirementSync() {
    }

    public static int base() {
        return base;
    }

    public static boolean isApplying() {
        return applying;
    }

    public static int readGamerule(MinecraftServer server) {
        return server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
    }

    public static void initialize(MinecraftServer server) {
        SleepConfig config = AdaptiveSleepy.config();
        base = config.baseOverrideEnabled() ? config.baseSleepingPercentage() : readGamerule(server);
        apply(server);
        AdaptiveSleepy.LOGGER.info("Managing players_sleeping_percentage: base {}%, currently {}%", base, readGamerule(server));
    }

    /** Recomputes the effective requirement and writes it if it differs; call after any input changes. */
    public static void apply(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        int effective = SleepRequirement.effective(base, overworld.isThundering(), VetoState.isActive(), AdaptiveSleepy.config());
        if (readGamerule(server) != effective) {
            write(server, effective);
            VetoState.refreshSleepAnnouncement(overworld);
        }
    }

    public static void setBase(MinecraftServer server, int value) {
        base = value;
        apply(server);
    }

    /** A player or console ran {@code /gamerule players_sleeping_percentage <value>}: that value becomes the base. */
    public static void adoptExternalBase(MinecraftServer server, int value) {
        base = value;
        SleepConfig config = AdaptiveSleepy.config();
        if (config.baseOverrideEnabled() && config.baseSleepingPercentage() != value) {
            config.setBaseSleepingPercentage(value);
            ConfigFile.save(config);
        }

        apply(server);
    }

    public static void applyBaseForShutdown(MinecraftServer server) {
        if (readGamerule(server) != base) {
            write(server, base);
        }
    }

    private static void write(MinecraftServer server, int value) {
        applying = true;

        try {
            server.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, value, server);
        } finally {
            applying = false;
        }
    }
}
