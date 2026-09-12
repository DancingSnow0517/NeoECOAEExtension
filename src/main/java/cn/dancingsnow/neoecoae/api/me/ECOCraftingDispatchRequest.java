package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.world.level.Level;

/** One candidate and its resolved, still-virtual input set for provider dispatch. */
record ECOCraftingDispatchRequest(
        ExecutingCraftingJob job,
        ECOExecutionRuntime.DispatchCandidate candidate,
        IPatternDetails pattern,
        KeyCounter[] inputs,
        KeyCounter outputs,
        KeyCounter remainders,
        long allowedCrafts,
        ListCraftingInventory inventory,
        Level level) {
}
