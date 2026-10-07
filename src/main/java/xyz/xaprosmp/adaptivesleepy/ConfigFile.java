package xyz.xaprosmp.adaptivesleepy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code config/adaptivesleepy.toml}. The format is a flat TOML subset:
 * {@code [section]} headers, {@code key = value} lines and {@code #} comments. The file is
 * always regenerated from {@link ConfigOptions#ALL}, so a file with missing, invalid or unknown
 * keys is rewritten with defaults for the bad entries and the schema's comments.
 */
public final class ConfigFile {
    public static final String FILE_NAME = "adaptivesleepy.toml";

    private static Path path;

    private ConfigFile() {
    }

    /** Loads the file into {@code config}, creating or repairing it if needed. */
    public static void initialize(Path configDir, SleepConfig config) {
        path = configDir.resolve(FILE_NAME);
        if (Files.notExists(path) || !load(config)) {
            save(config);
        }
    }

    /** Returns false when the file needs rewriting (missing, invalid or unknown keys). */
    private static boolean load(SleepConfig config) {
        Map<String, String> values = new HashMap<>();
        boolean clean = true;
        try {
            String section = "";
            int lineNumber = 0;
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    AdaptiveSleepy.LOGGER.warn("{} line {}: cannot parse '{}', ignoring", path.getFileName(), lineNumber, rawLine.trim());
                    clean = false;
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                values.put(section.isEmpty() ? key : section + "." + key, value);
            }
        } catch (IOException e) {
            AdaptiveSleepy.LOGGER.error("Could not read {} — keeping current values", path, e);
            return true;
        }

        for (ConfigOptions.Option option : ConfigOptions.ALL) {
            String raw = values.remove(option.id());
            if (raw == null) {
                AdaptiveSleepy.LOGGER.warn("Config option '{}' is missing, using default {}", option.id(), option.get(config));
                clean = false;
                continue;
            }
            try {
                option.set(config, raw);
            } catch (IllegalArgumentException e) {
                AdaptiveSleepy.LOGGER.warn(
                    "Config option '{}' has invalid value '{}' ({}), using default {}", option.id(), raw, e.getMessage(), option.get(config)
                );
                clean = false;
            }
        }

        for (String unknown : values.keySet()) {
            AdaptiveSleepy.LOGGER.warn("Config option '{}' is unknown and will be removed", unknown);
            clean = false;
        }
        return clean;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    /** Rewrites the whole file from the schema and the current values. */
    public static synchronized void save(SleepConfig config) {
        StringBuilder out = new StringBuilder();
        out.append("# AdaptiveSleepy configuration.\n");
        out.append("#\n");
        out.append("# Every option can also be changed at runtime with\n");
        out.append("# /adaptivesleepy config <category> <setting> <value> (categories: sleep,\n");
        out.append("# permissions, messages); runtime changes are applied immediately and saved\n");
        out.append("# back to this file (comments are preserved because the file is regenerated\n");
        out.append("# from the option schema).\n");
        out.append("#\n");
        out.append("# Percentage semantics: -1 disables the feature, 0..100 behaves like the vanilla\n");
        out.append("# players_sleeping_percentage gamerule, values above 100 make skipping the night\n");
        out.append("# impossible while the condition is active. When several conditions apply at\n");
        out.append("# once, the highest value wins. AdaptiveSleepy writes the effective requirement\n");
        out.append("# into the players_sleeping_percentage gamerule (so vanilla and datapacks always\n");
        out.append("# see it) and restores the base value when the conditions end.\n");

        for (String section : ConfigOptions.sections()) {
            out.append('\n').append('[').append(section).append(']').append('\n');
            List<ConfigOptions.Option> options = ConfigOptions.inSection(section);
            for (ConfigOptions.Option option : options) {
                out.append('\n');
                for (String comment : option.comments()) {
                    out.append("# ").append(comment).append('\n');
                }
                out.append(option.key()).append(" = ").append(option.get(config)).append('\n');
            }
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AdaptiveSleepy.LOGGER.error("Could not save config to {}", path, e);
        }
    }
}
