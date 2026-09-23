package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import gripe._90.megacells.misc.DecompressionPattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOMegaLongBulkStorageCellTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void ingotMarkerPublishesExtractableStock() throws Exception {
        assertIngotStock(AEItemKey.of(Items.IRON_INGOT));
    }

    @Test
    void changingBlockMarkerToIngotPublishesExtractableStock() throws Exception {
        assertIngotStock(AEItemKey.of(Items.IRON_BLOCK));
    }

    @Test
    void changingNuggetMarkerToIngotPublishesExtractableStock() throws Exception {
        assertIngotStock(AEItemKey.of(Items.IRON_NUGGET));
    }

    @Test
    void ordinaryItemsRoundTripAtLongLimitAndKeepComponents() throws Exception {
        var named = new ItemStack(Items.STICK);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Marked stick"));
        var key = AEItemKey.of(named);
        var cell = ordinaryCell(key);
        var constructor = CompressionChain.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var empty = constructor.newInstance(List.of());
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(empty);
            assertEquals(Long.MAX_VALUE, cell.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, null));
            assertEquals(0, cell.getStoredItemCount());
            assertEquals(0, cell.insert(AEItemKey.of(Items.STICK), 1, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE, cell.insert(key, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(0, cell.insert(key, 1, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE, cell.getAvailableStacks().get(key));
            assertEquals(Long.MAX_VALUE, cell.getStoredItemCount());

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
        var constructor = CompressionChain.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var empty = constructor.newInstance(List.of());
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(empty);
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
            config = ConfigInventory.configTypes(1).build();
        }
        config.setStack(0, new GenericStack(marker, 0));
        doReturn(config).when(cell).getConfigInventory();
        doAnswer(ignored -> config.toList()).when(cell).configuredStacks();
        doReturn(mock(IUpgradeInventory.class)).when(cell).getUpgradesInventory();
        doReturn(false).when(cell).hasEcoMegaUpgradeCard();
        var units = ECOMegaLongBulkStorageCell.class.getDeclaredField("storedUnits");
        units.setAccessible(true);
        units.set(cell, new LinkedHashMap<AEItemKey, Long>());
        var stack = ECOMegaLongBulkStorageCell.class.getDeclaredField("stack");
        stack.setAccessible(true);
        stack.set(cell, new ItemStack(Items.PAPER));
        return cell;
    }

    private static void initializeState(ECOMegaLongBulkStorageCell cell) throws Exception {
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

    private static void assertIngotStock(AEItemKey persistedKey) throws Exception {
        var constructor = CompressionChain.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var chain = constructor.newInstance(List.of(new ItemStack(Items.IRON_NUGGET),
            new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.IRON_BLOCK, 9)));
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var cell = mock(ECOMegaLongBulkStorageCell.class, CALLS_REAL_METHODS);
        initializeState(cell);
        ConfigInventory config;
        try (var keyTypes = mockStatic(AEKeyTypes.class)) {
            keyTypes.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items()));
            config = ConfigInventory.configTypes(1).build();
        }
        config.setStack(0, new GenericStack(ingot, 0L));
        doReturn(config).when(cell).getConfigInventory();
        doAnswer(ignored -> config.toList()).when(cell).configuredStacks();
        doReturn(false).when(cell).hasEcoMegaUpgradeCard();
        var upgrades = mock(IUpgradeInventory.class);
        when(upgrades.isInstalled(any(ItemLike.class))).thenReturn(true);
        doReturn(upgrades).when(cell).getUpgradesInventory();
        var units = new LinkedHashMap<AEItemKey, Long>();
        // Exactly 111 blocks: the old block listing reported zero ingots despite being extractable.
        units.put(persistedKey, 8_991L);
        doReturn(1L).when(cell).getTotalItemTypes();
        var field = ECOMegaLongBulkStorageCell.class.getDeclaredField("storedUnits");
        field.setAccessible(true);
        field.set(cell, units);

        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(chain);
            assertEquals(0, cell.getRemainingItemTypes());
            assertEquals(1, cell.insert(AEItemKey.of(Items.IRON_BLOCK), 1, Actionable.SIMULATE, null),
                "A variant of the occupied chain must not require another type slot");
            assertEquals(999L, cell.extract(ingot, Long.MAX_VALUE, Actionable.SIMULATE, null));
            assertEquals(111L, cell.getAvailableStacks().get(AEItemKey.of(Items.IRON_BLOCK)));
            assertCraftConversions(cell, ingot, AEItemKey.of(Items.IRON_BLOCK), 100L);
            cell.setCompressionCutoff(ingot, ingot);
            assertEquals(999L, cell.getAvailableStacks().get(ingot));
            assertEquals(999L, cell.getAvailableStacks().get(ingot), "Cached listing must retain stock");
            assertCraftUsesStock(cell, ingot, 900L, 0L);
            assertCraftUsesStock(cell, ingot, 1_008L, 9L);

            config.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 0L));
            assertEquals(0L, cell.getAvailableStacks().get(AEItemKey.of(Items.IRON_BLOCK)));
            assertEquals(999L, cell.getAvailableStacks().get(ingot), "Marker changes must not change the cutoff");
            config.setStack(0, new GenericStack(ingot, 0L));
            assertEquals(999L, cell.getAvailableStacks().get(ingot), "Reconfiguration must invalidate the cache");
            assertEquals(Map.of(persistedKey, 8_991L), cell.getStoredEntries(),
                "Changing the published form must not rewrite the persisted key or units");

            config.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 0L));
            cell.setCompressionCutoff(ingot, AEItemKey.of(Items.IRON_NUGGET));
            assertCraftConversions(cell, ingot, AEItemKey.of(Items.IRON_NUGGET), 8_100L);

            config.setStack(0, null);
            assertEquals(8_991L, cell.getAvailableStacks().get(AEItemKey.of(Items.IRON_NUGGET)),
                "Clearing a marker preserves the independent cutoff and old contents");
        }
    }

    private static void assertCraftConversions(ECOMegaLongBulkStorageCell cell, AEItemKey ingot,
            AEItemKey stockKey, long expectedUsed)
            throws Exception {
        var product = AEItemKey.of(Items.DIAMOND);
        var ore = AEItemKey.of(Items.RAW_IRON);
        var singularity = processingPattern(product, ingot, 900L);
        var smelting = processingPattern(ingot, ore, 1L);
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var unpackIngot = processingPattern(nugget, ingot, 1L);
        doReturn(List.of(new GenericStack(nugget, 9L))).when(unpackIngot).getOutputs();
        List<IPatternDetails> conversions;
        // Bypass only the encoded-definition constructor, which needs MEGA's mod registry.
        // Keep the real compression-chain selection and pattern input/output implementations.
        try (var construction = mockConstruction(DecompressionPattern.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS), (pattern, context) -> {
                    ItemStack from = (ItemStack) context.arguments().get(0);
                    ItemStack to = (ItemStack) context.arguments().get(1);
                    var definition = new ItemStack(Items.PAPER);
                    definition.set(DataComponents.CUSTOM_NAME, Component.literal(from + " -> " + to));
                    setPatternField(pattern, "from", from);
                    setPatternField(pattern, "to", to);
                    setPatternField(pattern, "definition", AEItemKey.of(definition));
                })) {
            conversions = cell.getDecompressionPatterns();
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any(AEKey.class))).thenAnswer(call -> {
            AEKey key = call.getArgument(0);
            var candidates = new java.util.ArrayList<IPatternDetails>();
            if (key.equals(product)) candidates.add(singularity);
            if (key.equals(ingot)) candidates.add(smelting);
            if (key.equals(nugget)) candidates.add(unpackIngot);
            for (var conversion : conversions) {
                if (conversion.getPrimaryOutput().what().equals(key)) candidates.add(conversion);
            }
            return candidates;
        });
        for (boolean cycles : List.of(false, true)) {
            var result = new ECOCraftingPlannerService().createSession(service, product,
                cell.getAvailableStacks(), cycles, false).plan(1L, false, ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
            assertEquals(expectedUsed, result.plan().usedItems().get(stockKey));
            assertFalse(result.plan().patternTimes().containsKey(smelting));
        }
    }

    private static CompressionChain ironChain(int ratio) throws Exception {
        var constructor = CompressionChain.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of(new ItemStack(Items.IRON_NUGGET),
            new ItemStack(Items.IRON_INGOT, ratio), new ItemStack(Items.IRON_BLOCK, 9)));
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
    void atomsConserveValueAcrossMixedTransfersAndCutoffChanges() throws Exception {
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var cell = compressedCell(nugget);
            assertEquals(2, cell.insert(block, 2, Actionable.MODULATE, null));
            assertEquals(3, cell.insert(ingot, 3, Actionable.MODULATE, null));
            assertEquals(189L, cell.getStoredEntries().get(nugget));
            assertEquals(5, cell.extract(ingot, 5, Actionable.SIMULATE, null));
            assertEquals(189L, cell.getStoredEntries().get(nugget));
            assertEquals(5, cell.extract(ingot, 5, Actionable.MODULATE, null));
            assertEquals(1, cell.getAvailableStacks().get(block));
            assertEquals(7, cell.getAvailableStacks().get(ingot));
            assertEquals(0, cell.getAvailableStacks().get(nugget));
            cell.setCompressionCutoff(nugget, ingot);
            assertEquals(144, cell.getStoredItemCount());
            assertEquals(16, cell.getAvailableStacks().get(ingot));
            assertEquals(0, cell.getAvailableStacks().get(block));
            var restored = restore(savedStack(cell), nugget);
            assertEquals(ingot, restored.getCompressionCutoff(nugget));
            assertEquals(144, restored.extract(nugget, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertTrue(restored.getAvailableStacks().isEmpty());
        }
    }

    @Test
    void longBoundaryAndLargeViewsNeverTruncateOrSaturate() throws Exception {
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var cell = compressedCell(nugget);
            long blocks = Long.MAX_VALUE / 81;
            assertEquals(blocks, cell.insert(block, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE % 81, cell.insert(nugget, 81, Actionable.MODULATE, null));
            assertEquals(0, cell.insert(nugget, 1, Actionable.MODULATE, null));
            cell.setCompressionCutoff(nugget, nugget);
            assertEquals(Long.MAX_VALUE, cell.getAvailableStacks().get(nugget));
            assertEquals(blocks, cell.extract(block, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE % 81, cell.extract(nugget, 81, Actionable.MODULATE, null));
            assertTrue(cell.getAvailableStacks().isEmpty());
        }
    }

    @Test
    void reloadRecoversSavedAtomsWithoutUsingNewRatios() throws Exception {
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var cell = compressedCell(ingot);
            cell.insert(ingot, 10, Actionable.MODULATE, null);
            cell.getAvailableStacks();
            var saved = savedStack(cell);
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(ironChain(4));
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
    void removingCompressionCardKeepsRemaindersRecoverableAndRejectsOtherForms() throws Exception {
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
            var cell = compressedCell(ingot);
            cell.insert(nugget, 10, Actionable.MODULATE, null);
            doReturn(mock(IUpgradeInventory.class)).when(cell).getUpgradesInventory();
            assertEquals(1, cell.getAvailableStacks().get(ingot));
            assertEquals(1, cell.getAvailableStacks().get(nugget));
            assertEquals(0, cell.insert(block, 1, Actionable.MODULATE, null));
            assertEquals(0, cell.insert(nugget, 1, Actionable.MODULATE, null));
            assertEquals(1, cell.extract(nugget, 100, Actionable.SIMULATE, null),
                "Without a card only the published remainder is extractable as nuggets");
            assertEquals(1, cell.extract(ingot, 1, Actionable.MODULATE, null));
            assertEquals(1, cell.extract(nugget, 1, Actionable.MODULATE, null));
        }
    }

    @Test
    void legacyMigrationRetainsCutoffAndInvalidData() throws Exception {
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(ironChain(9));
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
            saved.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
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

    @Test
    void oversizedDenominationsAreRejectedRatherThanClamped() throws Exception {
        var variants = List.of(Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.SAND,
            Items.GLASS, Items.GRAVEL, Items.OBSIDIAN, Items.NETHERRACK, Items.END_STONE,
            Items.BRICKS, Items.CLAY, Items.SNOW_BLOCK);
        var stacks = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < variants.size(); i++) stacks.add(new ItemStack(variants.get(i), i == 0 ? 1 : 64));
        var constructor = CompressionChain.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var chain = constructor.newInstance(stacks);
        var huge = AEItemKey.of(Items.SNOW_BLOCK);
        var base = AEItemKey.of(Items.STONE);
        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(chain);
            var cell = compressedCell(huge);
            assertEquals(0, cell.insert(huge, 1, Actionable.MODULATE, null));
            assertEquals(Long.MAX_VALUE, cell.insert(base, Long.MAX_VALUE, Actionable.MODULATE, null));
            assertEquals(0, cell.extract(huge, 1, Actionable.MODULATE, null));
            var restored = restore(savedStack(cell), huge);
            assertEquals(Long.MAX_VALUE, restored.extract(base, Long.MAX_VALUE, Actionable.MODULATE, null));
        }
    }

    private static void setPatternField(DecompressionPattern pattern, String name, Object value) throws Exception {
        var field = DecompressionPattern.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(pattern, value);
    }

    private static IPatternDetails processingPattern(AEKey output, AEKey inputKey, long amount) {
        var details = mock(IPatternDetails.class, CALLS_REAL_METHODS);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(inputKey, amount) });
        when(input.getMultiplier()).thenReturn(1L);
        doReturn(new IPatternDetails.IInput[] { input }).when(details).getInputs();
        doReturn(List.of(new GenericStack(output, 1L))).when(details).getOutputs();
        return details;
    }

    private static void assertCraftUsesStock(ECOMegaLongBulkStorageCell cell, AEItemKey ingot,
            long required, long smeltingCount) throws Exception {
        // A stand-in final product models the singularity recipe without requiring another mod's registry.
        AEKey product = AEItemKey.of(Items.DIAMOND);
        AEKey ore = AEItemKey.of(Items.RAW_IRON);
        var singularity = pattern(0, product, ingot, required);
        var smelting = pattern(1, ingot, ore, 1L);
        var network = new CompiledNetwork(product, Map.of(product, List.of(singularity),
            ingot, List.of(smelting), ore, List.of()), Set.of(), 2, 2);
        var inventory = cell.getAvailableStacks();
        inventory.add(ore, 1_008L);
        var result = new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(product, ingot, ore)), inventory, 1L, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(Math.min(required, 999L), result.state().usedItems().get(ingot));
        assertEquals(smeltingCount, result.state().patternTimes().getOrDefault(smelting.details(), 0L));
    }

    private static CompiledPattern pattern(int id, AEKey outputKey, AEKey inputKey, long inputAmount) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(outputKey, 1L));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, outputKey, PlannerAmount.of(1L),
            List.of(new CompiledInput(null, inputKey, inputAmount, false, null)), outputs,
            true, null, false, semantics);
    }
}
