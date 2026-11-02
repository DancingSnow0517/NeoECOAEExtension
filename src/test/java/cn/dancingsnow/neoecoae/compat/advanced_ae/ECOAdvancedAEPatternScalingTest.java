package cn.dancingsnow.neoecoae.compat.advanced_ae;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Items;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOAdvancedAEPatternScalingTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void providerCatalogRecognizesAnEquivalentSeparatelyDecodedPattern() {
        var base = cn.dancingsnow.neoecoae.util.NativePatternTestSupport.processing(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        var original = new AdvProcessingPattern(base.getDefinition());
        var decoded = new AdvProcessingPattern(base.getDefinition());
        assertNotSame(original, decoded);
        assertEquals(original, decoded);
        var scaled = ECOAdvancedAEPatternScaling.scale(original, 64);
        assertTrue(new ArrayList<>(List.of(decoded)).contains(scaled), "AdvancedAE must recognize ECO's scaled view");
        assertEquals(scaled, ECOAdvancedAEPatternScaling.scale(decoded, 64));
        assertEquals(scaled.hashCode(), decoded.hashCode());
        assertNotEquals(scaled, ECOAdvancedAEPatternScaling.scale(decoded, 32));
        assertNotEquals(scaled, null);
    }
}
