package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.RegistryManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiggerAE2StorageTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void onlyItemFluidChemicalExpandToFiveTypes() {
        for (BiggerAE2Channel channel : BiggerAE2Channel.values()) {
            assertEquals(Set.of("item", "fluid", "chemical").contains(channel.id) ? 5 : 1, channel.typeLimit);
        }
    }

    @Test
    void quantumRejectsSixthTypeAndRemainsCapacityBounded() {
        var stack = stack(false);
        var cell = new ECOStorageCell(stack, null);
        try (var storage = mockStatic(StorageCells.class)) {
            for (var item : List.of(Items.STICK, Items.PAPER, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND)) {
                assertEquals(1, cell.insert(AEItemKey.of(item), 1, Actionable.MODULATE, null));
            }
            assertEquals(0, cell.insert(AEItemKey.of(Items.EMERALD), 1, Actionable.MODULATE, null));
            var stick = AEItemKey.of(Items.STICK);
            long accepted = cell.insert(stick, Long.MAX_VALUE, Actionable.MODULATE, null);
            assertTrue(accepted > 0 && accepted < Long.MAX_VALUE);
            assertEquals(0, cell.insert(stick, 1, Actionable.MODULATE, null));
            assertTrue(cell.getUsedBytes() <= BiggerAE2StorageCellItem.QUANTUM_BYTES);
        }
    }

    @Test
    void singularityPreservesBeyondLongAcrossSimulationCopyAndReload() {
        var original = stack(true);
        var key = AEItemKey.of(Items.STICK);
        BigInteger offered = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)).add(BigInteger.TEN);
        var cell = new BiggerAE2SingularityStorageCell(original, null);
        try (var storage = mockStatic(StorageCells.class)) {
            assertEquals(offered, cell.insertBigInteger(key, offered, Actionable.SIMULATE, null));
            assertEquals(0, cell.getStoredItemTypes());
            assertEquals(offered, cell.insertBigInteger(key, offered, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE, cell.getAvailableStacks().get(key));
            assertEquals(offered, exact(cell, key));

            var copy = new ItemStack(Items.PAPER);
            copy.applyComponents(original.getComponentsPatch());
            var copied = spy(copy);
            doReturn(original.getItem()).when(copied).getItem();
            var restored = new BiggerAE2SingularityStorageCell(copied, null);
            assertEquals(Long.MAX_VALUE, restored.extract(key, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(offered.subtract(BigInteger.valueOf(Long.MAX_VALUE)), exact(restored, key));
            assertEquals(offered, exact(cell, key));
            assertFalse(restored.isInfiniteStorageEligible());
            assertFalse(restored.canFitInsideCell());
        }
    }

    @Test
    void singularityReusesTypeSlotAfterLastUnitIsExtracted() {
        var cell = new BiggerAE2SingularityStorageCell(stack(true), null);
        try (var storage = mockStatic(StorageCells.class)) {
            for (var item : List.of(Items.STICK, Items.PAPER, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND)) {
                assertEquals(10, cell.insert(AEItemKey.of(item), 10, Actionable.MODULATE, null));
            }
            var sixth = AEItemKey.of(Items.EMERALD);
            assertEquals(0, cell.insert(sixth, 10, Actionable.MODULATE, null));
            assertEquals(10, cell.extract(AEItemKey.of(Items.STICK), 100, Actionable.MODULATE, null));
            assertEquals(10, cell.insert(sixth, 10, Actionable.MODULATE, null));
            assertEquals(5, cell.getStoredItemTypes());
        }
    }

    @Test
    void singularityComponentRoundTripsThroughDiskAndNetworkCodecs() {
        try (var keys = mockStatic(AEKeyTypesInternal.class); var sync = mockStatic(RegistryManager.class)) {
        keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
        var key = AEFluidKey.of(Fluids.WATER);
        BigInteger amount = BigInteger.ONE.shiftLeft(256).add(BigInteger.valueOf(73));
        var value = new BiggerAE2SingularityContents(List.of(new BiggerAE2SingularityContents.Entry(key, amount)));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var encoded = BiggerAE2SingularityContents.CODEC.encodeStart(ops, value).getOrThrow();
        assertEquals(value, BiggerAE2SingularityContents.CODEC.parse(ops, encoded).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            BiggerAE2SingularityContents.STREAM_CODEC.encode(buffer, value);
            assertEquals(value, BiggerAE2SingularityContents.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        }
    }

    @Test
    void batchingPersistsExactContentsAtScopeClose() {
        var stack = stack(true);
        var cell = new BiggerAE2SingularityStorageCell(stack, null);
        try (var storage = mockStatic(StorageCells.class)) {
            try (var batch = ECOCellMutationBatch.open()) {
                cell.insert(AEItemKey.of(Items.STICK), 5, Actionable.MODULATE, null);
                assertFalse(stack.has(BiggerAE2SingularityContents.TYPE));
            }
            assertEquals(BigInteger.valueOf(5), stack.get(BiggerAE2SingularityContents.TYPE).entries().getFirst().amount());
        }
    }

    private static BigInteger exact(BiggerAE2SingularityStorageCell cell, AEItemKey key) {
        BigInteger[] value = {BigInteger.ZERO};
        cell.neoecoae$visitExactAmounts((found, amount) -> { if (key.equals(found)) value[0] = amount.value(); });
        return value[0];
    }

    static ItemStack stack(boolean singularity) {
        return stack(singularity, AEKeyType.items());
    }

    static ItemStack stack(boolean singularity, AEKeyType keyType) {
        var item = mock(BiggerAE2StorageCellItem.class);
        var tier = mock(IECOTier.class);
        when(item.getTier()).thenReturn(tier);
        when(item.getKeyType()).thenReturn(keyType);
        when(item.getTotalTypes()).thenReturn(5);
        when(item.getCellType()).thenReturn(new ECOCellType(Component.literal("Test"), 5));
        when(item.getBytes()).thenReturn(singularity ? Long.MAX_VALUE : BiggerAE2StorageCellItem.QUANTUM_BYTES);
        when(item.getBytesPerType()).thenReturn(65_536);
        when(item.getUpgrades(any())).thenReturn(mock(IUpgradeInventory.class));
        ConfigInventory config;
        try (var types = mockStatic(AEKeyTypes.class)) {
            types.when(AEKeyTypes::getAll).thenReturn(Set.of(keyType));
            config = ConfigInventory.configTypes(5).supportedTypes(Set.of(keyType)).build();
        }
        when(item.getConfigInventory(any())).thenReturn(config);
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }
}
