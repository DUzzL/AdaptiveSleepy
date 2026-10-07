package xyz.xaprosmp.adaptivesleepy.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private {@code ServerLevel.announceSleepStatus()} (the "N/M players sleeping" action bar). */
@Mixin(ServerLevel.class)
public interface ServerLevelInvoker {
    @Invoker("announceSleepStatus")
    void adaptivesleepy$announceSleepStatus();
}
