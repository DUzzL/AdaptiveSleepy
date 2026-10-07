package xyz.xaprosmp.adaptivesleepy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * Schema of every config option: its id ({@code section.key}), description, comments written to
 * the TOML file, value suggestions for the command, validation and the {@link SleepConfig}
 * accessor pair. {@link ConfigFile} and {@link AdaptiveSleepyCommand} are both driven by
 * {@link #ALL}; adding an option here adds it to the file and to {@code /adaptivesleepy config}.
 */
public final class ConfigOptions {
    public static final String SECTION_SLEEP = "sleep";
    public static final String SECTION_PERMISSIONS = "permissions";
    public static final String SECTION_MESSAGES = "messages";
    private static final List<String> PERCENTAGE_SUGGESTIONS = List.of("-1", "0", "10", "25", "50", "75", "100", "101");
    private static final List<String> PERMISSION_SUGGESTIONS = List.of("0", "1", "2", "3", "4");
    private static final List<String> BOOLEAN_SUGGESTIONS = List.of("true", "false");
    private static final String[] PERMISSION_LEVEL_NAMES = new String[]{"everyone", "moderators", "gamemasters", "admins", "owners"};
    private static final List<String> PERCENTAGE_SEMANTICS = List.of(
        "0..100 behaves like the vanilla players_sleeping_percentage gamerule.",
        "Values above 100 make skipping the night impossible while this condition is active."
    );
    public static final List<Option> ALL = List.of(
        percentage(
            "sleep.baseSleepingPercentage",
            "Base requirement for normal nights",
            List.of(
                "The base sleeping percentage for normal nights. AdaptiveSleepy manages the",
                "players_sleeping_percentage gamerule directly: the base is written to the",
                "gamerule, thunderstorm/veto conditions temporarily raise it (highest wins),",
                "and it returns to the base when they end.",
                "-1 = vanilla-managed base: whatever admins set with /gamerule is the base.",
                "Any other value = explicit base; changes made with /gamerule are adopted",
                "back into this setting."
            ),
            SleepConfig::baseSleepingPercentage,
            SleepConfig::setBaseSleepingPercentage,
            v -> v == SleepConfig.DISABLED ? "managed via /gamerule" : null
        ),
        percentage(
            "sleep.thunderstormSleepingPercentage",
            "Requirement while a thunderstorm is active",
            List.of("The sleeping percentage required while a thunderstorm is active in the level.", "-1 disables the thunderstorm condition entirely."),
            SleepConfig::thunderstormSleepingPercentage,
            SleepConfig::setThunderstormSleepingPercentage,
            v -> v == SleepConfig.DISABLED ? "disabled" : null
        ),
        percentage(
            "sleep.vetoSleepingPercentage",
            "Requirement while a sleep veto is active",
            List.of(
                "The sleeping percentage required while at least one /veto sleep vote is active.",
                "-1 disables the veto system and removes the /veto sleep commands",
                "(applies immediately; active votes are cleared)."
            ),
            SleepConfig::vetoSleepingPercentage,
            SleepConfig::setVetoSleepingPercentage,
            v -> v == SleepConfig.DISABLED ? "disabled, /veto removed" : null
        ),
        permission(
            "permissions.veto",
            "Permission for /veto sleep [cancel]",
            SleepConfig::permissionVeto,
            SleepConfig::setPermissionVeto,
            "Permission level required to start or withdraw a sleep veto",
            "(/veto sleep and /veto sleep cancel)."
        ),
        permission(
            "permissions.vetoList",
            "Permission for /veto sleep list",
            SleepConfig::permissionVetoList,
            SleepConfig::setPermissionVetoList,
            "Permission level required for /veto sleep list."
        ),
        permission(
            "permissions.config",
            "Permission for /adaptivesleepy config",
            SleepConfig::permissionConfig,
            SleepConfig::setPermissionConfig,
            "Permission level required to view and change this configuration",
            "(/adaptivesleepy config and its category subcommands)."
        ),
        toggle(
            "messages.broadcastVetoActivation",
            "Broadcast when a veto becomes active",
            SleepConfig::broadcastVetoActivation,
            SleepConfig::setBroadcastVetoActivation,
            "Broadcast to all players when a sleep veto becomes active.",
            "Disabled by default to keep global chat vanilla-quiet."
        ),
        toggle(
            "messages.broadcastVetoCancellation",
            "Broadcast when the veto is lifted",
            SleepConfig::broadcastVetoCancellation,
            SleepConfig::setBroadcastVetoCancellation,
            "Broadcast to all players when the last veto vote is withdrawn and the veto lifts.",
            "Disabled by default to keep global chat vanilla-quiet."
        ),
        toggle(
            "messages.broadcastVetoExpiration",
            "Broadcast when a veto night ends",
            SleepConfig::broadcastVetoExpiration,
            SleepConfig::setBroadcastVetoExpiration,
            "Broadcast to all players when an active veto expires because the night ended.",
            "Disabled by default to keep global chat vanilla-quiet."
        ),
        toggle(
            "messages.broadcastThunderstorm",
            "Broadcast thunderstorm requirement changes",
            SleepConfig::broadcastThunderstorm,
            SleepConfig::setBroadcastThunderstorm,
            "Broadcast when a thunderstorm starts or stops changing the effective sleeping",
            "requirement. Disabled by default to keep global chat vanilla-quiet."
        ),
        toggle(
            "messages.commandFeedback",
            "Private confirmations for /veto sleep [cancel]",
            SleepConfig::commandFeedback,
            SleepConfig::setCommandFeedback,
            "Send private confirmation messages for /veto sleep and /veto sleep cancel.",
            "Errors and query results (like /veto sleep list) are always shown."
        ),
        toggle(
            "messages.showEffectivePercentage",
            "Include percentages in messages",
            SleepConfig::showEffectivePercentage,
            SleepConfig::setShowEffectivePercentage,
            "Include the current effective sleeping percentage in broadcasts, confirmations",
            "and gamerule query hints."
        )
    );
    private static final Map<String, Option> BY_ID = buildIndex();
    private static final Map<String, List<Option>> BY_SECTION = buildSections();

    private static IntOption percentage(
        String id,
        String description,
        List<String> extraComments,
        ToIntFunction<SleepConfig> getter,
        ObjIntConsumer<SleepConfig> setter,
        IntFunction<String> annotator
    ) {
        List<String> comments = new ArrayList<>(extraComments);
        comments.addAll(PERCENTAGE_SEMANTICS);
        return new IntOption(
            id, SECTION_SLEEP, description, List.copyOf(comments), PERCENTAGE_SUGGESTIONS, SleepConfig.DISABLED, 10000, getter, setter, annotator
        );
    }

    private static IntOption permission(
        String id, String description, ToIntFunction<SleepConfig> getter, ObjIntConsumer<SleepConfig> setter, String... comments
    ) {
        List<String> allComments = new ArrayList<>(List.of(comments));
        allComments.add("Allowed values: 0 = everyone, 1 = moderators, 2 = gamemasters, 3 = admins, 4 = owners.");
        return new IntOption(
            id,
            SECTION_PERMISSIONS,
            description,
            List.copyOf(allComments),
            PERMISSION_SUGGESTIONS,
            0,
            4,
            getter,
            setter,
            level -> PERMISSION_LEVEL_NAMES[Math.max(0, Math.min(level, 4))]
        );
    }

    private static BoolOption toggle(
        String id, String description, Predicate<SleepConfig> getter, BiConsumer<SleepConfig, Boolean> setter, String... comments
    ) {
        return new BoolOption(id, SECTION_MESSAGES, description, List.of(comments), getter, setter);
    }

    private static Map<String, Option> buildIndex() {
        Map<String, Option> map = new LinkedHashMap<>();
        for (Option option : ALL) {
            map.put(option.id(), option);
        }
        return map;
    }

    private static Map<String, List<Option>> buildSections() {
        Map<String, List<Option>> map = new LinkedHashMap<>();
        for (Option option : ALL) {
            map.computeIfAbsent(option.section(), s -> new ArrayList<>()).add(option);
        }
        return map;
    }

    public static @Nullable Option bySectionAndKey(String section, String key) {
        return BY_ID.get(section + "." + key);
    }

    /** Section names in file order: sleep, permissions, messages. */
    public static List<String> sections() {
        return List.copyOf(BY_SECTION.keySet());
    }

    public static List<Option> inSection(String section) {
        return BY_SECTION.getOrDefault(section, List.of());
    }

    public static List<String> keysIn(String section) {
        return inSection(section).stream().map(Option::key).toList();
    }

    private ConfigOptions() {
    }

    public record BoolOption(
        String id, String section, String description, List<String> comments, Predicate<SleepConfig> getter, BiConsumer<SleepConfig, Boolean> setter
    ) implements Option {
        @Override
        public List<String> suggestions() {
            return BOOLEAN_SUGGESTIONS;
        }

        @Override
        public String get(SleepConfig config) {
            return String.valueOf(this.getter.test(config));
        }

        @Override
        public @Nullable String annotate(SleepConfig config) {
            return null;
        }

        @Override
        public void set(SleepConfig config, String raw) {
            switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "true" -> this.setter.accept(config, true);
                case "false" -> this.setter.accept(config, false);
                default -> throw new IllegalArgumentException("expected true or false");
            }
        }
    }

    public record IntOption(
        String id,
        String section,
        String description,
        List<String> comments,
        List<String> suggestions,
        int min,
        int max,
        ToIntFunction<SleepConfig> getter,
        ObjIntConsumer<SleepConfig> setter,
        @Nullable IntFunction<String> annotator
    ) implements Option {
        @Override
        public String get(SleepConfig config) {
            return String.valueOf(this.getter.applyAsInt(config));
        }

        @Override
        public @Nullable String annotate(SleepConfig config) {
            return this.annotator == null ? null : this.annotator.apply(this.getter.applyAsInt(config));
        }

        @Override
        public void set(SleepConfig config, String raw) {
            int value;
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("expected a whole number between " + this.min + " and " + this.max);
            }

            if (value >= this.min && value <= this.max) {
                this.setter.accept(config, value);
            } else {
                throw new IllegalArgumentException("must be between " + this.min + " and " + this.max);
            }
        }
    }

    public sealed interface Option permits IntOption, BoolOption {
        /** {@code section.key}, e.g. {@code sleep.vetoSleepingPercentage}. */
        String id();

        String section();

        default String key() {
            return this.id().substring(this.id().indexOf('.') + 1);
        }

        /** One-line summary shown by {@code /adaptivesleepy config}. */
        String description();

        /** Lines written above the option in the TOML file and shown on hover in chat. */
        List<String> comments();

        List<String> suggestions();

        String get(SleepConfig config);

        /** Short note shown next to the value (e.g. "disabled"), or null. */
        @Nullable String annotate(SleepConfig config);

        /** Parses and stores {@code raw}; throws IllegalArgumentException with a user-facing reason. */
        void set(SleepConfig config, String raw);
    }
}
