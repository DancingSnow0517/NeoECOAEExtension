package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

class ECOExternalPatternNormalizationTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }
    @Test void automaticWrappersMergeWithoutChangingEncodedAmountsOrOverflowingCounts() {
        var base = base();
        var ten = new ScaledProcessingPattern(base, 10);
        var nested = new ScaledProcessingPattern(ten, 2);
        var result = ECOExternalPatternNormalization.normalize(Map.of(base, 3L, ten, Long.MAX_VALUE, nested, 4L));
        assertEquals(Map.of(base, BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN).add(BigInteger.valueOf(83))), result);
        assertEquals(64, base.getOutputs().getFirst().amount(), "Manually encoded amounts stay part of the recipe");
    }
    @Test void rejectsChangedOutputKeysBeforeImportingPlan() {
        var base = base();
        var scaled = mock(ScaledProcessingPattern.class);
        when(scaled.getOriginal()).thenReturn(base);
        when(scaled.getOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 128)));
        assertThrows(IllegalArgumentException.class, () -> ECOExternalPatternNormalization.normalize(Map.of(scaled, 1L)));
    }
    private static IPatternDetails base() {
        var base = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)});
        when(input.getMultiplier()).thenReturn(32L);
        when(base.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(base.getOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 64)));
        return base;
    }
}
