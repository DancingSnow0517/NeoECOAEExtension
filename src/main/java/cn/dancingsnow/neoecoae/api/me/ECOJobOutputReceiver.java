package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.util.UUID;

/** Accepts a worker output only when this CPU still owns the matching crafting job. */
public interface ECOJobOutputReceiver {
    long neoecoae$insertWorkerOutput(UUID craftingJobId, AEKey what, long amount, Actionable type);
}
