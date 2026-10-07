package xyz.xaprosmp.adaptivesleepy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import xyz.xaprosmp.adaptivesleepy.mixin.ServerLevelInvoker;

/**
 * Active {@code /veto sleep} votes (player UUID → name). Any vote raises the requirement to
 * {@code vetoSleepingPercentage}. Votes live only in memory: they are cleared on server start
 * and when the overworld clock enters the next day (day index = clock time / 24000), which
 * also happens when the night is skipped by sleeping.
 */
public final class VetoState {
    public static final long DAY_LENGTH_TICKS = 24000L;
    private static final Map<UUID, String> votes = new ConcurrentHashMap<>();
    private static volatile long lastDayIndex = Long.MIN_VALUE;

    private VetoState() {
    }

    public static void initialize(ServerLevel overworld) {
        votes.clear();
        lastDayIndex = dayIndex(overworld);
    }

    public static boolean isActive() {
        return !votes.isEmpty();
    }

    public static int voteCount() {
        return votes.size();
    }

    public static boolean addVote(UUID playerId, String playerName) {
        return votes.putIfAbsent(playerId, playerName) == null;
    }

    public static boolean removeVote(UUID playerId) {
        return votes.remove(playerId) != null;
    }

    public static List<String> voterNames() {
        return votes.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Called at the end of every overworld tick: expires the votes when a new day starts. */
    public static void onOverworldTickEnd(ServerLevel overworld) {
        long day = dayIndex(overworld);
        long previous = lastDayIndex;
        if (day != previous) {
            lastDayIndex = day;
            if (day > previous && !votes.isEmpty()) {
                votes.clear();
                RequirementSync.apply(overworld.getServer());
                if (AdaptiveSleepy.config().broadcastVetoExpiration()) {
                    broadcast(
                        overworld.getServer(),
                        Texts.join(Texts.text("The sleep veto has ended — the night is over."), Texts.backToSentence(SleepRequirement.currentRequirement(overworld)))
                    );
                }
            }
        }
    }

    public static void clearForDisable(MinecraftServer server) {
        if (!votes.isEmpty()) {
            votes.clear();
            RequirementSync.apply(server);
            if (AdaptiveSleepy.config().broadcastVetoCancellation()) {
                broadcast(
                    server,
                    Texts.join(
                        Texts.text("The sleep veto system was disabled — the active veto was lifted."),
                        Texts.backToSentence(SleepRequirement.currentRequirement(server.overworld()))
                    )
                );
            }
        }
    }

    static long dayIndex(ServerLevel overworld) {
        return Math.floorDiv(overworld.getOverworldClockTime(), DAY_LENGTH_TICKS);
    }

    static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** Re-sends vanilla's "N/M players sleeping" action bar after the requirement changed. */
    static void refreshSleepAnnouncement(ServerLevel overworld) {
        if (overworld.players().stream().anyMatch(LivingEntity::isSleeping)) {
            ((ServerLevelInvoker)overworld).adaptivesleepy$announceSleepStatus();
        }
    }
}
