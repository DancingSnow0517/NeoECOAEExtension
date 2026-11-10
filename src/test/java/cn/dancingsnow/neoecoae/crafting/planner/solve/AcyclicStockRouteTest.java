package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.SupplyAllocation;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcyclicStockRouteTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void aggregatesSharedStockBeforeCuttingReverseRecipe() throws Exception {
        var outcome = solve(10L, 0L, 0L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(10L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2, outcome.state().patternTimes().size(), "The reverse recipe must not run");
    }

    @Test
    void sharedStockShortfallDoesNotBecomeAFreeCraft() throws Exception {
        // Nine nuggets cover the ingot, but the final recipe also consumes one directly.
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(9L, 0L, 0L).status());
    }

    @Test
    void aCycleWithoutStockStillFallsBack() throws Exception {
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(0L, 0L, 0L).status());
    }

    @Test
    void storedFinalOutputCannotBreakTheCycle() throws Exception {
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(0L, 0L, 1L).status());
    }

    @Test
    void reopensAnInsufficientStockLeafAndRetriesFromFreshInventory() throws Exception {
        // One stored ingot is insufficient for the two needed; craft the other from nine nuggets.
        var outcome = solve(10L, 1L, 0L, 2L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(10L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2, outcome.state().patternTimes().size());
    }

    @Test
    void changedByproductCreditsInvalidateCachedFiringAmounts() throws Exception {
        var network = retryNetwork();
        for (long amount : new long[] {1L, 1L << 62}) {
            var outcome = retry(new AcyclicCraftingSolver(), network, amount, 0L, 0L, amount);
            assertEquals(amount == 1L ? PlanningStatus.SUCCESS : PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
                outcome.status());
            assertRetryAmounts(network, outcome, amount, amount, 0L);
            assertEquals(PlannerAmount.of(amount).multiply(4),
                outcome.state().demandAmountFor(AEItemKey.of(Items.IRON_INGOT)));
        }
    }

    @Test
    void multipleGoalsShareFreshStockAndCreditsAfterCandidateChanges() throws Exception {
        var network = retryNetwork();
        var outcome = retry(new AcyclicCraftingSolver(), network, 1L, 2L, 1L, 1L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertRetryAmounts(network, outcome, 1L, 2L, 1L);
    }

    @Test
    void exhaustedRetriesStillReportTheFinalExactShortage() throws Exception {
        var network = retryNetwork();
        var outcome = retry(new AcyclicCraftingSolver(), network, 1L, 0L, 0L, 0L);
        assertEquals(PlanningStatus.MISSING_ITEMS, outcome.status());
        assertEquals(Map.of(AEItemKey.of(Items.GOLD_INGOT), PlannerAmount.ONE), outcome.state().missingAmounts());
        assertEquals(1L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2L, outcome.trace().diagnostics().stream()
            .filter(d -> d.code() == PlannerDiagnostic.Code.CANDIDATE_REJECTED).count());
    }

    @Test
    void simultaneousSolvesDoNotShareRetryCachesOrInventory() throws Exception {
        var solver = new AcyclicCraftingSolver();
        var network = retryNetwork();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> retry(solver, network, 1L, 2L, 1L, 1L));
            var two = executor.submit(() -> retry(solver, network, 3L, 1L, 2L, 3L));
            assertRetryAmounts(network, one.get(20, TimeUnit.SECONDS), 1L, 2L, 1L);
            assertRetryAmounts(network, two.get(20, TimeUnit.SECONDS), 3L, 2L, 2L);
            assertRetryAmounts(network, retry(solver, network, 2L, 0L, 0L, 2L), 2L, 2L, 0L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void checkpointRetriesMatchFullExpansionIncludingByproductsSharedGoalsAndExactAmounts() throws Exception {
        var network = prependChain(retryNetwork(), 96);
        var branch = AEItemKey.of(Items.EMERALD);
        var shared = AEItemKey.of(Items.IRON_INGOT);
        for (long amount : new long[] {1L, 3L, 1L << 62}) {
            boolean multipleGoals = amount == 3L;
            Map<AEKey, PlannerAmount> goals = new LinkedHashMap<>();
            goals.put(network.goal(), PlannerAmount.of(amount));
            if (multipleGoals) goals.put(shared, PlannerAmount.of(amount).multiply(2));
            var stock = multipleGoals ? retryStock(amount)
                : retryStock(amount).withoutReserved(Map.of(shared, PlannerAmount.of(amount)));
            var metrics = new AcyclicSolveMetrics();
            var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(),
                ECOCancellation.NONE, metrics);
            long fullVisits = 0, fullExpansions = 0, fullInputs = 0;
            for (int choice = 0; choice < 3; choice++) {
                Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>(network.producers());
                producers.put(branch, List.of(network.fastProducersOf(branch).get(choice)));
                var fixed = new CompiledNetwork(network.goal(), producers, network.emittable(),
                    network.reachablePatternCount(), network.edgeCount());
                var fullMetrics = new AcyclicSolveMetrics();
                var full = measured(new AcyclicCraftingSolver(), fixed, stock, goals, Map.of(),
                    ECOCancellation.NONE, fullMetrics);
                // The unchanged four-ingot demand needs 3/2/1 firings; shared goals and stock change it to 4/3/2.
                assertEquals(PlannerAmount.of(amount).multiply((multipleGoals ? 4 : 3) - choice),
                    full.state().plannerPatternTimes().get(network.fastProducersOf(shared).get(0).details()));
                fullVisits += fullMetrics.materialVisits;
                fullExpansions += fullMetrics.patternExpansions;
                fullInputs += fullMetrics.inputExpansions;
                if (choice == 2) assertSameResult(full, actual);
            }
            assertEquals(3, metrics.attempts);
            assertEquals(2, metrics.runOnceCalls);
            assertEquals(1, metrics.incrementalRestores);
            assertEquals(1, metrics.checkpointCopies);
            assertTrue(metrics.retainedPatternExpansions >= 97);
            assertEquals(fullVisits, metrics.materialVisits + metrics.retainedMaterialVisits);
            assertEquals(fullExpansions, metrics.patternExpansions + metrics.retainedPatternExpansions);
            assertEquals(fullInputs, metrics.inputExpansions + metrics.retainedInputExpansions);
            if (amount == 1) {
                assertNull(ECOPlanMaterialValidator.firstDeficit(actual.state(), network.goal(), amount, null, network));
                var schedule = cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.from(
                    List.of(), List.of(), actual.state().patternTimes(), actual.state().executionProvenance());
                assertEquals(actual.state().patternTimes().size(), schedule.phases().size());
                var used = new LinkedHashMap<AEKey, Long>();
                actual.state().usedAmounts().forEach((key, count) -> used.put(key, count.longValueExact()));
                var signature = new cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.Signature(
                    network.goal(), amount, cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity
                        .taskSignature(actual.state().patternTimes()), used, Map.of(), Map.of());
                var executable = cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlanBuilder.build(signature,
                    cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode.PHASED_DAG, List.of(), List.of(),
                    actual.state().patternTimes(), actual.state().executionProvenance());
                assertEquals(actual.state().patternTimes().size(), executable.tasks().size());
            }
        }
    }

    @Test
    void changedPotentialByproductsInvalidateThePrefixBeforeStockAndSupplierOrderAreReused() throws Exception {
        for (boolean removeIron : new boolean[] {false, true}) {
            var network = prependChain(retryNetwork(), 96);
            var branch = AEItemKey.of(Items.EMERALD);
            var outputs = new ArrayList<GenericStack>();
            outputs.add(new GenericStack(branch, 1));
            if (!removeIron) {
                outputs.add(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3));
                // This new source delays a copper demand which the saved prefix had already served from stock.
                outputs.add(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 2));
            }
            var replacement = pattern(3, branch, outputs, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1));
            var choices = new ArrayList<>(network.producersOf(branch));
            choices.set(2, replacement);
            var producers = new LinkedHashMap<>(network.producers());
            producers.put(branch, choices);
            network = new CompiledNetwork(network.goal(), producers, Set.of(), network.reachablePatternCount(), network.edgeCount());
            var metrics = new AcyclicSolveMetrics();
            var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.ONE);
            var actual = measured(new AcyclicCraftingSolver(), network, retryStock(1), goals, Map.of(),
                ECOCancellation.NONE, metrics);
            var full = measured(new AcyclicCraftingSolver(), network, retryStock(1), goals, Map.of(branch, 2),
                ECOCancellation.NONE, new AcyclicSolveMetrics());
            assertSameResult(full, actual);
            assertEquals(3, metrics.runOnceCalls);
            assertEquals(1, metrics.checkpointCopies);
            assertEquals(1, metrics.incrementalFallbacks);
            assertEquals(0, metrics.incrementalRestores);
            // Stored stock still has priority over credit. Equal used totals do not make its allocation order reusable.
            assertEquals(PlannerAmount.of(6),
                actual.state().usedAmounts().get(AEItemKey.of(Items.COPPER_INGOT)));
        }
    }

    @Test
    void equalByproductQuantitiesStillReplacePhysicalSuppliersAndKeepEmittedAndReservedStock() throws Exception {
        var base = prependChain(retryNetwork(), 96);
        var branch = AEItemKey.of(Items.EMERALD);
        var shared = AEItemKey.of(Items.IRON_INGOT);
        var copper = AEItemKey.of(Items.COPPER_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var choices = new ArrayList<CompiledPattern>();
        for (int i = 0; i < 3; i++) choices.add(pattern(i + 1, branch,
            List.of(new GenericStack(branch, 1), new GenericStack(shared, 2)),
            new GenericStack(i == 2 ? gold : AEItemKey.of(Items.COBBLESTONE), 1)));
        var producers = new LinkedHashMap<>(base.producers());
        producers.put(branch, choices);
        var network = new CompiledNetwork(base.goal(), producers, Set.of(copper), base.reachablePatternCount(), base.edgeCount());
        var stock = PlannerInventorySnapshot.exact(Map.of(shared, PlannerAmount.of(6),
            AEItemKey.of(Items.IRON_NUGGET), PlannerAmount.of(40)), Set.of(gold)).withoutReserved(Map.of(shared, PlannerAmount.ONE));
        var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.of(5));
        var metrics = new AcyclicSolveMetrics();
        var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(), ECOCancellation.NONE, metrics);
        var full = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(branch, 2),
            ECOCancellation.NONE, new AcyclicSolveMetrics());
        assertSameResult(full, actual);
        assertEquals(1, metrics.incrementalRestores);
        assertEquals(PlannerAmount.of(30), actual.state().emittedAmounts().get(copper));
        assertEquals(PlannerAmount.of(5), stock.amountOf(shared));
        var suppliers = actual.state().executionProvenance().supplierAmountsOf(shared);
        assertEquals(PlannerAmount.of(10), suppliers.get(
            new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource.PatternOutput(choices.get(2).details(), false)));
        assertFalse(suppliers.containsKey(
            new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource.PatternOutput(choices.get(1).details(), false)));
    }

    @Test
    void changingAnAlreadyProcessedAncestorDropsTheCheckpointAndAllItsUuidRelationships() throws Exception {
        var base = prependChain(retryNetwork(), 96);
        var producers = new LinkedHashMap<>(base.producers());
        var tool = AEItemKey.of(Items.STICK);
        var direct = withSpecialInput(pattern(500, base.goal(), 1,
            new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1), new GenericStack(tool, 1)), 1, false);
        producers.put(base.goal(), List.of(base.fastProducersOf(base.goal()).get(0), direct));
        var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount() + 1, base.edgeCount() + 1);
        var amounts = new LinkedHashMap<>(retryStock(1).amounts());
        amounts.remove(AEItemKey.of(Items.GOLD_INGOT));
        amounts.put(tool, PlannerAmount.ONE);
        var stock = PlannerInventorySnapshot.exact(amounts, Set.of());
        var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.ONE);
        var metrics = new AcyclicSolveMetrics();
        var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(), ECOCancellation.NONE, metrics);
        var full = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(network.goal(), 1),
            ECOCancellation.NONE, new AcyclicSolveMetrics());
        assertSameResult(full, actual);
        assertEquals(Map.of(direct.details(), PlannerAmount.ONE), actual.state().plannerPatternTimes());
        assertEquals(PlannerAmount.ONE, actual.state().usedAmounts().get(tool));
        assertEquals(4, metrics.attempts);
        assertEquals(3, metrics.runOnceCalls);
        assertEquals(1, metrics.incrementalRestores);
        assertEquals(1, metrics.incrementalFallbacks);
    }

    @Test
    void toolsIntroducedByTheNextCandidateUseFreshWorkingStockReservations() throws Exception {
        var base = prependChain(retryNetwork(), 96);
        var branch = AEItemKey.of(Items.EMERALD);
        var tool = AEItemKey.of(Items.STICK);
        var replacement = withSpecialInput(pattern(3, branch, base.fastProducersOf(branch).get(2).outputs(),
            new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1), new GenericStack(tool, 1)), 1, false);
        var choices = new ArrayList<>(base.producersOf(branch)); choices.set(2, replacement);
        var producers = new LinkedHashMap<>(base.producers()); producers.put(branch, choices);
        var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount(), base.edgeCount());
        var amounts = new LinkedHashMap<>(retryStock(3).amounts()); amounts.put(tool, PlannerAmount.ONE);
        var stock = PlannerInventorySnapshot.exact(amounts, Set.of());
        var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.of(3));
        var metrics = new AcyclicSolveMetrics();
        var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(), ECOCancellation.NONE, metrics);
        var full = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(branch, 2),
            ECOCancellation.NONE, new AcyclicSolveMetrics());
        assertSameResult(full, actual);
        assertEquals(PlannerAmount.ONE, actual.state().usedAmounts().get(tool));
        assertEquals(2, metrics.runOnceCalls);
        assertEquals(0, metrics.incrementalFallbacks);
        assertEquals(1, metrics.incrementalRestores);
    }

    @Test
    void deferredCycleStopsTheCheckpointWithoutDiscardingItsOrdinaryPrefix() throws Exception {
        var base = prependChain(retryNetwork(), 96);
        var branch = AEItemKey.of(Items.EMERALD);
        var boundary = AEItemKey.of(Items.REDSTONE);
        var block = AEItemKey.of(Items.REDSTONE_BLOCK);
        var unpack = pattern(500, boundary, 9, new GenericStack(block, 1));
        var pack = pattern(501, block, 1, new GenericStack(boundary, 9));
        var producers = new LinkedHashMap<>(base.producers());
        var parent = base.producers().values().stream().flatMap(List::stream)
            .filter(pattern -> pattern.id() == 180).findFirst().orElseThrow();
        var inputs = new ArrayList<GenericStack>();
        inputs.add(new GenericStack(boundary, 1));
        parent.inputs().forEach(input -> inputs.add(new GenericStack(input.key(), input.amountPerPattern().longValueExact())));
        producers.put(parent.producedKey(), List.of(pattern(parent.id(), parent.producedKey(), parent.outputs(),
            inputs.toArray(GenericStack[]::new))));
        producers.put(boundary, List.of(unpack));
        producers.put(block, List.of(pack));
        var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount() + 2, base.edgeCount() + 3);
        Set<IPatternDetails> deferred = Set.of(unpack.details(), pack.details());
        for (long amount : new long[] {3, 1L << 62}) {
            var amounts = new LinkedHashMap<>(retryStock(amount).amounts());
            amounts.put(boundary, PlannerAmount.ONE);
            var stock = PlannerInventorySnapshot.exact(amounts, Set.of());
            var goals = new LinkedHashMap<AEKey, PlannerAmount>();
            goals.put(network.goal(), PlannerAmount.of(amount));
            goals.put(AEItemKey.of(Items.IRON_INGOT), PlannerAmount.of(amount).multiply(2));
            var metrics = new AcyclicSolveMetrics();
            var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(), deferred,
                ECOCancellation.NONE, metrics);
            var full = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(branch, 2), deferred,
                ECOCancellation.NONE, new AcyclicSolveMetrics());
            assertSameResult(full, actual);
            assertEquals(Map.of(boundary, unpack.details()), actual.state().deferredSuppliers);
            assertFalse(actual.state().plannerPatternTimes().containsKey(unpack.details()));
            assertFalse(actual.state().plannerPatternTimes().containsKey(pack.details()));
            assertEquals(PlannerAmount.of(amount).subtract(PlannerAmount.ONE), actual.state().executionProvenance()
                .supplierAmountsOf(boundary).get(new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource
                    .PatternOutput(unpack.details(), true)));
            assertEquals(2, metrics.runOnceCalls);
            assertEquals(1, metrics.incrementalRestores);
            assertEquals(1, metrics.checkpointCopies);
            assertEquals("DEFERRED", metrics.checkpointBoundary);
            assertTrue(metrics.retainedPatternExpansions >= 81 && metrics.retainedPatternExpansions < 97);
        }
    }

    @Test
    void specialReservationsSurviveRetriesAndChangedComponentOrderingFallsBack() throws Exception {
        for (boolean components : new boolean[] {false, true}) {
            var base = prependChain(retryNetwork(), 96);
            var branch = AEItemKey.of(Items.EMERALD);
            var tool = AEItemKey.of(Items.STICK);
            var producers = new LinkedHashMap<>(base.producers());
            var parent = base.producers().values().stream().flatMap(List::stream)
                .filter(pattern -> pattern.id() == 100).findFirst().orElseThrow();
            var inputs = new ArrayList<GenericStack>();
            parent.inputs().forEach(input -> inputs.add(new GenericStack(input.key(), input.amountPerPattern().longValueExact())));
            inputs.add(new GenericStack(tool, 1));
            var special = withSpecialInput(pattern(parent.id(), parent.producedKey(), parent.outputs(),
                inputs.toArray(GenericStack[]::new)), inputs.size() - 1, components);
            producers.put(parent.producedKey(), List.of(special));
            var choices = new ArrayList<>(base.producersOf(branch));
            choices.set(2, withSpecialInput(pattern(3, branch, choices.get(2).outputs(),
                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1), new GenericStack(tool, 1)), 1, components));
            producers.put(branch, choices);
            producers.put(tool, List.of());
            var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount(), base.edgeCount() + 2);
            var amounts = new LinkedHashMap<>(retryStock(3).amounts());
            var variant = tool.toStack(1);
            if (components) variant.getOrCreateTag().putInt("variant", 1);
            var storedTool = AEItemKey.of(variant);
            var toolCount = PlannerAmount.of(components ? 6 : 1);
            amounts.put(storedTool, toolCount);
            var stock = PlannerInventorySnapshot.exact(amounts, Set.of());
            var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.of(3));
            var metrics = new AcyclicSolveMetrics();
            var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(), ECOCancellation.NONE, metrics);
            var full = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(branch, 2),
                ECOCancellation.NONE, new AcyclicSolveMetrics());
            assertSameResult(full, actual);
            assertEquals(toolCount, actual.state().usedAmounts().get(storedTool));
            // The new component consumer moves the shared input behind the branch in the new queue.
            // Keep the order-proof fallback; reusable tools stay outside the queue and share reservations.
            assertEquals(components ? 3 : 2, metrics.runOnceCalls, () -> "components=" + components
                + " copies=" + metrics.checkpointCopies + " fallback=" + metrics.incrementalFallbacks
                + " boundary=" + metrics.checkpointBoundary + " decision=" + metrics.checkpointDecision
                + " prefix=" + metrics.checkpointPrefixExpansions);
            assertEquals(components ? 0 : 1, metrics.incrementalRestores);
            assertEquals(components ? 1 : 0, metrics.incrementalFallbacks);
            assertEquals(1, metrics.checkpointCopies);
            assertEquals("CANDIDATE", metrics.checkpointBoundary);
            assertEquals(components ? 0 : 97, metrics.retainedPatternExpansions);
        }
    }

    @Test
    void compiledSubstitutionInputsKeepTheirConcreteMaterialChoiceAcrossRetries() throws Exception {
        var base = prependChain(retryNetwork(), 96);
        var producers = new LinkedHashMap<>(base.producers());
        var first = base.producers().values().stream().flatMap(List::stream)
            .filter(pattern -> pattern.id() == 100).findFirst().orElseThrow();
        var input = first.inputs().get(0);
        when(input.source().getPossibleInputs()).thenReturn(new GenericStack[] {
            new GenericStack(input.key(), input.amountPerPattern().longValueExact()),
            new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1) });
        var semantics = new cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter()
            .analyze(first.details());
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, semantics.matchingMode());
        assertFalse(semantics.completeForStaticPlanning());
        producers.put(first.producedKey(), List.of(new CompiledPattern(first.id(), first.details(), first.producedKey(),
            first.outputPerPattern(), first.inputs(), first.outputs(), true, "", false, semantics)));
        var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount(), base.edgeCount());
        for (long amount : new long[] {3, 1L << 62}) {
            var stock = new LinkedHashMap<>(retryStock(amount).amounts());
            var unusedSubstitute = AEItemKey.of(Items.OAK_PLANKS);
            stock.put(unusedSubstitute, PlannerAmount.of(amount));
            var inventory = PlannerInventorySnapshot.exact(stock, Set.of());
            var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.of(amount));
            var metrics = new AcyclicSolveMetrics();
            var actual = measured(new AcyclicCraftingSolver(), network, inventory, goals, Map.of(), ECOCancellation.NONE, metrics);
            var full = measured(new AcyclicCraftingSolver(), network, inventory, goals,
                Map.of(AEItemKey.of(Items.EMERALD), 2), ECOCancellation.NONE, new AcyclicSolveMetrics());
            assertSameResult(full, actual);
            assertFalse(actual.state().usedAmounts().containsKey(unusedSubstitute));
            assertEquals(2, metrics.runOnceCalls);
            assertEquals(1, metrics.incrementalRestores);
            assertEquals(97, metrics.retainedPatternExpansions);
        }
    }

    @Test
    void recursiveSpecialProducersAreReusedOnlyWhileTheirCandidateChoicesStayTheSame() throws Exception {
        for (boolean changeDependency : new boolean[] {false, true}) {
            var base = prependChain(retryNetwork(), 96);
            var producers = new LinkedHashMap<>(base.producers());
            var tool = AEItemKey.of(Items.STICK);
            var raw = AEItemKey.of(Items.BIRCH_PLANKS);
            var alternativeRaw = AEItemKey.of(Items.OAK_PLANKS);
            var branch = AEItemKey.of(Items.EMERALD);
            var parent = base.producers().values().stream().flatMap(List::stream)
                .filter(pattern -> pattern.id() == 100).findFirst().orElseThrow();
            var inputs = new ArrayList<GenericStack>();
            parent.inputs().forEach(input -> inputs.add(new GenericStack(input.key(), input.amountPerPattern().longValueExact())));
            inputs.add(new GenericStack(tool, 1));
            producers.put(parent.producedKey(), List.of(withSpecialInput(pattern(parent.id(), parent.producedKey(),
                parent.outputs(), inputs.toArray(GenericStack[]::new)), inputs.size() - 1, false)));
            var firstTool = pattern(800, tool, 1, new GenericStack(raw, 1));
            var secondTool = pattern(801, tool, 1, new GenericStack(alternativeRaw, 1));
            producers.put(tool, List.of(firstTool, secondTool));
            producers.put(raw, List.of());
            producers.put(alternativeRaw, List.of());
            if (changeDependency) {
                producers.put(branch, List.of(base.fastProducersOf(branch).get(0),
                    pattern(2, branch, base.fastProducersOf(branch).get(1).outputs(), new GenericStack(raw, 1))));
            }
            var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount() + 2, base.edgeCount() + 3);
            var stock = new LinkedHashMap<>(retryStock(1).amounts());
            stock.put(raw, PlannerAmount.ONE);
            stock.put(alternativeRaw, PlannerAmount.ONE);
            var inventory = PlannerInventorySnapshot.exact(stock, Set.of());
            var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.ONE);
            var metrics = new AcyclicSolveMetrics();
            var actual = measured(new AcyclicCraftingSolver(), network, inventory, goals, Map.of(), ECOCancellation.NONE, metrics);
            var full = measured(new AcyclicCraftingSolver(), network, inventory, goals,
                changeDependency ? Map.of(branch, 1, tool, 1) : Map.of(branch, 2), ECOCancellation.NONE, new AcyclicSolveMetrics());
            assertSameResult(full, actual);
            assertEquals(PlanningStatus.SUCCESS, actual.status());
            assertEquals(1, metrics.checkpointCopies, () -> "changed=" + changeDependency
                + " attempts=" + metrics.attempts + " boundary=" + metrics.checkpointBoundary
                + " decision=" + metrics.checkpointDecision + " prefix=" + metrics.checkpointPrefixExpansions);
            assertEquals(changeDependency ? 3 : 2, metrics.runOnceCalls);
            assertEquals(changeDependency ? 0 : 1, metrics.incrementalRestores);
            assertEquals(changeDependency ? 1 : 0, metrics.incrementalFallbacks);
            assertEquals(PlannerAmount.ONE, actual.state().plannerPatternTimes()
                .get(changeDependency ? secondTool.details() : firstTool.details()));
        }
    }

    @Test
    void aCancelledRestoreCannotLeakStateIntoConcurrentSolves() throws Exception {
        var solver = new AcyclicCraftingSolver();
        var network = prependChain(retryNetwork(), 96);
        var stock = retryStock(3);
        var metrics = new AcyclicSolveMetrics();
        assertThrows(InterruptedException.class, () -> measured(solver, network, stock,
            Map.of(network.goal(), PlannerAmount.ONE), Map.of(), () -> {
                if (metrics.incrementalRestores > 0) throw new InterruptedException("cancel restored attempt");
            }, metrics));
        assertEquals(1, metrics.incrementalRestores);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<java.util.concurrent.Future<AcyclicCraftingSolver.Outcome>>();
            for (long amount : new long[] {1, 3}) results.add(executor.submit(() -> {
                var local = new AcyclicSolveMetrics();
                var result = measured(solver, network, stock, Map.of(network.goal(), PlannerAmount.of(amount)),
                    Map.of(), ECOCancellation.NONE, local);
                assertEquals(1, local.incrementalRestores);
                return result;
            }));
            for (int i = 0; i < results.size(); i++) {
                var full = measured(solver, network, stock, Map.of(network.goal(), PlannerAmount.of(i == 0 ? 1 : 3)),
                    Map.of(AEItemKey.of(Items.EMERALD), 2), ECOCancellation.NONE, new AcyclicSolveMetrics());
                assertSameResult(full, results.get(i).get(20, TimeUnit.SECONDS));
            }
            assertEquals(PlannerAmount.of(3), stock.amountOf(AEItemKey.of(Items.GOLD_INGOT)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void componentMatchingPreservesTheActualVariantReservationAcrossRetries() throws Exception {
        var base = prependChain(retryNetwork(), 96);
        var root = base.fastProducersOf(base.goal()).get(0);
        var inputs = new ArrayList<>(root.inputs());
        var shared = inputs.get(1);
        inputs.set(1, new CompiledInput(shared.source(), shared.key(), shared.amountPerPattern(), true, null,
            null, PlannerAmount.ZERO, true));
        var producers = new LinkedHashMap<>(base.producers());
        producers.put(base.goal(), List.of(new CompiledPattern(root.id(), root.details(), root.producedKey(),
            root.outputPerPattern(), inputs, root.outputs(), true, null, false, root.semantics())));
        var network = new CompiledNetwork(base.goal(), producers, Set.of(), base.reachablePatternCount(), base.edgeCount());
        var variant = new ItemStack(Items.IRON_INGOT); variant.getOrCreateTag().putInt("variant", 1);
        var variantKey = AEItemKey.of(variant);
        when(shared.source().isValid(any(), isNull())).thenReturn(true);
        var stock = new LinkedHashMap<>(retryStock(1).amounts()); stock.remove(shared.key());
        stock.put(variantKey, PlannerAmount.ONE);
        var inventory = PlannerInventorySnapshot.exact(stock, Set.of());
        var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.ONE);
        var metrics = new AcyclicSolveMetrics();
        var actual = measured(new AcyclicCraftingSolver(), network, inventory, goals, Map.of(), ECOCancellation.NONE, metrics);
        var full = measured(new AcyclicCraftingSolver(), network, inventory, goals,
            Map.of(AEItemKey.of(Items.EMERALD), 2), ECOCancellation.NONE, new AcyclicSolveMetrics());
        assertSameResult(full, actual);
        assertEquals(PlannerAmount.ONE, actual.state().usedAmounts().get(variantKey));
        assertEquals(2, metrics.runOnceCalls);
        assertEquals(1, metrics.checkpointCopies);
        assertEquals(1, metrics.incrementalRestores);
        assertEquals("CANDIDATE", metrics.checkpointBoundary);
        assertEquals("COPIED", metrics.checkpointDecision);
    }

    @Test
    void largeStockWithLittleReusableWorkAvoidsCopyingACheckpoint() throws Exception {
        var network = prependChain(retryNetwork(), 96);
        var amounts = new LinkedHashMap<>(retryStock(1).amounts());
        for (int i = 0; i < 2048; i++) {
            var stack = new ItemStack(Items.STONE); stack.getOrCreateTag().putInt("stock", i);
            amounts.put(AEItemKey.of(stack), PlannerAmount.ONE);
        }
        var stock = PlannerInventorySnapshot.exact(amounts, Set.of());
        var goals = Map.<AEKey, PlannerAmount>of(network.goal(), PlannerAmount.ONE);
        var metrics = new AcyclicSolveMetrics();
        var actual = measured(new AcyclicCraftingSolver(), network, stock, goals, Map.of(), ECOCancellation.NONE, metrics);
        var full = measured(new AcyclicCraftingSolver(), network, stock, goals,
            Map.of(AEItemKey.of(Items.EMERALD), 2), ECOCancellation.NONE, new AcyclicSolveMetrics());
        assertSameResult(full, actual);
        assertEquals(3, metrics.runOnceCalls);
        assertEquals(0, metrics.checkpointCopies);
        assertEquals(0, metrics.incrementalRestores);
        assertEquals("STOCK_COST", metrics.checkpointDecision);
    }

    private static AcyclicCraftingSolver.Outcome measured(AcyclicCraftingSolver solver, CompiledNetwork network,
            PlannerInventorySnapshot inventory, Map<AEKey, PlannerAmount> goals, Map<AEKey, Integer> choices,
            ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws Exception {
        return measured(solver, network, inventory, goals, choices, Set.of(), cancellation, metrics);
    }

    private static AcyclicCraftingSolver.Outcome measured(AcyclicCraftingSolver solver, CompiledNetwork network,
            PlannerInventorySnapshot inventory, Map<AEKey, PlannerAmount> goals, Map<AEKey, Integer> choices,
            Set<IPatternDetails> deferred, ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws Exception {
        var method = AcyclicCraftingSolver.class.getDeclaredMethod("solveGoals", CompiledNetwork.class,
            PlannerInventorySnapshot.class, Map.class, boolean.class, Map.class, Set.class, boolean.class,
            ECOCancellation.class, AcyclicSolveMetrics.class);
        method.setAccessible(true);
        try {
            return (AcyclicCraftingSolver.Outcome) method.invoke(solver, network, inventory, goals, false, choices,
                deferred, false, cancellation, metrics);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    private static PlannerInventorySnapshot retryStock(long amount) {
        return PlannerInventorySnapshot.exact(Map.of(
            AEItemKey.of(Items.IRON_INGOT), PlannerAmount.of(amount),
            AEItemKey.of(Items.GOLD_INGOT), PlannerAmount.of(amount),
            AEItemKey.of(Items.IRON_NUGGET), PlannerAmount.of(amount).multiply(8),
            AEItemKey.of(Items.COPPER_INGOT), PlannerAmount.of(amount).multiply(8)), Set.of());
    }

    private static CompiledNetwork prependChain(CompiledNetwork original, int length) {
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < length; i++) {
            var stack = new ItemStack(Items.PAPER);
            stack.getOrCreateTag().putInt("prefix", i);
            keys.add(AEItemKey.of(stack));
        }
        var branch = AEItemKey.of(Items.EMERALD);
        var copper = AEItemKey.of(Items.COPPER_INGOT);
        var producers = new LinkedHashMap<>(original.producers());
        producers.put(original.goal(), List.of(pattern(0, original.goal(), 1,
            new GenericStack(keys.get(0), 1), new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4))));
        for (int i = 0; i < length; i++) {
            var inputs = new ArrayList<GenericStack>();
            inputs.add(new GenericStack(i + 1 == length ? branch : keys.get(i + 1), 1));
            if (i % 16 == 0) inputs.add(new GenericStack(copper, 1));
            producers.put(keys.get(i), List.of(pattern(100 + i, keys.get(i), 1, inputs.toArray(GenericStack[]::new))));
        }
        producers.put(copper, List.of());
        return new CompiledNetwork(original.goal(), producers, original.emittable(),
            original.reachablePatternCount() + length, original.edgeCount() + length + (length + 15) / 16);
    }

    private static void assertSameResult(AcyclicCraftingSolver.Outcome full, AcyclicCraftingSolver.Outcome actual) {
        assertEquals(full.status(), actual.status());
        var expected = full.state();
        var result = actual.state();
        assertEquals(expected.usedAmounts(), result.usedAmounts());
        assertEquals(expected.missingAmounts(), result.missingAmounts());
        assertEquals(expected.emittedAmounts(), result.emittedAmounts());
        assertEquals(expected.plannerPatternTimes(), result.plannerPatternTimes());
        assertEquals(expected.plannerDemands(), result.plannerDemands());
        assertEquals(expected.plannerBytes(), result.plannerBytes());
        assertEquals(expected.stored.asMap(), result.stored.asMap());
        assertEquals(expected.stored.unboundedKeys(), result.stored.unboundedKeys());
        assertEquals(expected.selected, result.selected);
        assertEquals(expected.parents, result.parents);
        assertEquals(expected.demandProducers, result.demandProducers);
        assertEquals(expected.deferredSuppliers, result.deferredSuppliers);
        assertEquals(expected.unsupported, result.unsupported);
        assertEquals(normalized(expected.executionProvenance()), normalized(result.executionProvenance()));
        if (result.missingAmounts().isEmpty() && result.unsupported.isEmpty()) result.executionProvenance().requireComplete();
    }

    private static ExecutionProvenance normalized(ExecutionProvenance provenance) {
        var ids = new LinkedHashMap<UUID, UUID>();
        var demands = new LinkedHashMap<UUID, MaterialDemand>();
        provenance.demands().values().forEach(demand -> {
            UUID id = new UUID(0, ids.size());
            ids.put(demand.id(), id);
            demands.put(id, new MaterialDemand(id, demand.kind(), demand.consumer(), demand.inputSlot(),
                demand.componentId(), demand.key(), demand.amount()));
        });
        var allocations = provenance.allocations().stream().map(allocation -> new SupplyAllocation(
            ids.get(allocation.demandId()), allocation.material(), allocation.source(), allocation.amount())).toList();
        return new ExecutionProvenance(provenance.suppliers(), demands, allocations);
    }

    private static CompiledNetwork retryNetwork() {
        var goal = AEItemKey.of(Items.DIAMOND);
        var branch = AEItemKey.of(Items.EMERALD);
        var shared = AEItemKey.of(Items.IRON_INGOT);
        var root = pattern(0, goal, 1L, new GenericStack(branch, 1L), new GenericStack(shared, 4L));
        var choices = new java.util.ArrayList<CompiledPattern>();
        for (int i = 1; i <= 3; i++) {
            AEKey input = AEItemKey.of(i < 3 ? Items.COBBLESTONE : Items.GOLD_INGOT);
            choices.add(pattern(i, branch, List.of(new GenericStack(branch, 1L), new GenericStack(shared, i)),
                new GenericStack(input, 1L)));
        }
        var sharedPattern = pattern(4, shared, 1L, new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 1L));
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(root));
        producers.put(branch, choices);
        producers.put(shared, List.of(sharedPattern));
        producers.put(AEItemKey.of(Items.COBBLESTONE), List.of());
        producers.put(AEItemKey.of(Items.GOLD_INGOT), List.of());
        producers.put(AEItemKey.of(Items.IRON_NUGGET), List.of());
        return new CompiledNetwork(goal, producers, Set.of(), 5, 6);
    }

    private static AcyclicCraftingSolver.Outcome retry(AcyclicCraftingSolver solver, CompiledNetwork network,
            long amount, long extraShared, long sharedStock, long goodStock) throws Exception {
        Map<AEKey, PlannerAmount> goals = new LinkedHashMap<>();
        goals.put(network.goal(), PlannerAmount.of(amount));
        if (extraShared > 0L) goals.put(AEItemKey.of(Items.IRON_INGOT), PlannerAmount.of(extraShared));
        var inventory = PlannerInventorySnapshot.exact(Map.of(
            AEItemKey.of(Items.IRON_INGOT), PlannerAmount.of(sharedStock),
            AEItemKey.of(Items.GOLD_INGOT), PlannerAmount.of(goodStock),
            AEItemKey.of(Items.IRON_NUGGET), PlannerAmount.of(amount).multiply(4).add(extraShared)), Set.of());
        return solver.solveExactDemands(network, inventory, goals, Map.of(), Set.of(), false, ECOCancellation.NONE);
    }

    private static void assertRetryAmounts(CompiledNetwork network, AcyclicCraftingSolver.Outcome outcome,
            long goalAmount, long rawAmount, long sharedStock) {
        assertTrue(outcome.state().missingAmounts().isEmpty());
        assertTrue(outcome.state().unsupported.isEmpty());
        assertEquals(PlannerAmount.of(rawAmount), outcome.state().usedAmounts().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(PlannerAmount.of(goalAmount), outcome.state().usedAmounts().get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(sharedStock, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        var branch = network.fastProducersOf(AEItemKey.of(Items.EMERALD)).get(2);
        assertEquals(Map.of(network.fastProducersOf(network.goal()).get(0).details(), PlannerAmount.of(goalAmount),
            branch.details(), PlannerAmount.of(goalAmount),
            network.fastProducersOf(AEItemKey.of(Items.IRON_INGOT)).get(0).details(), PlannerAmount.of(rawAmount)),
            outcome.state().plannerPatternTimes());
        assertEquals(2L, outcome.trace().diagnostics().stream()
            .filter(d -> d.code() == PlannerDiagnostic.Code.CANDIDATE_REJECTED).count());
    }

    private static AcyclicCraftingSolver.Outcome solve(long nuggets, long ingots, long products) throws Exception {
        return solve(nuggets, ingots, products, 1L);
    }

    private static AcyclicCraftingSolver.Outcome solve(long nuggets, long ingots, long products,
            long requiredIngots) throws Exception {
        var product = AEItemKey.of(Items.DIAMOND);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var finalRecipe = pattern(0, product, 1L,
            new GenericStack(ingot, requiredIngots), new GenericStack(nugget, 1L));
        var pack = pattern(1, ingot, 1L, new GenericStack(nugget, 9L));
        var unpack = pattern(2, nugget, 9L, new GenericStack(ingot, 1L));
        var network = new CompiledNetwork(product, Map.of(product, List.of(finalRecipe),
            ingot, List.of(pack), nugget, List.of(unpack)), Set.of(), 3, 3);
        var inventory = new KeyCounter();
        inventory.set(nugget, nuggets);
        inventory.set(ingot, ingots);
        inventory.set(product, products);
        return new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(product, ingot, nugget)), inventory, 1L, ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, AEKey output, long outputAmount, GenericStack... inputs) {
        return pattern(id, output, List.of(new GenericStack(output, outputAmount)), inputs);
    }

    private static CompiledPattern withSpecialInput(CompiledPattern pattern, int slot, boolean components) {
        var inputs = new ArrayList<>(pattern.inputs());
        var input = inputs.get(slot);
        SpecialPatternAnalysis analysis = SpecialPatternAnalysis.NONE;
        if (components) {
            when(input.source().isValid(any(), isNull())).thenReturn(true);
            inputs.set(slot, new CompiledInput(input.source(), input.key(), input.amountPerPattern(), true, null,
                null, PlannerAmount.ZERO, true));
        } else {
            when(input.source().getRemainingKey(input.key())).thenReturn(input.key());
            analysis = new SpecialPatternAnalysis(List.of(new SpecialPatternAnalysis.Requirement(input, input.key(),
                SpecialPatternAnalysis.Type.REUSABLE, 0, 0)));
        }
        return new CompiledPattern(pattern.id(), pattern.details(), pattern.producedKey(), pattern.outputPerPattern(),
            inputs, pattern.outputs(), true, null, false, pattern.semantics(), analysis);
    }

    private static CompiledPattern pattern(int id, AEKey output, List<GenericStack> outputs, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        when(details.getOutputs()).thenReturn(outputs.toArray(GenericStack[]::new));
        when(details.getPrimaryOutput()).thenReturn(outputs.get(0));
        var rawInputs = java.util.Arrays.stream(inputs).map(input -> {
            var raw = mock(IPatternDetails.IInput.class);
            when(raw.getPossibleInputs()).thenReturn(new GenericStack[] {input});
            when(raw.getMultiplier()).thenReturn(1L);
            return raw;
        }).toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var compiledInputs = java.util.stream.IntStream.range(0, inputs.length)
            .mapToObj(slot -> new CompiledInput(rawInputs[slot], inputs[slot].what(), inputs[slot].amount(), true, null)).toList();
        return new CompiledPattern(id, details, output, PlannerAmount.of(outputs.get(0).amount()), compiledInputs, outputs,
            true, null, false, semantics);
    }
}
