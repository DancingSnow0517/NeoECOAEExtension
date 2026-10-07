package cn.dancingsnow.neoecoae.compat.useless;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ECOPlanMaterialValidator;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests the released Useless interfaces without relying on a running Mixin environment. */
class UselessPatternPlanningTest {
    private final UselessPatternSemanticAdapter adapter = new UselessPatternSemanticAdapter();
    private final AEItemKey result = AEItemKey.of(Items.DIAMOND_BLOCK);
    private final AEItemKey activation = named(Items.BOOK, "encoded spirit");
    private final AEItemKey controller = AEItemKey.of(Items.CHEST);
    private final AEItemKey stabilizer = AEItemKey.of(Items.AMETHYST_SHARD);
    private final AEItemKey dust = AEItemKey.of(Items.REDSTONE);

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({"2,1", "2,5000", "8,1", "8,5000", "8,1000000"})
    void omniversalCrystalCycleVerifiesTheWholeOrderAndReportedSeed(long storedCrystals, long amount) throws Exception {
        AEKey crystal = AEItemKey.of(Items.DIAMOND);
        AEKey seed = AEItemKey.of(Items.PUMPKIN_SEEDS);
        AEKey powder = AEItemKey.of(Items.GLOWSTONE_DUST);
        var grow = pattern(OmniversalPatternDetails.class, crystal, 1, new GenericStack(seed, 1));
        var crush = pattern(OmniversalPatternDetails.class, powder, 1, new GenericStack(crystal, 1));
        var makeSeeds = pattern(OmniversalPatternDetails.class, seed, 32,
            new GenericStack(powder, 8), new GenericStack(stabilizer, 4), new GenericStack(dust, 4));
        for (var pattern : List.of(grow, crush, makeSeeds)) {
            for (int slot = 0; slot < pattern.getInputs().length; slot++) {
                when(pattern.isItemIdInput(slot)).thenReturn(true);
            }
        }
        when(makeSeeds.isTagInput(1)).thenReturn(true);
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(crystal)).thenReturn(List.of(grow));
        when(service.getCraftingFor(powder)).thenReturn(List.of(crush));
        when(service.getCraftingFor(seed)).thenReturn(List.of(makeSeeds));
        var network = new CraftingNetworkCompiler().compile(service, crystal, true, ECOCancellation.NONE);
        var inventory = new KeyCounter();
        inventory.add(crystal, storedCrystals);
        inventory.add(stabilizer, Long.MAX_VALUE);
        inventory.add(dust, Long.MAX_VALUE);
        var outcome = plan(network, inventory, amount);
        if (storedCrystals < 8) {
            assertEquals(PlanningStatus.MISSING_ITEMS, outcome.status(), outcome.trace().diagnostics().toString());
            var cycle = outcome.trace().cycles().getFirst().solveResult();
            assertTrue(cycle.hasExactExecutionCounts(), cycle.summary());
            assertTrue(cycle.diagnostics().stream().anyMatch(d -> d.code()
                == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic.Code.FULL_ORDER_MATERIAL_DEFICIT));
            assertEquals(6L, outcome.state().missingItems().get(powder));
            assertEquals(1, outcome.state().missingItems().size());
            assertFalse(cycle.diagnostics().stream().anyMatch(d -> d.code()
                == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic.Code.SEED_ESTIMATE_LOWER_BOUND));
            outcome.state().missingItems().forEach(entry -> inventory.add(entry.getKey(), entry.getLongValue()));
            outcome = plan(network, inventory, amount);
        }
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertTrue(outcome.state().missingItems().isEmpty());
        var cycle = outcome.trace().cycles().getFirst().solveResult();
        assertTrue(cycle.deliverableOutputs().get(crystal) >= storedCrystals + amount);
        assertTrue(cycle.executionPlan().size() < 100);
        outcome.state().executionProvenance().requireComplete();
    }

    @Test void fixedOutputWithRelaxedOrdinaryInputsCanProveACycle() {
        var pattern = pattern(OmniversalPatternDetails.class, result, 1,
            new GenericStack(stabilizer, 1));
        when(pattern.isItemIdInput(0)).thenReturn(true);
        assertTrue(adapter.analyze(pattern).cycleSafeForStaticPlanning());
        when(pattern.usesDynamicOutputs()).thenReturn(true);
        assertFalse(adapter.analyze(pattern).cycleSafeForStaticPlanning(),
            "Runtime-dependent output components still require a separate proof");
    }

    @Test void dynamicOutputPreservesDeclaredAmountsAndPhysicalPattern() {
        var pattern = ritual();
        var semantics = adapter.analyze(pattern);
        assertTrue(semantics.supported(), semantics.unsupportedReason());
        assertSame(pattern, semantics.physicalPattern());
        assertEquals(pattern.getOutputs(), semantics.producedOutputs());
        assertEquals(List.of(1L, 1L, 6L, 2L), semantics.consumedInputs().stream()
            .map(input -> input.amountPerPattern().longValueExact()).toList());
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, semantics.matchingMode());
        assertFalse(semantics.cycleSafeForStaticPlanning());
        assertFalse(semantics.completeForStaticPlanning());
    }

    @Test void ritualPlansUsingStoredComponentVariantWithoutConsumingAMold() throws Exception {
        var pattern = ritual();
        var actualBook = named(Items.BOOK, "stored spirit");
        var inventory = ingredients(actualBook, 1);
        var network = compile(service(pattern));
        var outcome = plan(network, inventory, 1);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(Map.of(pattern, 1L), outcome.state().patternTimes());
        assertEquals(1L, outcome.state().usedItems().get(actualBook));
        assertEquals(0L, outcome.state().usedItems().get(activation));
        assertEquals(6L, outcome.state().usedItems().get(stabilizer));
        assertEquals(2L, outcome.state().usedItems().get(dust));
        assertEquals(4, outcome.state().usedAmounts().size());
        assertNull(ECOPlanMaterialValidator.firstDeficit(outcome.state(), result, 1, inventory, network));
    }

    @Test void missingRitualInputsProduceCompleteQuantitiesInsteadOfUnsupported() throws Exception {
        var outcome = plan(compile(service(ritual())), new KeyCounter(), 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(1L, outcome.state().missingItems().get(activation));
        assertEquals(1L, outcome.state().missingItems().get(controller));
        assertEquals(6L, outcome.state().missingItems().get(stabilizer));
        assertEquals(2L, outcome.state().missingItems().get(dust));
    }

    @Test void sameItemCraftableChildUsesItsActualDeclaredTemplate() throws Exception {
        var parent = ritual();
        var childOutput = named(Items.BOOK, "craftable spirit");
        var child = pattern(DynamicComponentPattern.class, childOutput, 1,
            new GenericStack(AEItemKey.of(Items.PAPER), 1));
        when(child.usesDynamicOutputs()).thenReturn(true);
        var service = service(parent);
        when(service.getCraftingFor(childOutput)).thenReturn(List.of(child));
        when(service.getFuzzyCraftable(eq(activation), any())).thenAnswer(invocation -> {
            AEKeyFilter filter = invocation.getArgument(1);
            return filter.matches(childOutput) ? childOutput : null;
        });
        var inventory = ingredients(childOutput, 1);
        inventory.remove(childOutput, 1);
        inventory.add(AEItemKey.of(Items.PAPER), 1);
        var network = compile(service);
        assertEquals(childOutput, network.producersOf(result).getFirst().inputs().getFirst().key());
        var outcome = plan(network, inventory, 1);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(Map.of(parent, 1L, child, 1L), outcome.state().patternTimes());
        assertNull(ECOPlanMaterialValidator.firstDeficit(outcome.state(), result, 1, inventory, network));
        outcome.state().executionProvenance().requireComplete();
        var schedule = ECOExecutionSchedule.from(outcome.components(), outcome.executionComponentOrder(),
            outcome.state().patternTimes(), outcome.state().executionProvenance());
        assertEquals(2, schedule.phases().size());
        assertTrue(schedule.phases().getFirst().patternSet().contains(child));
        assertTrue(schedule.phases().getLast().patternSet().contains(parent));
    }

    @Test void relaxedSlotsDoNotRelaxExactNeighborInputs() throws Exception {
        var pattern = ritual();
        var inventory = ingredients(named(Items.BOOK, "stored spirit"), 1);
        inventory.remove(controller, 1);
        inventory.add(named(Items.CHEST, "different component"), 1);
        var network = compile(service(pattern));
        var inputs = network.producersOf(result).getFirst().inputs();
        assertTrue(inputs.getFirst().ignoresComponents());
        assertFalse(inputs.get(1).ignoresComponents());
        var outcome = plan(network, inventory, 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, outcome.status());
        assertEquals(1L, outcome.state().missingItems().get(controller));
    }

    @Test void storedVariantMustSatisfyTheRecipeInputPredicate() throws Exception {
        var pattern = ritual();
        var rejected = named(Items.BOOK, "wrong soul");
        when(pattern.getInputs()[0].isValid(eq(rejected), isNull())).thenReturn(false);
        var inventory = ingredients(rejected, 1);
        var outcome = plan(compile(service(pattern)), inventory, 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, outcome.status());
        assertEquals(1L, outcome.state().missingItems().get(activation));
        assertEquals(0L, outcome.state().usedItems().get(rejected));
    }

    @Test void exactUselessPatternRetainsItsExactContract() {
        var pattern = ritual();
        when(pattern.usesDynamicOutputs()).thenReturn(false);
        when(pattern.isItemIdInput(0)).thenReturn(false);
        var semantics = adapter.analyze(pattern);
        assertTrue(semantics.supported());
        assertTrue(semantics.completeForStaticPlanning());
        assertEquals(PatternSemantics.MatchingMode.EXACT, semantics.matchingMode());
    }

    @Test void tagPoliciesRetainAmountsWithoutRelaxingExactFluidKeys() {
        var pattern = ritual();
        when(pattern.usesDynamicOutputs()).thenReturn(false);
        when(pattern.isItemIdInput(0)).thenReturn(false);
        when(pattern.isTagInput(1)).thenReturn(true);
        when(pattern.isItemIdInput(1)).thenReturn(true); // Useless marks item-tag slots as item-ID inputs too.
        when(pattern.isFluidTagInput(2)).thenReturn(true);
        var semantics = adapter.analyze(pattern);
        assertTrue(semantics.supported());
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, semantics.matchingMode());
        assertTrue(adapter.ignoresComponents(pattern, 1));
        assertFalse(adapter.ignoresComponents(pattern, 2));
        assertEquals(6L, semantics.consumedInputs().get(2).amountPerPattern().longValueExact());
    }

    @Test void publicScaledContractKeepsEffectiveQuantitiesAndInputPolicy() throws Exception {
        var original = ritual();
        var scaled = pattern(IPatternDetails.class, withSettings().extraInterfaces(ScaledPattern.class), result, 8,
            new GenericStack(activation, 8), new GenericStack(controller, 8),
            new GenericStack(stabilizer, 48), new GenericStack(dust, 16));
        when(((ScaledPattern) scaled).getOriginal()).thenReturn(original);
        assertTrue(adapter.supports(scaled));
        assertTrue(adapter.ignoresComponents(scaled, 0));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(result)).thenReturn(List.of(scaled));
        var actualBook = named(Items.BOOK, "stored spirit");
        var network = compile(service);
        var outcome = plan(network, ingredients(actualBook, 8), 8);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(Map.of(scaled, 1L), outcome.state().patternTimes());
        assertEquals(48L, outcome.state().usedItems().get(stabilizer));
    }

    @Test void declaredEqualAmountCraftableIsPreferredBeforeFuzzySearch() {
        var pattern = ritual();
        var input = pattern.getInputs()[0];
        var alternate = named(Items.BOOK, "alternate spirit");
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {
            new GenericStack(activation, 1), new GenericStack(alternate, 1)
        });
        var service = service(pattern);
        when(service.getCraftingFor(alternate)).thenReturn(List.of(mock(IPatternDetails.class)));
        assertEquals(alternate, adapter.preferredInputKey(pattern, 0, activation, service));
        verify(service, never()).getFuzzyCraftable(any(), any());
    }

    @Test void invalidComponentCandidateCannotReplaceDeclaredInput() {
        var pattern = ritual();
        when(pattern.getInputs()[0].isValid(any(), isNull())).thenReturn(false);
        var service = service(pattern);
        when(service.getFuzzyCraftable(eq(activation), any())).thenAnswer(invocation -> {
            AEKeyFilter filter = invocation.getArgument(1);
            return filter.matches(activation) ? activation : null;
        });
        assertEquals(activation, adapter.preferredInputKey(pattern, 0, activation, service));
    }

    @Test void nestedAndCyclicWrappersAreHandledWithoutAnArbitraryDepthLimit() {
        IPatternDetails wrapped = ritual();
        for (int i = 0; i < 8; i++) {
            var next = mock(IPatternDetails.class, withSettings().extraInterfaces(ScaledPattern.class));
            when(((ScaledPattern) next).getOriginal()).thenReturn(wrapped);
            wrapped = next;
        }
        assertTrue(adapter.supports(wrapped));
        var cyclic = mock(IPatternDetails.class, withSettings().extraInterfaces(ScaledPattern.class));
        when(((ScaledPattern) cyclic).getOriginal()).thenReturn(cyclic);
        assertFalse(adapter.supports(cyclic));
    }

    @Test void componentRelaxationCannotProveReusableCycle() {
        var pattern = ritual();
        when(pattern.getInputs()[0].getRemainingKey(activation)).thenReturn(activation);
        assertFalse(adapter.analyze(pattern).cycleSafeForStaticPlanning());
        when(pattern.usesDynamicOutputs()).thenReturn(false);
        assertFalse(adapter.analyze(pattern).cycleSafeForStaticPlanning());
        when(pattern.isItemIdInput(0)).thenReturn(false);
        assertTrue(adapter.analyze(pattern).cycleSafeForStaticPlanning());
    }

    @Test void acceptedDynamicOutputCanClaimActualComponentsThroughExistingBridge() {
        var pattern = ritual();
        when(pattern.dynamicPatternIdentity()).thenReturn("useless_mod:omniversal|recipe=ritual_upgrade");
        when(pattern.isItemIdOutput(0)).thenReturn(true);
        Object cpu = new Object();
        var manager = DynamicPatternCpuStateManager.INSTANCE;
        try {
            var registration = ECOUselessDynamicOutputBridge.prepare(cpu, pattern, 3);
            assertNotNull(registration);
            registration.commit(UUID.randomUUID(), result);
            var actual = named(Items.DIAMOND_BLOCK, "runtime spirit");
            var claim = manager.claim(cpu, actual, 3, Actionable.MODULATE);
            assertEquals(3L, claim.claimedAmount());
            assertEquals(result, claim.claims().getFirst().exactExpectedKey());
            assertFalse(manager.hasAnyPending(cpu));
        } finally {
            manager.clear(cpu);
        }
    }

    @Test void absentUselessLeavesDefaultAdaptersUsable() throws Exception {
        String api = UselessPatternApi.class.getName();
        String adapterName = UselessPatternSemanticAdapter.class.getName();
        String registry = "cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapters";
        var isolated = Set.of(api, adapterName, registry);
        var parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.sorrowmist.useless.")) throw new ClassNotFoundException(name);
                if (!isolated.contains(name) && !name.startsWith(api + "$")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        assertNotNull(loader.loadClass(registry).getMethod("defaults").invoke(null));
        var instance = loader.loadClass(adapterName).getConstructor().newInstance();
        assertEquals(false, instance.getClass().getMethod("supports", IPatternDetails.class)
            .invoke(instance, mock(IPatternDetails.class)));
    }

    private DynamicComponentPattern ritual() {
        var pattern = pattern(DynamicComponentPattern.class, result, 1,
            new GenericStack(activation, 1), new GenericStack(controller, 1),
            new GenericStack(stabilizer, 6), new GenericStack(dust, 2));
        when(pattern.usesDynamicOutputs()).thenReturn(true);
        when(pattern.isItemIdInput(0)).thenReturn(true);
        return pattern;
    }

    private KeyCounter ingredients(AEKey book, long copies) {
        var inventory = new KeyCounter();
        inventory.add(book, copies);
        inventory.add(controller, copies);
        inventory.add(stabilizer, 6 * copies);
        inventory.add(dust, 2 * copies);
        return inventory;
    }

    private ICraftingService service(IPatternDetails pattern) {
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(result)).thenReturn(List.of(pattern));
        return service;
    }

    private CompiledNetwork compile(ICraftingService service) throws Exception {
        return new CraftingNetworkCompiler().compile(service, result, true, ECOCancellation.NONE);
    }

    private static ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter inventory, long amount)
            throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, amount, true, ECOCancellation.NONE);
    }

    private static <T extends IPatternDetails> T pattern(Class<T> type, AEKey output, long count,
            GenericStack... inputs) {
        return pattern(type, withSettings(), output, count, inputs);
    }

    private static <T extends IPatternDetails> T pattern(Class<T> type, org.mockito.MockSettings settings,
            AEKey output, long count, GenericStack... inputs) {
        T pattern = mock(type, settings);
        var sourceInputs = java.util.Arrays.stream(inputs).map(stack -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(stack.amount());
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(stack.what(), 1) });
            when(input.isValid(any(), isNull())).thenAnswer(invocation ->
                stack.what() instanceof AEItemKey expected && invocation.getArgument(0) instanceof AEItemKey actual
                    && expected.getItem() == actual.getItem());
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(pattern.getInputs()).thenReturn(sourceInputs);
        var product = new GenericStack(output, count);
        when(pattern.getOutputs()).thenReturn(List.of(product));
        when(pattern.getPrimaryOutput()).thenReturn(product);
        return pattern;
    }

    private static AEItemKey named(Item item, String name) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }
}
