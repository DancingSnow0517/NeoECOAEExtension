package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.UUID;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.world.level.Level;

/** Optional single-craft execution adapter. Scheduling and inventory remain owned by the CPU. */
public final class ECOSingleCraftingExecutor {
    private ECOSingleCraftingExecutor() {}

    public static boolean pushPattern(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            KeyCounter outputs,
            KeyCounter containers,
            Level level,
            UUID craftingJobId) {
        if (!(provider instanceof ECOCraftingPatternBusBlockEntity bus)) {
            return provider.pushPattern(pattern, inputs);
        }

        if (NEConfig.ecoAe2FastPathEnabled && !NEConfig.postCraftingEvent && level != null) {
            var execution = prepare(pattern, inputs, outputs, containers, level);
            if (execution != null && execution.canUseFastPath()
                    && bus.pushPattern(execution, craftingJobId)) {
                return true;
            }
        }

        // No work was accepted: use the ordinary assembler path with the same extracted inputs.
        return bus.pushPatternSlow(pattern, inputs, craftingJobId);
    }

    private static ECOExtractedPatternExecution prepare(
            IPatternDetails pattern, KeyCounter[] inputs, KeyCounter outputs,
            KeyCounter containers, Level level) {
        try {
            return ECOExtractedPatternExecution.create(pattern, inputs, outputs, containers, level);
        } catch (RuntimeException unavailable) {
            // Preparation has no provider or inventory side effects.
            return null;
        }
    }
}
