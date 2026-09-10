package org.gtlcore.gtlcore.integration.ae2.common;

/**
 * Small binary-compatible surface used by GTLCore's optional long amount GUI.
 * Kept here so the menu remains usable when GTLCore is present at runtime.
 */
public interface ILongCraftAmountMenu {
    void gtlcore$confirmLongAmount(long amount, boolean craftMissingAmount, boolean startImmediately);

    void gtlcore$setLongWhatToCraft(appeng.api.stacks.AEKey whatToCraft, long amount);
}
