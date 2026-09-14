package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Both Thunderbolt API generations delegate allocated inputs to the same ECO execution path. */
public final class ECOThunderboltBatchBridge {
    private ECOThunderboltBatchBridge() {}

    public static long capacity(ECOCraftingPatternBusBlockEntity bus, IPatternDetails pattern) {
        return !bus.isBusy() && pattern instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern
            && ECORecipeClassifier.classify(pattern).type() == ECORecipeClassifier.Type.NORMAL
            ? Long.MAX_VALUE : 0;
    }

    public static long push(ECOCraftingPatternBusBlockEntity bus, IPatternDetails pattern,
            KeyCounter[] oneCopy, long maxCraft, Level level, @Nullable UUID jobId) {
        var batch = ECOFastPathFacade.prepareAllocated(bus, pattern, oneCopy, maxCraft, level, jobId);
        if (batch == null) return maxCraft;
        // The calling CPU owns reservation, energy accounting and reinsertion of unaccepted copies.
        boolean accepted = batch.submit(amount -> new ECOFastPathFacade.Reservation() {
            public void commit() {}
            public void refund() {}
        });
        return accepted ? maxCraft - batch.craftCount() : maxCraft;
    }

    public static long pushLegacy(ECOCraftingPatternBusBlockEntity bus, Object context) {
        // Decode before dispatch: never turn an exception after submission into a retry of the same inputs.
        final IPatternDetails pattern;
        final KeyCounter[] inputs;
        final long copies;
        final Level level;
        final UUID jobId;
        try {
            Class<?> type = context.getClass();
            pattern = (IPatternDetails) type.getMethod("details").invoke(context);
            inputs = (KeyCounter[]) type.getMethod("oneCopyTemplate").invoke(context);
            copies = (long) type.getMethod("maxCraft").invoke(context);
            level = (Level) type.getMethod("level").invoke(context);
            jobId = (UUID) type.getMethod("craftingJobId").invoke(context);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported legacy Thunderbolt batch context", failure);
        }
        return push(bus, pattern, inputs, copies, level, jobId);
    }
}
