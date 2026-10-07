package xyz.xaprosmp.adaptivesleepy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.xaprosmp.adaptivesleepy.AdaptiveSleepy;
import xyz.xaprosmp.adaptivesleepy.RequirementSync;
import xyz.xaprosmp.adaptivesleepy.SleepRequirement;
import xyz.xaprosmp.adaptivesleepy.Texts;

/**
 * Hooks {@code /gamerule players_sleeping_percentage}: a query explains a raised value, a set by
 * a player or console becomes the new base ({@link RequirementSync#adoptExternalBase}), a set by
 * a function or datapack (silent source) is reverted to the managed value.
 *
 * <p>Mixin targets (check these first when porting to a new Minecraft version):
 * {@code GameRuleCommand.queryRule(CommandSourceStack, GameRule)},
 * {@code GameRuleCommand.setRule(CommandContext, GameRule)} and the single
 * {@code Object.equals} call inside setRule (added in 26.3).
 */
@Mixin(GameRuleCommand.class)
public abstract class GameRuleCommandMixin {
    @Inject(method = "queryRule", at = @At("RETURN"))
    private static void adaptivesleepy$explainQuery(CommandSourceStack source, GameRule<?> gameRule, CallbackInfoReturnable<Integer> cir) {
        if (gameRule == GameRules.PLAYERS_SLEEPING_PERCENTAGE && !source.isSilent()) {
            if (AdaptiveSleepy.config().showEffectivePercentage()) {
                int base = RequirementSync.base();
                if (cir.getReturnValueI() != base) {
                    source.sendSuccess(
                        () -> Texts.muted("AdaptiveSleepy: base is ")
                            .append(Texts.percent(base))
                            .append(Texts.muted(" — currently raised (" + SleepRequirement.describeConditions(source.getServer().overworld()) + ")")),
                        false
                    );
                }
            }
        }
    }

    /**
     * Since 26.3, setRule throws commands.gamerule.not_set when the requested value equals the
     * current one. While the requirement is raised, the current value is the raised one, not the
     * base the admin manages: "/gamerule players_sleeping_percentage 50" during a 50% thunderstorm
     * with base 30 must still save 50 as the base. Treat the request as unchanged only when it
     * also equals the base. Silent sources (functions/datapacks) keep vanilla's check.
     */
    @WrapOperation(method = "setRule", at = @At(value = "INVOKE", target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"), allow = 1)
    private static boolean adaptivesleepy$compareWithBase(
        Object current, Object requested, Operation<Boolean> original, CommandContext<CommandSourceStack> context, GameRule<?> gameRule
    ) {
        boolean unchanged = original.call(current, requested);
        if (gameRule != GameRules.PLAYERS_SLEEPING_PERCENTAGE || context.getSource().isSilent()) {
            return unchanged;
        }

        return unchanged && requested.equals(RequirementSync.base());
    }

    @Inject(method = "setRule", at = @At("RETURN"))
    private static void adaptivesleepy$trackSet(CommandContext<CommandSourceStack> context, GameRule<?> gameRule, CallbackInfoReturnable<Integer> cir) {
        if (gameRule == GameRules.PLAYERS_SLEEPING_PERCENTAGE && !RequirementSync.isApplying()) {
            CommandSourceStack source = context.getSource();
            MinecraftServer server = source.getServer();
            int written = RequirementSync.readGamerule(server);
            if (source.isSilent()) {
                AdaptiveSleepy.LOGGER.info("players_sleeping_percentage was set to {} by a function/datapack — restoring the managed value", written);
                RequirementSync.apply(server);
            } else {
                RequirementSync.adoptExternalBase(server, written);
                int effective = RequirementSync.readGamerule(server);
                if (effective != written && AdaptiveSleepy.config().showEffectivePercentage()) {
                    source.sendSuccess(
                        () -> Texts.muted("AdaptiveSleepy: base saved as ")
                            .append(Texts.percent(written))
                            .append(Texts.muted(" — currently raised to "))
                            .append(Texts.percent(effective))
                            .append(Texts.muted(" (" + SleepRequirement.describeConditions(server.overworld()) + ")")),
                        false
                    );
                }
            }
        }
    }
}
