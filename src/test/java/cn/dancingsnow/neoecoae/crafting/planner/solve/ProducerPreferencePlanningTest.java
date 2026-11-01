package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Observable route policy shared by all supported Minecraft adapters. */
class ProducerPreferencePlanningTest {
    private static AEKey raw, alternateRaw, a, b, goal;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        raw = key("raw");
        alternateRaw = key("alternate_raw");
        a = key("a");
        b = key("b");
        goal = key("goal");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void retainsThePreferredCompleteChainEvenWhenAnotherRouteNeedsFewerFirings(boolean cycles) throws Exception {
        var finish = pattern(List.of(stack(a, 1)), stack(goal, 1));
        var prepare = pattern(List.of(stack(raw, 1)), stack(a, 1));
        var direct = pattern(List.of(stack(raw, 1)), stack(goal, 1));
        var result = plan(service(finish, prepare, direct), stock(raw, 5), 5, cycles);
        assertEquals(Map.of(finish, 5L, prepare, 5L), result.plan().patternTimes());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLargeAcyclicCatalogStillStartsWithThePreferredRoute(boolean cycles) throws Exception {
        var finish = pattern(List.of(stack(a, 1)), stack(goal, 1));
        var prepare = pattern(List.of(stack(raw, 1)), stack(a, 1));
        List<IPatternDetails> recipes = new ArrayList<>();
        recipes.add(finish);
        recipes.add(prepare);
        for (int amount = 1; amount <= 65; amount++)
            recipes.add(pattern(List.of(stack(raw, amount)), stack(goal, 1)));
        var result = plan(service(recipes.toArray(new IPatternDetails[0])), stock(raw, 100), 1, cycles);
        assertEquals(Map.of(finish, 1L, prepare, 1L), result.plan().patternTimes());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unavailablePreferredProducerCanFallBackToTheNextProducer(boolean cycles) throws Exception {
        var preferred = pattern(List.of(stack(raw, 1)), stack(goal, 1));
        var fallback = pattern(List.of(stack(alternateRaw, 1)), stack(goal, 1));
        var result = plan(service(preferred, fallback), stock(alternateRaw, 4), 4, cycles);
        assertEquals(Map.of(fallback, 4L), result.plan().patternTimes());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void recoveryCanCombineIndividuallyInsufficientSupplies(boolean cycles) throws Exception {
        var preferred = pattern(List.of(stack(raw, 1)), stack(goal, 1));
        var fallback = pattern(List.of(stack(alternateRaw, 1)), stack(goal, 1));
        var result = plan(service(preferred, fallback), stock(raw, 6, alternateRaw, 4), 10, cycles);
        assertEquals(Map.of(preferred, 6L, fallback, 4L), result.plan().patternTimes());
        assertEquals(6L, result.plan().usedItems().get(raw));
        assertEquals(4L, result.plan().usedItems().get(alternateRaw));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sharedStockRecoveryCanRevisitAnEarlierChoice(boolean cycles) throws Exception {
        var finish = pattern(List.of(stack(a, 1), stack(b, 1)), stack(goal, 1));
        var preferredA = pattern(List.of(stack(raw, 1)), stack(a, 1));
        var fallbackA = pattern(List.of(stack(alternateRaw, 1)), stack(a, 1));
        var preferredB = pattern(List.of(stack(raw, 2)), stack(b, 1));
        var fallbackB = pattern(List.of(stack(alternateRaw, 1)), stack(b, 1));
        // A monotone retry first changes A, then B, exhausting the shared alternate stock.
        // The legal combination restores preferred A and keeps fallback B. The bounded
        // route-test policy isolates this recovery from optional joint algebra.
        var inventory = stock(raw, 1, alternateRaw, 1);
        var network = new CraftingNetworkCompiler().compile(
                service(finish, preferredA, fallbackA, preferredB, fallbackB), goal, cycles, ECOCancellation.NONE);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
                new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver(), 64);
        var selection = planner.selectRoutes(condensation, cycles, ECOCancellation.NONE);
        var result = planner.planWithRouteFallback(network, condensation, selection, inventory,
                PlannerInventorySnapshot.of(inventory), 1, cycles, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertEquals(Map.of(finish, 1L, preferredA, 1L, fallbackB, 1L), result.state().patternTimes());
        assertEquals(1L, result.state().usedItems().get(raw));
        assertEquals(1L, result.state().usedItems().get(alternateRaw));
        result.state().executionProvenance().requireComplete();
    }

    private static ECOPlanningResult plan(ICraftingService service, KeyCounter inventory, long amount,
            boolean cycles) throws Exception {
        var result = new ECOCraftingPlannerService().createSession(service, goal, inventory, cycles)
                .plan(amount, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertTrue(result.exactMissingItems().isEmpty());
        assertNull(result.executionPlanError());
        result.provenance().requireComplete();
        return result;
    }

    private static GenericStack stack(AEKey key, long amount) { return new GenericStack(key, amount); }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static KeyCounter stock(Object... entries) {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < entries.length; i += 2)
            result.add((AEKey) entries[i], ((Number) entries[i + 1]).longValue());
        return result;
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var pattern = mock(IPatternDetails.class);
        var slots = inputs.stream().map(stack -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(1L);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {stack});
            when(input.isValid(any(), isNull())).thenAnswer(call -> stack.what().equals(call.getArgument(0)));
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(pattern.getInputs()).thenReturn(slots);
        when(pattern.getOutputs()).thenReturn(outputs);
        when(pattern.getPrimaryOutput()).thenReturn(outputs[0]);
        return pattern;
    }

    private static ICraftingService service(IPatternDetails... patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns)
            index.computeIfAbsent(pattern.getPrimaryOutput().what(), ignored -> new ArrayList<>()).add(pattern);
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        return service;
    }
}
