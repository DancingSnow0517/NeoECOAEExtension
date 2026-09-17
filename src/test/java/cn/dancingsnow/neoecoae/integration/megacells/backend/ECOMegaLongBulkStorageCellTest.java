package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
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

    private static void assertIngotStock(AEItemKey persistedKey) throws Exception {
        var constructor = CompressionChain.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        var chain = constructor.newInstance(List.of(new ItemStack(Items.IRON_NUGGET),
            new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.IRON_BLOCK, 9)));
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var cell = mock(ECOMegaLongBulkStorageCell.class, CALLS_REAL_METHODS);
        ConfigInventory config;
        try (var keyTypes = mockStatic(AEKeyTypes.class)) {
            keyTypes.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items()));
            config = ConfigInventory.configTypes(1).build();
        }
        config.setStack(0, new GenericStack(ingot, 0L));
        doReturn(config).when(cell).getConfigInventory();
        doReturn(false).when(cell).hasEcoMegaUpgradeCard();
        var upgrades = mock(IUpgradeInventory.class);
        when(upgrades.isInstalled(any(ItemLike.class))).thenReturn(true);
        doReturn(upgrades).when(cell).getUpgradesInventory();
        var units = new LinkedHashMap<AEItemKey, Long>();
        // Exactly 111 blocks: the old block listing reported zero ingots despite being extractable.
        units.put(persistedKey, 8_991L);
        var field = ECOMegaLongBulkStorageCell.class.getDeclaredField("storedUnits");
        field.setAccessible(true);
        field.set(cell, units);

        try (var compression = mockStatic(CompressionService.class)) {
            compression.when(() -> CompressionService.getChain(any(AEItemKey.class))).thenReturn(chain);
            assertEquals(999L, cell.extract(ingot, Long.MAX_VALUE, Actionable.SIMULATE, null));
            assertEquals(999L, cell.getAvailableStacks().get(ingot));
            assertEquals(999L, cell.getAvailableStacks().get(ingot), "Cached listing must retain stock");
            assertCraftUsesStock(cell, ingot, 900L, 0L);
            assertCraftUsesStock(cell, ingot, 1_008L, 9L);

            config.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 0L));
            assertEquals(111L, cell.getAvailableStacks().get(AEItemKey.of(Items.IRON_BLOCK)));
            assertEquals(0L, cell.getAvailableStacks().get(ingot));
            config.setStack(0, new GenericStack(ingot, 0L));
            assertEquals(999L, cell.getAvailableStacks().get(ingot), "Reconfiguration must invalidate the cache");
            assertEquals(Map.of(persistedKey, 8_991L), cell.getStoredEntries(),
                "Changing the published form must not rewrite the persisted key or units");

            config.setStack(0, null);
            long expectedFallback = persistedKey.equals(ingot) ? 999L
                : persistedKey.equals(AEItemKey.of(Items.IRON_BLOCK)) ? 111L : 8_991L;
            assertEquals(expectedFallback, cell.getAvailableStacks().get(persistedKey),
                "Clearing the marker must keep old contents visible");
        }
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
