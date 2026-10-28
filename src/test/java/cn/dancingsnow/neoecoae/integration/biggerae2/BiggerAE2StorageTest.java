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
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiggerAE2StorageTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        if (appeng.core.AEConfig.instance() == null) {
            appeng.core.AEConfig.load(java.nio.file.Files.createTempDirectory("neoecoae-bigger-ae2-config"));
        }
    }

    @Test
    void onlyItemFluidChemicalExpandToFiveTypes() {
        for (BiggerAE2Channel channel : BiggerAE2Channel.values()) {
            assertEquals(Set.of("item", "fluid", "chemical").contains(channel.id) ? 5 : 1, channel.typeLimit);
        }
    }

    @Test
    void quantumRejectsSixthTypeAndRemainsCapacityBounded() {
        var stack = stack(false);
        var cell = spy(new ECOStorageCell(stack, null));
        doReturn(true).when(cell).storageWritable();
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
            assertEquals(offered, cell.getStoredItemCountExact());
            assertEquals(offered, cell.extractBigInteger(key, offered, Actionable.SIMULATE, null));

            var copy = new ItemStack(Items.PAPER);
            copy.setTag(original.getTag() == null ? null : original.getTag().copy());
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
    void singularityContentsRoundTripThroughNativeItemDiskAndNetworkNbt() {
        var key = AEFluidKey.of(Fluids.WATER);
        BigInteger amount = BigInteger.ONE.shiftLeft(256).add(BigInteger.valueOf(73));
        var value = new BiggerAE2SingularityContents(List.of(new BiggerAE2SingularityContents.Entry(key, amount)));
        var stack = new ItemStack(Items.PAPER);
        BiggerAE2SingularityContents.write(stack, value);
        assertEquals(value, BiggerAE2SingularityContents.read(ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()))));
        var buffer = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeItem(stack);
            assertEquals(value, BiggerAE2SingularityContents.read(buffer.readItem()));
        } finally { buffer.release(); }
    }

    @Test
    void batchingPersistsExactContentsAtScopeClose() {
        var stack = stack(true);
        var cell = new BiggerAE2SingularityStorageCell(stack, null);
        try (var storage = mockStatic(StorageCells.class)) {
            try (var batch = ECOCellMutationBatch.open()) {
                cell.insert(AEItemKey.of(Items.STICK), 5, Actionable.MODULATE, null);
                assertTrue(BiggerAE2SingularityContents.read(stack).entries().isEmpty());
            }
            assertEquals(BigInteger.valueOf(5), BiggerAE2SingularityContents.read(stack).entries().get(0).amount());
        }
    }

    private static BigInteger exact(BiggerAE2SingularityStorageCell cell, AEItemKey key) {
        BigInteger[] value = {BigInteger.ZERO};
        cell.neoecoae$visitExactAmounts((found, amount) -> { if (key.equals(found)) value[0] = amount.value(); });
        return value[0];
    }

    private static ItemStack stack(boolean singularity) {
        var item = mock(BiggerAE2StorageCellItem.class);
        var tier = mock(IECOTier.class);
        when(item.getTier()).thenReturn(tier);
        when(item.getKeyType()).thenReturn(AEKeyType.items());
        when(item.getTotalTypes()).thenReturn(5);
        when(item.getCellType()).thenReturn(new ECOCellType(Component.literal("Test"), 5));
        when(item.getBytes()).thenReturn(singularity ? Long.MAX_VALUE : BiggerAE2StorageCellItem.QUANTUM_BYTES);
        when(item.getBytesPerType()).thenReturn(65_536);
        when(item.getUpgrades(any())).thenReturn(mock(IUpgradeInventory.class));
        ConfigInventory config;
        try (var types = mockStatic(AEKeyTypes.class)) {
            types.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items()));
            config = ConfigInventory.configTypes(key -> AEKeyType.items().contains(key), 5, null);
        }
        when(item.getConfigInventory(any())).thenReturn(config);
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }
}
