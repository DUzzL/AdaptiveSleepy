package xyz.xaprosmp.adaptivesleepy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /veto sleep}, {@code /veto sleep cancel}, {@code /veto sleep list}. The whole
 * {@code /veto} tree is hidden while {@code vetoSleepingPercentage = -1}.
 */
public final class VetoCommand {
    private VetoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("veto")
                .requires(source -> AdaptiveSleepy.config().vetoEnabled())
                .then(
                    Commands.literal("sleep")
                        .requires(source -> Perms.any(source, AdaptiveSleepy.config().permissionVeto(), AdaptiveSleepy.config().permissionVetoList()))
                        .executes(context -> vote(context.getSource()))
                        .then(
                            Commands.literal("cancel")
                                .requires(source -> Perms.has(source, AdaptiveSleepy.config().permissionVeto()))
                                .executes(context -> cancel(context.getSource()))
                        )
                        .then(
                            Commands.literal("list")
                                .requires(source -> Perms.has(source, AdaptiveSleepy.config().permissionVetoList()))
                                .executes(context -> list(context.getSource()))
                        )
                )
        );
    }

    private static int vote(CommandSourceStack source) throws CommandSyntaxException {
        if (!Perms.has(source, AdaptiveSleepy.config().permissionVeto())) {
            Texts.error(source, Texts.text("You don't have permission to use this command."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();
        if (!VetoState.addVote(player.getUUID(), player.getName().getString())) {
            Texts.error(
                source, Texts.text("You already have an active sleep veto — use ").append(Texts.command("/veto sleep cancel")).append(Texts.text(" to remove it."))
            );
            return 0;
        }

        RequirementSync.apply(source.getServer());
        boolean appliesNow = overworld.isDarkOutside();
        int required = SleepRequirement.currentRequirement(overworld);
        Texts.feedback(
            source,
            Texts.join(
                Texts.text("Sleep veto registered — the sleeping requirement is raised for the " + (appliesNow ? "current" : "next") + " night."),
                Texts.requirementSentence(required)
            )
        );
        if (VetoState.voteCount() == 1 && AdaptiveSleepy.config().broadcastVetoActivation()) {
            VetoState.broadcast(
                source.getServer(), Texts.join(Texts.text(player.getName().getString() + " has vetoed skipping the night."), Texts.requirementSentence(required))
            );
        }

        return 1;
    }

    private static int cancel(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();
        if (!VetoState.removeVote(player.getUUID())) {
            Texts.error(source, Texts.text("You have no active sleep veto."));
            return 0;
        }

        Texts.feedback(source, Texts.text("Your sleep veto was removed."));
        if (!VetoState.isActive()) {
            RequirementSync.apply(source.getServer());
            if (AdaptiveSleepy.config().broadcastVetoCancellation()) {
                VetoState.broadcast(
                    source.getServer(),
                    Texts.join(Texts.text("The sleep veto has been lifted."), Texts.backToSentence(SleepRequirement.currentRequirement(overworld)))
                );
            }
        }

        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<String> names = VetoState.voterNames();
        if (names.isEmpty()) {
            Texts.reply(source, Texts.muted("No active sleep vetoes. Start one with ").append(Texts.command("/veto sleep")).append(Texts.muted(".")));
            return 0;
        }

        ServerLevel overworld = source.getServer().overworld();
        boolean appliesNow = overworld.isDarkOutside();
        MutableComponent message = Texts.text("Active sleep vetoes (" + names.size() + ") — keeping the " + (appliesNow ? "current" : "next") + " night:");

        for (String name : names) {
            message.append(Texts.muted("\n - ")).append(Texts.text(name));
        }

        MutableComponent requirement = Texts.requirementSentence(SleepRequirement.currentRequirement(overworld));
        if (requirement != null) {
            message.append(Texts.text("\n")).append(requirement);
        }

        Texts.reply(source, message);
        return names.size();
    }
}
