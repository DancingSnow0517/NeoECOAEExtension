package cn.dancingsnow.neoecoae.compat.advanced_ae;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.List;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOAdvancedAEPatternScalingTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void providerCatalogRecognizesAnEquivalentSeparatelyDecodedPattern() throws Exception {
        var definition = mock(AEItemKey.class);
        var original = decoded(definition);
        var decoded = decoded(definition);
        assertNotSame(original, decoded);
        assertEquals(original, decoded);
        var scaled = ECOAdvancedAEPatternScaling.scale(original, 64);
        assertTrue(new ArrayList<>(List.of(decoded)).contains(scaled), "AdvancedAE must recognize ECO's scaled view");
        assertEquals(scaled, ECOAdvancedAEPatternScaling.scale(decoded, 64));
        assertEquals(scaled.hashCode(), decoded.hashCode());
        assertNotEquals(scaled, ECOAdvancedAEPatternScaling.scale(decoded, 32));
        assertNotEquals(scaled, null);
    }

    private static AdvProcessingPattern decoded(AEItemKey definition) throws Exception {
        // Keep AdvancedAE's real equality; its codec constructor needs optional-mod registration and JarJar libraries.
        var allocator = Class.forName("org.objenesis.ObjenesisStd").getConstructor().newInstance();
        var pattern = (AdvProcessingPattern) allocator.getClass().getMethod("newInstance", Class.class)
                .invoke(allocator, AdvProcessingPattern.class);
        var field = AdvProcessingPattern.class.getDeclaredField("definition");
        field.setAccessible(true);
        field.set(pattern, definition);
        return pattern;
    }
}
