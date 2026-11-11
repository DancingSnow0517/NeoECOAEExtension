package cn.dancingsnow.neoecoae.crafting.planner.compiled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CompiledStructureCacheTest {
    private static AEKey raw, goal;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        raw = AEItemKey.of(Items.RAW_IRON);
        goal = AEItemKey.of(Items.IRON_INGOT);
    }

    @Test void separateServicesAndSessionsReuseStructureButCaptureFreshStockAndAmounts() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(service, goal, stock(5)).plan(5, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.MISSING_ITEMS, new ECOCraftingPlannerService().createSession(service, goal, stock(0))
                .plan(1, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(service, goal, stock(2)).plan(2, false, ECOCancellation.NONE).status());
        verify(service, times(1)).getCraftingFor(goal);
        verify(service, times(1)).getCraftingFor(raw);
    }

    @Test void providerRefreshInvalidatesTheCompiledContract() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(2L);
        when(service.getCraftingFor(goal)).thenReturn(List.of());
        assertEquals(PlanningStatus.MISSING_ITEMS, planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test void recipeReloadInvalidatesCrossSessionAndSessionLocalStructures() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        var session = planner.createSession(service, goal, stock(5));
        assertEquals(PlanningStatus.SUCCESS, session.plan(1, false, ECOCancellation.NONE).status());
        when(service.getCraftingFor(goal)).thenReturn(List.of());
        AE2PatternIntrospection.onRecipeReloadOrServerReload();
        assertEquals(PlanningStatus.MISSING_ITEMS, session.plan(1, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.MISSING_ITEMS,
            planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test void existingPlannerObservesRegisteredSpecialSemanticsAndRemoval() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        var session = planner.createSession(service, goal, stock(5));
        assertEquals(PlanningStatus.SUCCESS, session.plan(1, false, ECOCancellation.NONE).status());
        IPatternDetails physical = service.getCraftingFor(goal).iterator().next();
        var id = ResourceLocation.fromNamespaceAndPath("eco_cache_test", "special");
        ECOPatternSemanticsRegistry.register(id, 1000, new ECOPatternSemanticAdapter() {
            @Override public boolean supports(IPatternDetails pattern) { return pattern == physical; }
            @Override public ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics base) {
                return ECOPatternSemantics.unsupported(pattern, base.physicalDefinition(), "SPECIAL_TEST_RESTRICTION");
            }
        });
        try {
            assertNotEquals(PlanningStatus.SUCCESS, session.plan(1, false, ECOCancellation.NONE).status());
            assertNotEquals(PlanningStatus.SUCCESS,
                planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        } finally {
            ECOPatternSemanticsRegistry.unregister(id);
        }
        assertEquals(PlanningStatus.SUCCESS, session.plan(1, false, ECOCancellation.NONE).status());
    }

    @Test void pendingProviderRebuildCannotReadOrPopulateTheCache() throws Exception {
        var service = service(true);
        var revision = (ECOCraftingProviderRevision) service;
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(false);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(true);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test void cycleCapabilityAndComponentMatchingUseSeparateCacheEntries() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5), false).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5), true).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5), false, false, Set.of(BuiltInRegistries.ITEM.getKey(Items.RAW_IRON)))
                .plan(1, false, ECOCancellation.NONE);
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test void servicesWithoutStableRevisionEvidenceAlwaysCompileFresh() throws Exception {
        var service = service(false);
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test void twoNetworksWithTheSameRevisionNeverSharePhysicalPatterns() throws Exception {
        var first = service(true);
        var second = service(true);
        when(second.getCraftingFor(goal)).thenReturn(List.of());
        var planner = new ECOCraftingPlannerService();
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(first, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.MISSING_ITEMS, planner.createSession(second, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        verify(second).getCraftingFor(goal);
    }

    @Test void liteAndFullModesKeepSeparateRoutesWhenTheFirstProducerIsMissing() throws Exception {
        var alternativeInput = AEItemKey.of(Items.GOLD_INGOT);
        var first = pattern(goal, 1, new GenericStack(raw, 1));
        var second = pattern(goal, 1, new GenericStack(alternativeInput, 1));
        var service = routes(java.util.Map.of(goal, List.of(first, second)));
        var stock = new KeyCounter();
        stock.add(alternativeInput, 1);
        var planner = new ECOCraftingPlannerService();
        for (boolean lite : new boolean[] {true, false, true, false}) {
            var result = planner.createSession(service, goal, stock, false, lite).plan(1, false, ECOCancellation.NONE);
            assertEquals(lite ? PlanningStatus.MISSING_ITEMS : PlanningStatus.SUCCESS, result.status());
            assertEquals(lite, result.liteCraftingMode());
            assertTrue(result.exactPatternTimes().containsKey(lite ? first : second));
            assertFalse(result.exactPatternTimes().containsKey(lite ? second : first));
        }
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test void enabledCyclePlanningOverridesLiteAndSurvivesStructureReuse() throws Exception {
        var seed = AEItemKey.of(Items.IRON_NUGGET);
        var root = pattern(goal, 1, new GenericStack(raw, 1));
        var grow = pattern(raw, 2, new GenericStack(seed, 1));
        var recover = pattern(seed, 1, new GenericStack(raw, 1));
        var service = routes(java.util.Map.of(goal, List.of(root), raw, List.of(grow), seed, List.of(recover)));
        var stock = new KeyCounter();
        stock.add(seed, 1);
        var planner = new ECOCraftingPlannerService();
        for (boolean cycles : new boolean[] {false, true, false, true}) {
            var result = planner.createSession(service, goal, stock, cycles, true)
                .plan(10, false, ECOCancellation.NONE);
            assertEquals(cycles ? PlanningStatus.SUCCESS : PlanningStatus.MISSING_ITEMS, result.status(),
                result.trace().diagnostics().toString());
            assertEquals(!cycles, result.liteCraftingMode());
            if (cycles) {
                assertTrue(result.exactMissingItems().isEmpty());
                assertNull(result.executionPlanError());
                assertEquals(cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus.SOLVED,
                    result.trace().cycles().getFirst().status());
            }
            assertEquals(1, stock.get(seed), "Planning must preserve the captured startup stock");
        }
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test void unrelatedComponentOverridesKeepTheRequestedLitePass() throws Exception {
        var recipe = pattern(goal, 1, new GenericStack(raw, 1));
        var service = routes(java.util.Map.of(goal, List.of(recipe)));
        var unrelated = Set.of(BuiltInRegistries.ITEM.getKey(Items.GOLD_INGOT));
        var result = new ECOCraftingPlannerService().createSession(service, goal, stock(1), true, true, unrelated)
            .plan(1, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertTrue(result.liteCraftingMode());
        assertEquals(unrelated, result.fuzzyPlanningItemIds());
        assertNull(result.executionPlanError());
    }

    @Test void liteAggregatesSharedIngredientsBeforeRoundingPatternCounts() throws Exception {
        var other = AEItemKey.of(Items.GOLD_INGOT);
        var shared = AEItemKey.of(Items.IRON_NUGGET);
        var fuel = AEItemKey.of(Items.COAL);
        var root = pattern(goal, 1, new GenericStack(raw, 1), new GenericStack(other, 1));
        var left = pattern(raw, 1, new GenericStack(shared, 1));
        var right = pattern(other, 1, new GenericStack(shared, 1));
        var batch = pattern(shared, 3, new GenericStack(fuel, 1));
        var service = routes(java.util.Map.of(goal, List.of(root), raw, List.of(left),
            other, List.of(right), shared, List.of(batch)));
        var inventory = new KeyCounter();
        inventory.add(fuel, 1);
        var result = lite(service, inventory, 1);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(java.math.BigInteger.ONE, result.exactPatternTimes().get(batch).toBigInteger());
        assertEquals(java.math.BigInteger.ONE, result.exactUsedItems().get(fuel).toBigInteger());
        assertNull(result.executionPlanError());
        assertEquals(4, result.executionPlan().tasks().size());
    }

    @Test void liteDoesNotCreditASecondaryOutputToAnotherBranch() throws Exception {
        var secondary = AEItemKey.of(Items.GOLD_INGOT);
        var fuel = AEItemKey.of(Items.COAL);
        var root = pattern(goal, 1, new GenericStack(raw, 1), new GenericStack(secondary, 1));
        var source = pattern(raw, 1, new GenericStack(fuel, 1));
        when(source.getOutputs()).thenReturn(List.of(new GenericStack(raw, 1), new GenericStack(secondary, 1)));
        var service = routes(java.util.Map.of(goal, List.of(root), raw, List.of(source)));
        var inventory = new KeyCounter();
        inventory.add(fuel, 1);
        var result = lite(service, inventory, 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(java.math.BigInteger.ONE, result.exactMissingItems().get(secondary).toBigInteger());
        assertEquals(2, result.exactPatternTimes().size());
    }

    @Test void liteChargesPrimaryInputsPerCraftWithoutSubstituteOrRemainderAnalysis() throws Exception {
        var substitute = AEItemKey.of(Items.GOLD_INGOT);
        var recipe = pattern(goal, 1, new GenericStack(raw, 1));
        var input = recipe.getInputs()[0];
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(raw, 1), new GenericStack(substitute, 1)});
        when(input.getRemainingKey(raw)).thenReturn(raw);
        var inventory = new KeyCounter();
        inventory.add(raw, 31);
        inventory.add(substitute, 100);
        var service = routes(java.util.Map.of(goal, List.of(recipe)));
        new cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler().compile(
            service, goal, true, true, Set.of(), ECOCancellation.NONE);
        verify(input, never()).getRemainingKey(any());
        var result = new ECOCraftingPlannerService().createSession(service,
            goal, inventory, false, true, Set.of())
            .plan(32, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(java.math.BigInteger.ONE, result.exactMissingItems().get(raw).toBigInteger());
        assertEquals(java.math.BigInteger.valueOf(31), result.exactUsedItems().get(raw).toBigInteger());
        assertFalse(result.exactUsedItems().containsKey(substitute));
    }

    @Test void liteTraversesCyclesOnceAndReportsUnfundedInputs() throws Exception {
        var seed = AEItemKey.of(Items.IRON_NUGGET);
        var root = pattern(goal, 1, new GenericStack(raw, 1));
        var forward = pattern(raw, 1, new GenericStack(seed, 1));
        var reverse = pattern(seed, 1, new GenericStack(raw, 1));
        var service = routes(java.util.Map.of(goal, List.of(root), raw, List.of(forward), seed, List.of(reverse)));
        var inventory = new KeyCounter();
        inventory.add(seed, 1);
        var funded = lite(service, inventory, 1);
        assertEquals(PlanningStatus.SUCCESS, funded.status());
        assertEquals(1, funded.cycles().size());
        assertFalse(funded.exactPatternTimes().containsKey(reverse));
        assertNull(funded.executionPlanError());
        var missing = lite(service, new KeyCounter(), 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, missing.status());
        assertEquals(java.math.BigInteger.ONE, missing.exactMissingItems().get(raw).toBigInteger());
        assertEquals(3, missing.exactPatternTimes().size());
    }

    @Test void liteKeepsLargeIntermediateRequirementsExact() throws Exception {
        var recipe = pattern(goal, 1, new GenericStack(raw, 3));
        var result = lite(routes(java.util.Map.of(goal, List.of(recipe))), new KeyCounter(), Long.MAX_VALUE);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, result.status());
        assertEquals(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(3)),
            result.exactMissingItems().get(raw).toBigInteger());
        assertTrue(result.liteCraftingMode());
    }

    private static cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult lite(
            ICraftingService service, KeyCounter inventory, long amount) throws Exception {
        return new ECOCraftingPlannerService().createSession(service, goal, inventory, false, true)
            .plan(amount, false, ECOCancellation.NONE);
    }

    private static ICraftingService routes(java.util.Map<AEKey, List<IPatternDetails>> patterns) {
        var service = mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingProviderRevision.class));
        when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(1L);
        when(((ECOCraftingProviderRevision) service).neoecoae$isProviderSnapshotStable()).thenReturn(true);
        when(service.getCraftingFor(any(AEKey.class))).thenAnswer(call -> patterns.getOrDefault(call.getArgument(0), List.of()));
        return service;
    }

    private static IPatternDetails pattern(AEKey output, long count, GenericStack... ingredients) {
        var inputs = java.util.Arrays.stream(ingredients).map(ingredient -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(1L);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {ingredient});
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, count)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(output, count));
        return pattern;
    }

    private static KeyCounter stock(long amount) {
        var stock = new KeyCounter();
        if (amount > 0) stock.add(raw, amount);
        return stock;
    }

    private static ICraftingService service(boolean revisioned) {
        var service = revisioned ? mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingProviderRevision.class))
                : mock(ICraftingService.class);
        if (revisioned) {
            when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(1L);
            when(((ECOCraftingProviderRevision) service).neoecoae$isProviderSnapshotStable()).thenReturn(true);
        }
        var input = mock(IPatternDetails.IInput.class);
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(raw, 1)});
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(goal, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(goal, 1));
        when(service.getCraftingFor(goal)).thenReturn(List.of(pattern));
        when(service.getCraftingFor(raw)).thenReturn(List.of());
        return service;
    }
}
