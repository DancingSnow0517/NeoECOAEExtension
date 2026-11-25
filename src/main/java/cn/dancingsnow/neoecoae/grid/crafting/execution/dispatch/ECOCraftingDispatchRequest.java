package cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch;

import cn.dancingsnow.neoecoae.grid.crafting.execution.job.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.grid.crafting.execution.job.ExecutingCraftingJob;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.world.level.Level;

/** One candidate and its resolved, still-virtual input set for provider dispatch. */
public record ECOCraftingDispatchRequest(
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
