package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecutionOutputsTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void sumsEachOutputTimesItsCraftCount() {
        var sink = new KeyCounter();
        ExecutionOutputs.addSaturating(Map.of(
                pattern(new GenericStack(iron(), 2)), progress(3),
                pattern(new GenericStack(iron(), 1), new GenericStack(diamond(), 4)), progress(5)), sink);
        assertEquals(11, sink.get(iron()));
        assertEquals(20, sink.get(diamond()));
    }

    @Test
    void saturatesInsteadOfOverflowing() {
        var sink = new KeyCounter();
        sink.set(iron(), Long.MAX_VALUE - 1);
        ExecutionOutputs.addSaturating(Map.of(
                pattern(new GenericStack(iron(), 2)), progress(2),
                pattern(new GenericStack(diamond(), Long.MAX_VALUE)), progress(2)), sink);
        assertEquals(Long.MAX_VALUE, sink.get(iron()));
        assertEquals(Long.MAX_VALUE, sink.get(diamond()));
    }

    @Test
    void exactAccumulatesBeyondLongRange() {
        var huge = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        var exact = progress(0);
        exact.setExact(huge, huge);
        var sink = new HashMap<AEKey, BigInteger>();
        ExecutionOutputs.addExact(Map.of(pattern(new GenericStack(iron(), 3)), exact), sink);
        assertEquals(huge.multiply(BigInteger.valueOf(3)), sink.get(iron()));
    }

    private static AEItemKey iron() {
        return AEItemKey.of(Items.IRON_INGOT);
    }

    private static AEItemKey diamond() {
        return AEItemKey.of(Items.DIAMOND);
    }

    private static IPatternDetails pattern(GenericStack... outputs) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getOutputs()).thenReturn(List.of(outputs));
        return pattern;
    }

    private static ExecutingCraftingJob.TaskProgress progress(long crafts) {
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.value = crafts;
        return progress;
    }
}
