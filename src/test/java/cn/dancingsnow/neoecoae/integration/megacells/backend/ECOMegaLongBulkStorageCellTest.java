package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOMegaLongBulkStorageCellTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void compressionCardFeaturesStayDisabled() throws Exception {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        try (var compression = mockStatic(MegaCompressionChains.class, CALLS_REAL_METHODS)) {
            compression.when(() -> MegaCompressionChains.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var cell = compressedCell(ingot);
            assertFalse(cell.isCompressionEnabled());
            assertEquals(0, cell.insert(block, 1, Actionable.MODULATE, null));
            assertTrue(cell.getDecompressionPatterns().isEmpty());
        }
    }

    @Test
    void ordinaryItemsRoundTripAtLongLimitAndKeepComponents() throws Exception {
        var named = new ItemStack(Items.STICK);
        named.setHoverName(Component.literal("Marked stick"));
        var key = AEItemKey.of(named);
        var cell = ordinaryCell(key);
        var empty = new CompressionChain();
        try (var compression = mockStatic(MegaCompressionChains.class, CALLS_REAL_METHODS)) {
            compression.when(() -> MegaCompressionChains.getChain(any(AEItemKey.class))).thenReturn(empty);
            assertEquals(Long.MAX_VALUE, cell.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, null));
            assertEquals(0, cell.getStoredItemCount());
            assertEquals(0, cell.insert(AEItemKey.of(Items.STICK), 1, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE, cell.insert(key, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(0, cell.insert(key, 1, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE, cell.getAvailableStacks().get(key));
            assertEquals(Long.MAX_VALUE, cell.getStoredItemCount());
            var exact = java.math.BigInteger.valueOf(Long.MAX_VALUE);
            assertEquals(exact, cell.getStoredItemCountExact());
            assertEquals(exact, cell.extractBigInteger(key, exact.add(java.math.BigInteger.ONE), Actionable.SIMULATE, null));
            java.util.Map<appeng.api.stacks.AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> amounts = new java.util.HashMap<>();
            cell.neoecoae$visitExactAmounts(amounts::put);
            assertEquals(exact, amounts.get(key).value());

            var stackField = ECOMegaLongBulkStorageCell.class.getDeclaredField("stack");
            stackField.setAccessible(true);
            var restored = ordinaryCell(key);
            stackField.set(restored, ((ItemStack) stackField.get(cell)).copy());
            var load = ECOMegaLongBulkStorageCell.class.getDeclaredMethod("loadStoredUnits");
            load.setAccessible(true);
            load.invoke(restored);
            assertEquals(Long.MAX_VALUE, restored.extract(key, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(0, restored.getStoredItemCount());
            assertTrue(restored.getAvailableStacks().isEmpty());
        }
    }

    @Test
    void changingMarkersCannotBypassTypeLimitButOldStockRemainsExtractable() throws Exception {
        var oldKey = AEItemKey.of(Items.STICK);
        var newKey = AEItemKey.of(Items.PAPER);
        var cell = ordinaryCell(oldKey);
        doReturn(1L).when(cell).getTotalItemTypes();
        var empty = new CompressionChain();
        try (var compression = mockStatic(MegaCompressionChains.class, CALLS_REAL_METHODS)) {
            compression.when(() -> MegaCompressionChains.getChain(any(AEItemKey.class))).thenReturn(empty);
            assertEquals(10, cell.insert(oldKey, 10, Actionable.MODULATE, null));
            assertEquals(1, cell.insert(oldKey, 1, Actionable.SIMULATE, null));
            cell.getConfigInventory().setStack(0, new GenericStack(newKey, 0));
            assertEquals(0, cell.insert(newKey, 1, Actionable.SIMULATE, null));
            assertEquals(0, cell.insert(newKey, 1, Actionable.MODULATE, null));
            assertFalse(cell.isPreferredStorageFor(newKey, null));
            assertEquals(0, cell.getRemainingItemCount());
            assertEquals(0, cell.insert(oldKey, 1, Actionable.MODULATE, null));
            assertEquals(10, cell.extract(oldKey, 10, Actionable.MODULATE, null));
            assertTrue(cell.isPreferredStorageFor(newKey, null));
            assertEquals(1, cell.insert(newKey, 1, Actionable.MODULATE, null));
        }
    }

    private static ECOMegaLongBulkStorageCell ordinaryCell(AEItemKey marker) throws Exception {
        var cell = mock(ECOMegaLongBulkStorageCell.class, CALLS_REAL_METHODS);
        initializeState(cell);
        ConfigInventory config;
        try (var keyTypes = mockStatic(AEKeyTypes.class)) {
            keyTypes.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items()));
            config = ConfigInventory.configTypes(key -> AEKeyType.items().contains(key), 1, null);
        }
        config.setStack(0, new GenericStack(marker, 0));
        doReturn(config).when(cell).getConfigInventory();
        doAnswer(ignored -> java.util.Collections.singletonList(config.getStack(0))).when(cell).configuredStacks();
        doReturn(mock(IUpgradeInventory.class)).when(cell).getUpgradesInventory();
        doReturn(false).when(cell).hasEcoMegaUpgradeCard();
        var units = ECOMegaLongBulkStorageCell.class.getDeclaredField("storedUnits");
        units.setAccessible(true);
        units.set(cell, new it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap<AEItemKey>());
        var stack = ECOMegaLongBulkStorageCell.class.getDeclaredField("stack");
        stack.setAccessible(true);
        stack.set(cell, new ItemStack(Items.PAPER));
        return cell;
    }

    private static void initializeState(ECOMegaLongBulkStorageCell cell) throws Exception {
        doReturn(new cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData(java.util.UUID.randomUUID()))
            .when(cell).storageData();
        for (String name : List.of("definitions", "cutoffs")) {
            var field = ECOMegaLongBulkStorageCell.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(cell, new LinkedHashMap<>());
        }
        var unresolved = ECOMegaLongBulkStorageCell.class.getDeclaredField("unresolvedEntries");
        unresolved.setAccessible(true);
        unresolved.set(cell, new net.minecraft.nbt.ListTag());
        var stack = ECOMegaLongBulkStorageCell.class.getDeclaredField("stack");
        stack.setAccessible(true);
        stack.set(cell, new ItemStack(Items.PAPER));
    }

    private static CompressionChain ironChain(int ratio) throws Exception {
        var chain = new CompressionChain();
        chain.add(AEItemKey.of(Items.IRON_NUGGET), (byte) 1);
        chain.add(AEItemKey.of(Items.IRON_INGOT), (byte) ratio);
        chain.add(AEItemKey.of(Items.IRON_BLOCK), (byte) 9);
        return chain;
    }

    private static ECOMegaLongBulkStorageCell compressedCell(AEItemKey marker) throws Exception {
        var cell = ordinaryCell(marker);
        var upgrades = mock(IUpgradeInventory.class);
        when(upgrades.isInstalled(any(ItemLike.class))).thenReturn(true);
        doReturn(upgrades).when(cell).getUpgradesInventory();
        return cell;
    }

    private static ItemStack savedStack(ECOMegaLongBulkStorageCell cell) throws Exception {
        var field = ECOMegaLongBulkStorageCell.class.getDeclaredField("stack");
        field.setAccessible(true);
        return ((ItemStack) field.get(cell)).copy();
    }

    private static ECOMegaLongBulkStorageCell restore(ItemStack saved, AEItemKey marker) throws Exception {
        var cell = compressedCell(marker);
        var field = ECOMegaLongBulkStorageCell.class.getDeclaredField("stack");
        field.setAccessible(true);
        field.set(cell, saved);
        var load = ECOMegaLongBulkStorageCell.class.getDeclaredMethod("loadStoredUnits");
        load.setAccessible(true);
        load.invoke(cell);
        return cell;
    }

    @Test
    void reloadRecoversSavedAtomsWithoutUsingNewRatios() throws Exception {
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        try (var compression = mockStatic(MegaCompressionChains.class, CALLS_REAL_METHODS)) {
            compression.when(() -> MegaCompressionChains.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var cell = compressedCell(ingot);
            cell.insert(ingot, 10, Actionable.MODULATE, null);
            cell.getAvailableStacks();
            var saved = savedStack(cell);
            compression.when(() -> MegaCompressionChains.getChain(any(AEItemKey.class))).thenReturn(ironChain(4));
            for (var inventory : List.of(cell, restore(saved, ingot))) {
                assertEquals(90, inventory.getAvailableStacks().get(nugget));
                assertEquals(0, inventory.getAvailableStacks().get(ingot));
                assertEquals(0, inventory.insert(ingot, 1, Actionable.MODULATE, null));
                assertEquals(0, inventory.extract(ingot, 1, Actionable.MODULATE, null));
                assertEquals(90, inventory.extract(nugget, 100, Actionable.MODULATE, null));
            }
        }
    }

    @Test
    void legacyMigrationRetainsCutoffAndInvalidData() throws Exception {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        try (var compression = mockStatic(MegaCompressionChains.class, CALLS_REAL_METHODS)) {
            compression.when(() -> MegaCompressionChains.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var entries = new net.minecraft.nbt.ListTag();
            var valid = new net.minecraft.nbt.CompoundTag();
            valid.putString("item", "minecraft:iron_ingot");
            valid.putLong("units", 91);
            entries.add(valid);
            var missing = new net.minecraft.nbt.CompoundTag();
            missing.putString("item", "missing:unknown");
            missing.putLong("units", 500);
            entries.add(missing);
            var data = new net.minecraft.nbt.CompoundTag();
            data.put("entries", entries);
            var custom = new net.minecraft.nbt.CompoundTag();
            custom.put("neoecoae_mega_long_bulk", data);
            var saved = new ItemStack(Items.PAPER);
            saved.setTag(custom);
            var cell = restore(saved, ingot);
            assertEquals(10, cell.getAvailableStacks().get(ingot));
            assertEquals(1, cell.getUnresolvedEntryCount());
            assertEquals(2, cell.getStoredItemTypes());
            cell.persist();
            var restored = restore(savedStack(cell), ingot);
            assertEquals(10, restored.getAvailableStacks().get(ingot));
            assertEquals(1, restored.getUnresolvedEntryCount());
            restored.clearAllStoredStacks();
            assertFalse(restored.canFitInsideCell());
            assertEquals(1, restore(savedStack(restored), ingot).getUnresolvedEntryCount());
        }
    }

}
