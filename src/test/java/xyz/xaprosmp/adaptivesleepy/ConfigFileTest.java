package xyz.xaprosmp.adaptivesleepy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileTest {
    @TempDir
    Path dir;

    private Path file() {
        return this.dir.resolve(ConfigFile.FILE_NAME);
    }

    @Test
    void missingFileIsCreatedWithDefaultsAndReadsBackUnchanged() throws IOException {
        ConfigFile.initialize(this.dir, new SleepConfig());
        String written = Files.readString(file());
        assertTrue(written.contains("[sleep]") && written.contains("[permissions]") && written.contains("[messages]"));
        assertTrue(written.contains("baseSleepingPercentage = -1"));

        ConfigFile.initialize(this.dir, new SleepConfig());
        assertEquals(written, Files.readString(file()), "a clean file must not be rewritten");
    }

    @Test
    void savedValuesSurviveARestart() {
        SleepConfig changed = new SleepConfig();
        changed.setBaseSleepingPercentage(20);
        changed.setVetoSleepingPercentage(101);
        changed.setPermissionConfig(4);
        changed.setBroadcastThunderstorm(true);
        ConfigFile.initialize(this.dir, changed); // no file yet: writes the current values

        SleepConfig loaded = new SleepConfig();
        ConfigFile.initialize(this.dir, loaded);
        assertEquals(20, loaded.baseSleepingPercentage());
        assertEquals(101, loaded.vetoSleepingPercentage());
        assertEquals(4, loaded.permissionConfig());
        assertTrue(loaded.broadcastThunderstorm());
    }

    @Test
    void badEntriesFallBackToDefaultsAndTheFileIsRepaired() throws IOException {
        Files.writeString(file(), """
            [sleep]
            baseSleepingPercentage = 25  # trailing comment
            thunderstormSleepingPercentage = loud
            vetoSleepingPercentage = -5
            [permissions]
            veto = 9
            vetoList = 0
            config = 3
            obsolete = 1
            [messages]
            broadcastVetoActivation = TRUE
            """);

        SleepConfig loaded = new SleepConfig();
        ConfigFile.initialize(this.dir, loaded);

        assertEquals(25, loaded.baseSleepingPercentage());
        assertEquals(50, loaded.thunderstormSleepingPercentage(), "non-number keeps the default");
        assertEquals(50, loaded.vetoSleepingPercentage(), "below -1 keeps the default");
        assertEquals(0, loaded.permissionVeto(), "above 4 keeps the default");
        assertEquals(3, loaded.permissionConfig());
        assertTrue(loaded.broadcastVetoActivation(), "booleans are case-insensitive");
        assertTrue(loaded.commandFeedback(), "missing option keeps the default");

        String repaired = Files.readString(file());
        assertFalse(repaired.contains("obsolete"), "unknown keys are dropped");
        assertTrue(repaired.contains("thunderstormSleepingPercentage = 50"));
        assertTrue(repaired.contains("commandFeedback = true"));
        assertTrue(repaired.contains("baseSleepingPercentage = 25"));
    }
}
