package cn.dancingsnow.neoecoae.api.me.output;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.util.UUID;

/** Optional output route implemented by the AdvancedAE-specific CraftingService mixin. */
public interface ECOAdvancedAeCraftingOutputRouter {
    long neoecoae$insertIntoAdvancedAeCpuForJob(UUID craftingJobId, AEKey what, long amount, Actionable type);
}
