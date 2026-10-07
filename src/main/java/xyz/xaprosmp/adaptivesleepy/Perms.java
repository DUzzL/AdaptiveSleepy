package xyz.xaprosmp.adaptivesleepy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permission.HasCommandLevel;
import net.minecraft.server.permissions.PermissionLevel;

/** Vanilla permission levels 0..4 (0 = everyone, 2 = gamemasters, 4 = owners) for the config's permission options. */
public final class Perms {
    private static final Permission[] HAS_LEVEL = buildLevels();

    private static Permission[] buildLevels() {
        Permission[] levels = new Permission[5];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = new HasCommandLevel(PermissionLevel.byId(i));
        }
        return levels;
    }

    private Perms() {
    }

    public static boolean has(CommandSourceStack source, int level) {
        return level <= 0 || source.permissions().hasPermission(HAS_LEVEL[Math.min(level, 4)]);
    }

    public static boolean any(CommandSourceStack source, int... levels) {
        for (int level : levels) {
            if (has(source, level)) {
                return true;
            }
        }

        return false;
    }
}
