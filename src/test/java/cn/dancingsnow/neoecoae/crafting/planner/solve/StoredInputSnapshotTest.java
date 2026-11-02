package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoredInputSnapshotTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fuzzyConsumptionKeepsAllRemovedVariantKeys(boolean special) throws Exception {
        var template = AEItemKey.of(Items.IRON_PICKAXE);
        var goal = AEItemKey.of(Items.DIAMOND);
        int variants = 64;
        var input = new CompiledInput(null, template, PlannerAmount.of(variants), true, null,
            null, PlannerAmount.ZERO, true);
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(goal, 1L));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var analysis = special ? new SpecialPatternAnalysis(List.of(new SpecialPatternAnalysis.Requirement(
            input, template, SpecialPatternAnalysis.Type.CATALYST, 0, 0))) : SpecialPatternAnalysis.NONE;
        var pattern = new CompiledPattern(0, details, goal, PlannerAmount.ONE, List.of(input), outputs,
            true, null, false, semantics, analysis);
        var stock = new KeyCounter();
        for (int i = 1; i <= variants; i++) {
            var stack = new ItemStack(Items.IRON_PICKAXE);
            stack.setDamageValue(i);
            stock.add(AEItemKey.of(stack), 1L);
        }
        SolveState state;
        if (special) {
            state = new SolveState(stock);
            new SpecialPatternResolver(null, state, Map.of(), ECOCancellation.NONE, false)
                .resolve(pattern, PlannerAmount.ONE);
        } else {
            var network = new CompiledNetwork(goal, Map.of(goal, List.of(pattern), template, List.of()),
                Set.of(), 1, 1);
            var result = new AcyclicCraftingSolver().solve(network,
                new AcyclicRoutePlan(List.of(goal, template)), stock, 1L, ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, result.status());
            state = result.state();
        }
        assertTrue(state.stored.isEmpty());
        assertEquals(variants, state.used.asMap().size());
        for (var entry : stock) assertEquals(1L, state.usedItems().get(entry.getKey()));
        state.executionProvenance().requireComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void exhaustingDurabilityVariantsKeepsTheirMaterialIdentity(int variants) throws Exception {
        var template = new ItemStack(Items.IRON_PICKAXE);
        var key = AEItemKey.of(template);
        var input = new CompiledInput(null, key, 1L, true, null);
        var requirement = new SpecialPatternAnalysis.Requirement(input, key,
            SpecialPatternAnalysis.Type.DURABILITY, 1, template.getMaxDamage());
        var pattern = mock(CompiledPattern.class);
        when(pattern.inputs()).thenReturn(List.of(input));
        when(pattern.details()).thenReturn(mock(IPatternDetails.class));
        when(pattern.specialAnalysis()).thenReturn(new SpecialPatternAnalysis(List.of(requirement)));
        var state = new SolveState(new KeyCounter());
        List<AEKey> storedKeys = new ArrayList<>();
        long uses = 0;
        for (int i = 1; i <= variants; i++) {
            var stack = template.copy();
            stack.setDamageValue(template.getMaxDamage() - i);
            var storedKey = AEItemKey.of(stack);
            storedKeys.add(storedKey);
            state.stored.add(storedKey, 1L);
            uses += i;
        }
        new SpecialPatternResolver(null, state, Map.of(), ECOCancellation.NONE, false)
            .resolve(pattern, PlannerAmount.of(uses));
        assertTrue(state.stored.isEmpty());
        assertEquals(variants, state.used.asMap().size());
        for (AEKey storedKey : storedKeys) assertEquals(1L, state.usedItems().get(storedKey));
        state.executionProvenance().requireComplete();
        assertEquals(variants, state.executionProvenance().allocations().size());
    }
}
