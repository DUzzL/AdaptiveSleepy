package xyz.xaprosmp.adaptivesleepy;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/**
 * Re-applies the requirement when the overworld starts or stops thundering. Uses
 * {@code Level.isThundering()} (thunder level &gt; 0.9), so the switch lags the weather command by
 * a few seconds while the thunder level ramps up or down.
 */
public final class StormWatcher {
    private static volatile boolean lastThundering;

    private StormWatcher() {
    }

    public static void initialize(ServerLevel overworld) {
        lastThundering = overworld.isThundering();
    }

    public static void onOverworldTickEnd(ServerLevel overworld) {
        boolean thundering = overworld.isThundering();
        if (thundering != lastThundering) {
            lastThundering = thundering;
            SleepConfig config = AdaptiveSleepy.config();
            int base = RequirementSync.base();
            int withStorm = SleepRequirement.effective(base, true, VetoState.isActive(), config);
            int withoutStorm = SleepRequirement.effective(base, false, VetoState.isActive(), config);
            RequirementSync.apply(overworld.getServer());
            if (withStorm != withoutStorm && config.broadcastThunderstorm()) {
                MutableComponent message;
                if (thundering) {
                    message = Texts.join(Texts.text("A thunderstorm is raging."), Texts.requirementSentence(withStorm));
                } else {
                    message = Texts.join(Texts.text("The thunderstorm has passed."), Texts.backToSentence(withoutStorm));
                }

                VetoState.broadcast(overworld.getServer(), message);
            }
        }
    }
}
