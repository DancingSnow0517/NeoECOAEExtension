package cn.dancingsnow.neoecoae.api.extension;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ECOExtensionRegistryTest {
    @Test void priorityAndIdAreDeterministicAndPublishedSnapshotsAreImmutable() {
        var registry = new ECOExtensionRegistry<String>();
        registry.register(id("z"), 10, "z");
        var previous = registry.entries();
        registry.register(id("low"), -1, "low");
        registry.register(id("a"), 10, "a");
        assertEquals(List.of("a", "z", "low"), registry.entries().stream().map(ECOExtensionRegistry.Entry::extension).toList());
        assertEquals(1, previous.size());
        assertThrows(UnsupportedOperationException.class, () -> registry.entries().clear());
        long revision = registry.revision();
        assertThrows(IllegalArgumentException.class, () -> registry.register(id("a"), 0, "replacement"));
        assertEquals(revision, registry.revision());
        assertTrue(registry.unregister(id("a")));
        assertTrue(registry.revision() > revision);
        assertFalse(registry.unregister(id("missing")));
    }

    @Test void apiAdvertisesTheDocumentedExtensionFamilies() {
        assertEquals(1, ECOAddonApi.VERSION);
        assertTrue(ECOAddonApi.supports("workstation_modules"));
        assertFalse(ECOAddonApi.supports("unknown_future_api"));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("addon_api_test", path); }
}
