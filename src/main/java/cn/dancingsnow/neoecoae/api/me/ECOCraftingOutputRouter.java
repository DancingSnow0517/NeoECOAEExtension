package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.util.UUID;

/** Routes outputs from an ECO worker to the CPU that owns the corresponding crafting job. */
public interface ECOCraftingOutputRouter {
    long neoecoae$insertIntoCpuForJob(UUID craftingJobId, AEKey what, long amount, Actionable type);
}
