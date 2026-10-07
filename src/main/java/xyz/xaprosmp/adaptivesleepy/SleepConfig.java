package xyz.xaprosmp.adaptivesleepy;

/**
 * In-memory config values. Loaded and saved by {@link ConfigFile}; the option schema (ids,
 * ranges, comments) lives in {@link ConfigOptions}. Percentages use {@link #DISABLED} (-1) to
 * turn a condition off; for the base it means "the base is whatever /gamerule sets".
 */
public final class SleepConfig {
    public static final int DISABLED = -1;
    private volatile int baseSleepingPercentage = DISABLED;
    private volatile int thunderstormSleepingPercentage = 50;
    private volatile int vetoSleepingPercentage = 50;
    private volatile int permissionVeto = 0;
    private volatile int permissionVetoList = 0;
    private volatile int permissionConfig = 2;
    private volatile boolean broadcastVetoActivation = false;
    private volatile boolean broadcastVetoCancellation = false;
    private volatile boolean broadcastVetoExpiration = false;
    private volatile boolean broadcastThunderstorm = false;
    private volatile boolean commandFeedback = true;
    private volatile boolean showEffectivePercentage = true;

    public int baseSleepingPercentage() {
        return this.baseSleepingPercentage;
    }

    public int thunderstormSleepingPercentage() {
        return this.thunderstormSleepingPercentage;
    }

    public int vetoSleepingPercentage() {
        return this.vetoSleepingPercentage;
    }

    public int permissionVeto() {
        return this.permissionVeto;
    }

    public int permissionVetoList() {
        return this.permissionVetoList;
    }

    public int permissionConfig() {
        return this.permissionConfig;
    }

    public boolean broadcastVetoActivation() {
        return this.broadcastVetoActivation;
    }

    public boolean broadcastVetoCancellation() {
        return this.broadcastVetoCancellation;
    }

    public boolean broadcastVetoExpiration() {
        return this.broadcastVetoExpiration;
    }

    public boolean broadcastThunderstorm() {
        return this.broadcastThunderstorm;
    }

    public boolean commandFeedback() {
        return this.commandFeedback;
    }

    public boolean showEffectivePercentage() {
        return this.showEffectivePercentage;
    }

    void setBaseSleepingPercentage(int v) {
        this.baseSleepingPercentage = v;
    }

    void setThunderstormSleepingPercentage(int v) {
        this.thunderstormSleepingPercentage = v;
    }

    void setVetoSleepingPercentage(int v) {
        this.vetoSleepingPercentage = v;
    }

    void setPermissionVeto(int v) {
        this.permissionVeto = v;
    }

    void setPermissionVetoList(int v) {
        this.permissionVetoList = v;
    }

    void setPermissionConfig(int v) {
        this.permissionConfig = v;
    }

    void setBroadcastVetoActivation(boolean v) {
        this.broadcastVetoActivation = v;
    }

    void setBroadcastVetoCancellation(boolean v) {
        this.broadcastVetoCancellation = v;
    }

    void setBroadcastVetoExpiration(boolean v) {
        this.broadcastVetoExpiration = v;
    }

    void setBroadcastThunderstorm(boolean v) {
        this.broadcastThunderstorm = v;
    }

    void setCommandFeedback(boolean v) {
        this.commandFeedback = v;
    }

    void setShowEffectivePercentage(boolean v) {
        this.showEffectivePercentage = v;
    }

    public boolean baseOverrideEnabled() {
        return this.baseSleepingPercentage != DISABLED;
    }

    public boolean thunderstormEnabled() {
        return this.thunderstormSleepingPercentage != DISABLED;
    }

    public boolean vetoEnabled() {
        return this.vetoSleepingPercentage != DISABLED;
    }
}
