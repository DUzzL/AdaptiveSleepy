package xyz.xaprosmp.adaptivesleepy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SleepRequirementTest {
    private static SleepConfig config(int thunder, int veto) {
        SleepConfig config = new SleepConfig();
        config.setThunderstormSleepingPercentage(thunder);
        config.setVetoSleepingPercentage(veto);
        return config;
    }

    @Test
    void baseAppliesWithoutConditions() {
        assertEquals(30, SleepRequirement.effective(30, false, false, config(50, 80)));
    }

    @Test
    void activeConditionRaisesButNeverLowersTheBase() {
        assertEquals(50, SleepRequirement.effective(30, true, false, config(50, 80)));
        assertEquals(70, SleepRequirement.effective(70, true, false, config(50, 80)));
    }

    @Test
    void highestActiveConditionWins() {
        assertEquals(80, SleepRequirement.effective(30, true, true, config(50, 80)));
        assertEquals(90, SleepRequirement.effective(30, true, true, config(90, 80)));
    }

    @Test
    void disabledConditionIsIgnoredWhileActive() {
        assertEquals(30, SleepRequirement.effective(30, true, true, config(SleepConfig.DISABLED, SleepConfig.DISABLED)));
    }

    @Test
    void valuesAbove100PassThroughToBlockSkipping() {
        assertEquals(101, SleepRequirement.effective(30, false, true, config(50, 101)));
    }
}
