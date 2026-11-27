package cn.dancingsnow.neoecoae.integration.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath.classify.ECORecipeClassifier;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Thunderbolt/time-wheel adapter. The caller already extracted a homogeneous batch and owns energy
 * accounting; the drain returns the number of copies the F-series host did not accept.
 */
public final class ECOThunderboltBatchBridge {
    private ECOThunderboltBatchBridge() {}

    /**
     * Fills every currently free FX lane that can accept this pattern, not just the highest-ranked
     * worker. Time-wheel CPUs count one {@code pushBatch} as one successful dispatch, so a single-lane
     * drain would leave half of a two-FX host idle until the next tick.
     */
    public static long drainFastPath(ECOCraftingPatternBusBlockEntity bus, IPatternDetails pattern,
            KeyCounter[] oneCopy, long maxCraft, Level level, @Nullable UUID jobId) {
        if (maxCraft <= 0L) return 0L;
        long leftover = maxCraft;
        while (leftover > 0L) {
            var batch = ECOFastPathFacade.prepareAllocated(bus, pattern, oneCopy, leftover, level, jobId);
            if (batch == null) break;
            long accepted = batch.craftCount();
            if (accepted <= 0L || accepted > leftover) break;
            if (!batch.submit(amount -> new ECOFastPathFacade.Reservation() {
                @Override public void commit() {}
                @Override public void refund() {}
            })) {
                break;
            }
            leftover -= accepted;
        }
        return leftover;
    }

    public static boolean fastPathEligible(ECOCraftingPatternBusBlockEntity bus, IPatternDetails pattern) {
        if (bus.isBusy() || !(pattern instanceof IMolecularAssemblerSupportedPattern)) return false;
        var classification = ECORecipeClassifier.classify(pattern);
        return classification.type() == ECORecipeClassifier.Type.NORMAL && classification.supported();
    }
}
