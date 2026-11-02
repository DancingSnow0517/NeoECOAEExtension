package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageChannelDisplays;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddonStorageChannelTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @Test void aliasLookupIsDeterministicAndRemovalRestoresTheFallback() {
        var channel = id("channel"); var alias = id("alias");
        var display = new ECOStorageChannelDisplays.Display(channel, Set.of(alias), Component.literal("Custom resource"),
            0x123456, id("textures/icon.png"), id("textures/swatch.png"), -5);
        ECOStorageChannelDisplays.register(channel, 0, display);
        try {
            assertSame(display, ECOStorageChannelDisplays.resolve(alias));
            assertSame(display, ECOStorageChannelDisplays.resolve(channel));
        } finally { ECOStorageChannelDisplays.unregister(channel); }
        assertNull(ECOStorageChannelDisplays.resolve(alias));
    }

    @Test void customArtworkSurvivesTransportAndAliasesMergeWithoutLosingExactAmounts() {
        var exact = BigInteger.TEN.pow(40);
        var one = line(1, id("channel"), exact);
        var two = line(2, id("channel"), exact);
        var received = StoragePanelState.fromTag(state(List.of(one, two)).toTag());
        assertEquals(List.of(one, two), received.types());
        var segments = StorageHostCharts.capacitySegments(received, 100);
        assertEquals(1, segments.size());
        assertEquals(100, segments.getFirst().width());
        assertEquals(id("textures/swatch.png"), segments.getFirst().texture());
        assertEquals(exact.multiply(BigInteger.TWO), received.types().stream().map(StoragePanelState.TypeLine::exactUsedBytes)
            .reduce(BigInteger.ZERO, BigInteger::add));
        assertEquals(2, StorageHostCharts.capacitySegments(state(List.of(one, line(3, id("different"), exact))), 100).size());
    }
    private static StoragePanelState.TypeLine line(int id, ResourceLocation channel, BigInteger amount) {
        return new StoragePanelState.TypeLine(id, Component.literal("Custom"), 0x123456, 1, 2, Long.MAX_VALUE, Long.MAX_VALUE,
            amount.toString(), StoragePanelTextures.FALLBACK_SWATCH, amount, channel, id("textures/icon.png"), id("textures/swatch.png"));
    }
    private static StoragePanelState state(List<StoragePanelState.TypeLine> types) {
        return new StoragePanelState(Component.literal("Host"), 0, true, false, false, 0, 0, 0, "0", 0, 0, 0,
            List.of(), types, "", 0, false);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("addon_channel_test", path); }
}
