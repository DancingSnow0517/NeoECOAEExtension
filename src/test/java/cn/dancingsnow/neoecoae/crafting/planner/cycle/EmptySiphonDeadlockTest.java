package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Block = 9 ingots + a reusable mold; the mold is cut from a block. Ores are imported without limit, so without a
 * mold the reachable markings are infinite and only a structural proof can answer before the budget runs out.
 */
class EmptySiphonDeadlockTest {
    private static final long WORK = 2_000_000L;
    private int ids;
    private final AEKey mold = key("mold");
    private final List<AEKey> members = new ArrayList<>();
    private final List<CompiledPattern> patterns = new ArrayList<>();
    private final List<ComponentDependency> boundary = new ArrayList<>();
    private AEKey block, ingot;

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 20})
    void missingMoldIsProvenWithoutExhaustingTheBudget(int materials) throws Exception {
        build(materials);
        var work = new AtomicLong();
        var result = solve(Map.of(block, 1000L), Map.of(), work);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertTrue(result.diagnostics().stream()
            .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.EMPTY_SIPHON_DEADLOCK), result.summary());
        assertEquals(1L, result.seedShortfall().get(mold), result.summary());
        assertTrue(work.get() < WORK / 10, "Proof must not search the infinite marking space: " + work);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void storedMoldStillSolves(int materials) throws Exception {
        build(materials);
        var result = solve(Map.of(block, 1000L), Map.of(mold, 1L), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void deadMoldPatternsDoNotBlockAReachableTarget(int materials) throws Exception {
        build(materials);
        var result = solve(Map.of(ingot, 1000L), Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertTrue(result.diagnostics().stream()
            .noneMatch(d -> d.code() == CycleSolveDiagnostic.Code.EMPTY_SIPHON_DEADLOCK));
    }

    private void build(int materials) {
        members.add(mold);
        AEKey[] dust = new AEKey[materials];
        for (int i = 0; i < materials; i++) {
            dust[i] = key("dust" + i);
            AEKey ingotKey = key("ingot" + i), blockKey = key("block" + i), ore = key("ore" + i);
            members.addAll(List.of(dust[i], ingotKey, blockKey));
            var crush = pattern(Map.of(ore, 1L), Map.of(dust[i], 2L));
            patterns.add(crush);
            boundary.add(new ComponentDependency(0, 1, List.of(new CraftingGraphEdge(dust[i], ore, crush, null))));
            patterns.add(pattern(Map.of(dust[i], 1L), Map.of(ingotKey, 1L)));
            patterns.add(pattern(Map.of(ingotKey, 1L), Map.of(dust[i], 1L)));
            patterns.add(pattern(Map.of(ingotKey, 9L, mold, 1L), Map.of(blockKey, 1L, mold, 1L)));
            patterns.add(pattern(Map.of(blockKey, 1L), Map.of(ingotKey, 9L)));
            if (i == 0) patterns.add(pattern(Map.of(blockKey, 1L), Map.of(mold, 1L)));
            block = blockKey;
            ingot = ingotKey;
        }
        for (int i = 0; i + 1 < materials; i++) {
            AEKey alloy = key("alloy" + i);
            members.add(alloy);
            patterns.add(pattern(Map.of(dust[i], 1L, dust[i + 1], 1L), Map.of(alloy, 2L)));
            patterns.add(pattern(Map.of(alloy, 2L), Map.of(dust[i], 1L, dust[i + 1], 1L)));
        }
    }

    private CycleSolveResult solve(Map<AEKey, Long> targets, Map<AEKey, Long> stock, AtomicLong work)
            throws Exception {
        var cycle = new CycleComponent(0, members, patterns, List.of(), List.of(), boundary);
        var request = new CycleSolveRequest(cycle, targets, stock, boundary, new CycleSolveRequest.PlannerOptions());
        return new BoundedCycleSolver().solve(request,
            new ECOPlanningBudget(work::incrementAndGet, WORK, Long.MAX_VALUE, () -> 0L));
    }

    private static AEKey key(String name) { return mock(AEKey.class, name); }

    private CompiledPattern pattern(Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        int id = ids++;
        var details = mock(IPatternDetails.class, "pattern-" + id);
        var stacks = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        when(details.getOutputs()).thenReturn(stacks.toArray(GenericStack[]::new));
        var semantics = new PatternSemantics(details, null, List.of(), stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var primary = stacks.get(0);
        return new CompiledPattern(id, details, primary.what(), PlannerAmount.of(primary.amount()),
            inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList(),
            stacks, true, null, false, semantics);
    }
}
