package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.util.UUID;

/** Accepts physical worker outputs, including surplus, into their owning CPU. */
public interface ECOJobOutputReceiver {
    long neoecoae$insertWorkerOutput(UUID jobId, AEKey what, long amount, Actionable type);
}
