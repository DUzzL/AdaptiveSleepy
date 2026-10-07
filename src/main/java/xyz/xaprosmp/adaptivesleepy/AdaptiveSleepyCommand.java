package xyz.xaprosmp.adaptivesleepy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /adaptivesleepy config [section] [setting value]}: shows and changes every option from
 * {@link ConfigOptions}. A change is saved to the TOML file at once and its side effects
 * (re-sending the command tree, clearing votes, re-applying the gamerule) run immediately.
 */
public final class AdaptiveSleepyCommand {
    private AdaptiveSleepyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config").executes(context -> show(context.getSource(), null));

        for (String section : ConfigOptions.sections()) {
            config.then(
                Commands.literal(section)
                    .executes(context -> show(context.getSource(), section))
                    .then(
                        Commands.argument("setting", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigOptions.keysIn(section), builder))
                            .then(
                                Commands.argument("value", StringArgumentType.word())
                                    .suggests((context, builder) -> suggestValues(section, context, builder))
                                    .executes(context -> set(context, section))
                            )
                    )
            );
        }

        dispatcher.register(
            Commands.literal("adaptivesleepy")
                .requires(source -> Perms.has(source, AdaptiveSleepy.config().permissionConfig()))
                .then(config)
        );
    }

    private static CompletableFuture<Suggestions> suggestValues(String section, CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ConfigOptions.Option option = ConfigOptions.bySectionAndKey(section, StringArgumentType.getString(context, "setting"));
        return option == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(option.suggestions(), builder);
    }

    private static int show(CommandSourceStack source, @Nullable String section) {
        SleepConfig config = AdaptiveSleepy.config();
        MutableComponent message = Texts.text("AdaptiveSleepy configuration" + (section == null ? "" : " — " + section));

        for (String current : section == null ? ConfigOptions.sections() : List.of(section)) {
            if (section == null) {
                message.append(Texts.muted("\n[" + current + "]"));
            }

            for (ConfigOptions.Option option : ConfigOptions.inSection(current)) {
                message.append(Texts.text("\n  ")).append(settingLink(current, option)).append(Texts.muted(" = ")).append(Texts.text(option.get(config)));
                String note = option.annotate(config);
                if (note != null) {
                    message.append(Texts.muted(" (" + note + ")"));
                }

                message.append(Texts.muted(" — " + option.description()));
            }
        }

        ServerLevel overworld = source.getServer().overworld();
        int required = SleepRequirement.currentRequirement(overworld);
        message.append(Texts.text("\n")).append(Texts.muted("Effective now: ")).append(Texts.percent(required));
        if (required > 100) {
            message.append(Texts.muted(" — night skipping is currently impossible"));
        }

        message.append(Texts.muted(" (" + SleepRequirement.describeConditions(overworld) + ")"));
        Texts.reply(source, message);
        return 1;
    }

    private static MutableComponent settingLink(String section, ConfigOptions.Option option) {
        MutableComponent hover = Texts.muted(String.join("\n", option.comments())).append(Texts.muted("\n\nClick to prepare the change command in chat."));
        return Texts.setting(option.key())
            .withStyle(
                style -> style.withClickEvent(new SuggestCommand("/adaptivesleepy config " + section + " " + option.key() + " "))
                    .withHoverEvent(new ShowText(hover))
            );
    }

    private static int set(CommandContext<CommandSourceStack> context, String section) {
        CommandSourceStack source = context.getSource();
        String key = StringArgumentType.getString(context, "setting");
        String raw = StringArgumentType.getString(context, "value");
        ConfigOptions.Option option = ConfigOptions.bySectionAndKey(section, key);
        if (option == null) {
            Texts.error(source, Texts.text("Unknown " + section + " setting: " + key + " — press Tab to see the available settings."));
            return 0;
        }

        try {
            option.set(AdaptiveSleepy.config(), raw);
        } catch (IllegalArgumentException e) {
            Texts.error(source, Texts.text("Invalid value for ").append(Texts.setting(option.key())).append(Texts.text(": " + raw + " — " + e.getMessage() + ".")));
            return 0;
        }

        ConfigFile.save(AdaptiveSleepy.config());
        applySideEffects(source.getServer(), option);
        MutableComponent confirmation = Texts.text("Set ")
            .append(Texts.setting(option.key()))
            .append(Texts.text(" to " + option.get(AdaptiveSleepy.config())))
            .append(Texts.muted(" — saved."));
        if (option.id().equals("sleep.baseSleepingPercentage")) {
            if (!AdaptiveSleepy.config().baseOverrideEnabled()) {
                confirmation.append(Texts.muted(" The base now follows /gamerule players_sleeping_percentage."));
            } else {
                int applied = RequirementSync.readGamerule(source.getServer());
                if (applied != RequirementSync.base()) {
                    confirmation.append(Texts.muted(" Gamerule currently "))
                        .append(Texts.percent(applied))
                        .append(Texts.muted(" (" + SleepRequirement.describeConditions(source.getServer().overworld()) + ")."));
                }
            }
        }

        source.sendSuccess(() -> confirmation, true);
        return 1;
    }

    private static void applySideEffects(MinecraftServer server, ConfigOptions.Option option) {
        String id = option.id();
        if (id.startsWith("permissions.") || id.equals("sleep.vetoSleepingPercentage")) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        }

        if (id.equals("sleep.vetoSleepingPercentage") && !AdaptiveSleepy.config().vetoEnabled()) {
            VetoState.clearForDisable(server);
        }

        if (id.equals("sleep.baseSleepingPercentage")) {
            int value = AdaptiveSleepy.config().baseSleepingPercentage();
            if (value >= 0) {
                RequirementSync.setBase(server, value);
            }
        } else if (id.startsWith("sleep.")) {
            RequirementSync.apply(server);
        }
    }
}
