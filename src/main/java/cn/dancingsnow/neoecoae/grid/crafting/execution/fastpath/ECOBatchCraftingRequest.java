package cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath;

import cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath.verify.ECOVerifiedFastPathExecution;

/**
 * Legacy type retained as a binary-compatible target for optional crafting-tracker integrations.
 * The active fast-path pipeline uses {@link ECOVerifiedFastPathExecution} instead.
 */
public final class ECOBatchCraftingRequest {
    private ECOBatchCraftingRequest() {
    }
}
