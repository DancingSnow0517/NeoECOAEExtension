package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.fastpath.EcoFastpathHost;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
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
        if (maxCraft <= 0L) return 0L;
        var inputs = java.util.Arrays.stream(oneCopy).map(ECOFastPathStacks::copyCounter).toList();
        var request = new EcoFastpathHost.FastpathRequest(pattern.getDefinition(), inputs, maxCraft,
            EcoFastpathHost.CAPABILITY_ID, EcoFastpathHost.API_VERSION, UUID.randomUUID());
        var capability = bus.inspect(request);
        if (capability.acceptedAmount() <= 0L) return maxCraft;
        var submission = bus.submit(request);
        long accepted = submission.acceptedAmount();
        if (accepted < 0L || accepted > maxCraft
            || submission.unacceptedAmount() != maxCraft - accepted) return maxCraft;
        return maxCraft - accepted;
    }

}
