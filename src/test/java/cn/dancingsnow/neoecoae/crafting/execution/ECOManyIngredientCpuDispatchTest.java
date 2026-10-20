package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises CPU scheduling, real AE2 ingredient resolution, provider calls and ledger settlement together. */
class ECOManyIngredientCpuDispatchTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(ints = {64, 65, 81})
    void importedPlanDispatchesEveryIngredientThroughTheCpu(int ingredientCount) {
        var fixture = new Fixture(ingredientCount, false);
        assertEquals(4, fixture.dispatch());
        fixture.assertSettled();
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 65, 81})
    void ecoExecutionPlanDispatchesEveryIngredientThroughTheCpu(int ingredientCount) {
        var fixture = new Fixture(ingredientCount, true);
        assertEquals(4, fixture.dispatch());
        fixture.assertSettled();
        assertTrue(fixture.job.executionRuntime.isComplete());
    }

    @ParameterizedTest
    @ValueSource(ints = {65, 81})
    void rejectedProviderDoesNotLoseMaterialsOrPreventCpuRetry(int ingredientCount) {
        var fixture = new Fixture(ingredientCount, true);
        doReturn(false).when(fixture.provider).pushPattern(any(), any());
        assertEquals(0, fixture.dispatch());
        assertEquals(4, fixture.job.tasks.get(fixture.pattern).value);
        assertEquals(0, fixture.job.waitingFor.list.get(fixture.output));
        for (var ingredient : fixture.ingredients) {
            assertEquals(4, fixture.logic.getInventory().list.get(ingredient.what()));
        }
        verify(fixture.energy).injectPower(ingredientCount, Actionable.MODULATE);
        clearInvocations(fixture.provider);
        fixture.acceptInputs();
        assertEquals(4, fixture.dispatch());
        fixture.assertSettled();
    }

    private static final class Fixture {
        final List<GenericStack> ingredients;
        final AEItemKey output = AEItemKey.of(Items.NETHER_STAR);
        final AEProcessingPattern pattern;
        final ICraftingProvider provider = mock(ICraftingProvider.class);
        final IEnergyService energy = mock(IEnergyService.class);
        final CraftingService service = mock(CraftingService.class);
        final Level level = mock(Level.class);
        final ECOCraftingCPULogic logic;
        final ExecutingCraftingJob job;
        final KeyCounter received = new KeyCounter();

        Fixture(int ingredientCount, boolean ecoPlan) {
            ingredients = BuiltInRegistries.ITEM.stream()
                    .filter(item -> item != Items.AIR && item != Items.NETHER_STAR)
                    .limit(ingredientCount).map(item -> new GenericStack(AEItemKey.of(item), 1)).toList();
            assertEquals(ingredientCount, ingredients.size());
            var encoded = mock(EncodedProcessingPattern.class);
            when(encoded.sparseInputs()).thenReturn(ingredients);
            when(encoded.sparseOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
            var definition = mock(AEItemKey.class);
            when(definition.get(AEComponents.ENCODED_PROCESSING_PATTERN)).thenReturn(encoded);
            pattern = new AEProcessingPattern(definition);
            assertEquals(ingredientCount, pattern.getInputs().length);

            var cpu = mock(ECOCraftingCPU.class);
            when(cpu.isActive()).thenReturn(true);
            when(cpu.getLevel()).thenReturn(level);
            logic = new ECOCraftingCPULogic(cpu);
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(output, 4));
            when(plan.patternTimes()).thenReturn(Map.of(pattern, 4L));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ecoPlan ? executionPlan() : null, ignored -> {}, link, null);
            }
            logic.setJobFromPersistence(job);
            for (var ingredient : ingredients) {
                logic.getInventory().insert(ingredient.what(), 4, Actionable.MODULATE);
            }
            when(service.getProviders(pattern)).thenReturn(List.of(provider));
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
            acceptInputs();
        }

        ECOExecutionPlan executionPlan() {
            var task = new ECOExecutionPlan.TaskSpec(0, mock(PlanIdentity.PatternIdentity.class), pattern,
                    ECOExecutionPlan.PatternRuntimeInfo.from(pattern), 4, 0, ECOExecutionPlan.TaskKind.DAG);
            var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.DAG,
                    List.of(0), List.of(), List.of());
            return new ECOExecutionPlan(mock(PlanIdentity.Signature.class), ExecutionMode.PHASED_DAG,
                    List.of(task), List.of(phase), new ECOExecutionSchedule(List.of()));
        }

        void acceptInputs() {
            when(provider.pushPattern(any(), any())).thenAnswer(call -> {
                IPatternDetails attemptedPattern = call.getArgument(0);
                KeyCounter[] counters = call.getArgument(1);
                assertEquals(ingredients.size(), counters.length);
                attemptedPattern.pushInputsToExternalInventory(counters, received::add);
                return true;
            });
        }

        int dispatch() { return logic.executeNormalCrafting(64, service, energy, level); }

        void assertSettled() {
            assertFalse(job.suspended, job.permanentExecutionError);
            assertEquals(0, job.tasks.get(pattern).value);
            assertEquals(4, job.waitingFor.list.get(output));
            for (var ingredient : ingredients) {
                assertEquals(4, received.get(ingredient.what()));
                assertEquals(0, logic.getInventory().list.get(ingredient.what()));
            }
            verify(provider, times(4)).pushPattern(eq(pattern), any());
        }
    }
}
