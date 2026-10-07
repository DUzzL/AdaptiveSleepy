package xyz.xaprosmp.adaptivesleepy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Wires the config, the two commands and the server events: state is initialized on
 * SERVER_STARTED, the gamerule is re-applied after a datapack reload, reset to the base on
 * SERVER_STOPPING, and votes/weather are checked at the end of every overworld tick.
 */
public final class AdaptiveSleepy implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("AdaptiveSleepy");
    private static final SleepConfig CONFIG = new SleepConfig();

    public static SleepConfig config() {
        return CONFIG;
    }

    @Override
    public void onInitialize() {
        ConfigFile.initialize(FabricLoader.getInstance().getConfigDir(), CONFIG);
        LOGGER.info(
            "AdaptiveSleepy loaded: baseSleepingPercentage={}, thunderstormSleepingPercentage={}, vetoSleepingPercentage={} (-1 = disabled)",
            CONFIG.baseSleepingPercentage(), CONFIG.thunderstormSleepingPercentage(), CONFIG.vetoSleepingPercentage()
        );
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VetoCommand.register(dispatcher);
            AdaptiveSleepyCommand.register(dispatcher);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            VetoState.initialize(server.overworld());
            StormWatcher.initialize(server.overworld());
            RequirementSync.initialize(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(RequirementSync::applyBaseForShutdown);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                RequirementSync.apply(server);
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level == level.getServer().overworld()) {
                VetoState.onOverworldTickEnd(level);
                StormWatcher.onOverworldTickEnd(level);
            }
        });
    }
}
