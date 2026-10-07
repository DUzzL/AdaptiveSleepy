package xyz.xaprosmp.adaptivesleepy;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;

/** Chat text helpers: colors, clickable commands and the shared requirement sentences. */
public final class Texts {
    public static final ChatFormatting VALUE = ChatFormatting.AQUA;
    public static final ChatFormatting MUTED = ChatFormatting.GRAY;

    private Texts() {
    }

    public static MutableComponent t(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    public static MutableComponent text(String body) {
        return Component.literal(body);
    }

    public static MutableComponent muted(String body) {
        return t(body, MUTED);
    }

    public static MutableComponent percent(int percentage) {
        return t(percentage + "%", VALUE);
    }

    public static MutableComponent command(String command) {
        return t(command, VALUE)
            .withStyle(
                style -> style.withClickEvent(new SuggestCommand(command)).withHoverEvent(new ShowText(muted("Click to put " + command + " in your chat bar")))
            );
    }

    public static MutableComponent setting(String name) {
        return t(name, VALUE);
    }

    public static MutableComponent requirementSentence(int required) {
        if (required > 100) {
            return text("The night cannot be skipped by sleeping while this applies.");
        } else {
            return !AdaptiveSleepy.config().showEffectivePercentage() ? null : percent(required).append(text(" of players must sleep to skip the night."));
        }
    }

    public static MutableComponent backToSentence(int required) {
        if (required > 100) {
            return text("Skipping the night remains impossible.");
        } else {
            return !AdaptiveSleepy.config().showEffectivePercentage()
                ? null
                : muted("The sleeping requirement is back to ").append(percent(required)).append(muted("."));
        }
    }

    public static MutableComponent join(MutableComponent base, MutableComponent addition) {
        return addition == null ? base : base.append(Component.literal(" ")).append(addition);
    }

    public static void feedback(CommandSourceStack source, MutableComponent message) {
        if (AdaptiveSleepy.config().commandFeedback()) {
            source.sendSuccess(() -> message, false);
        }
    }

    public static void reply(CommandSourceStack source, MutableComponent message) {
        source.sendSuccess(() -> message, false);
    }

    public static void error(CommandSourceStack source, MutableComponent message) {
        source.sendFailure(message);
    }
}
