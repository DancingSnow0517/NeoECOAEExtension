package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Thunderbolt/time-wheel adapter. The caller already extracted a homogeneous batch and owns energy
 * accounting; this only reports how many of those copies the F-series host actually accepted.
 */
public final class ECOThunderboltBatchBridge {
    private ECOThunderboltBatchBridge() {}

    public static long capacity(ECOCraftingPatternBusBlockEntity bus, IPatternDetails pattern) {
        if (!fastPathEligible(bus, pattern)) return 0L;
        int slots = bus.getAvailableThreadSlots();
        return slots > 1 ? slots : 0L;
    }

    public static long push(ECOCraftingPatternBusBlockEntity bus, IPatternDetails pattern,
            KeyCounter[] oneCopy, long maxCraft, Level level, @Nullable UUID jobId) {
        if (maxCraft <= 0L) return 0L;
        long leftover = drainFastPath(bus, pattern, oneCopy, maxCraft, level, jobId);
        if (leftover == maxCraft && bus.pushPattern(pattern, oneCopy, jobId)) {
            leftover--;
        }
        return leftover;
    }

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
