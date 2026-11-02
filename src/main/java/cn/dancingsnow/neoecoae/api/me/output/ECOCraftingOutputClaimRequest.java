package cn.dancingsnow.neoecoae.api.me.output;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/**
 * Describes one attempt to claim a physical output for the current ECO crafting job.
 *
 * <p>The expected key is the key reserved by the crafting plan. The actual key is the key produced by the
 * machine. They are intentionally separate: integrations that create a dynamic or substituted output can tell
 * ECO which reserved entry was consumed without exposing ECO's waiting inventory.</p>
 */
public record ECOCraftingOutputClaimRequest(
        @Nullable UUID craftingJobId,
        @Nullable AEKey expectedKey,
        @Nullable AEKey actualKey,
        long amount,
        @Nullable Actionable mode) {
}
