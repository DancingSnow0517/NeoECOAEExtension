package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching;
import cn.dancingsnow.neoecoae.crafting.ECOComponentInsensitiveProcessingPattern;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltPlanningBridge;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOProcessingComponentMatchingTest {
    private static final Set<ResourceLocation> MARKED = Set.of(ResourceLocation.parse("minecraft:iron_pickaxe"));

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static AEItemKey pickaxe(int damage) {
        var stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(damage);
        return AEItemKey.of(stack);
    }

    private static AEProcessingPattern pattern(GenericStack... inputs) {
        return patternWithOutput(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1), inputs);
    }

    private static AEProcessingPattern patternWithOutput(GenericStack output, GenericStack... inputs) {
        var encoded = mock(EncodedProcessingPattern.class);
        when(encoded.sparseInputs()).thenReturn(Arrays.asList(inputs));
        when(encoded.sparseOutputs()).thenReturn(List.of(output));
        var definition = mock(AEItemKey.class);
        when(definition.get(AEComponents.ENCODED_PROCESSING_PATTERN)).thenReturn(encoded);
        return new AEProcessingPattern(definition);
    }

    private static AEProcessingPattern persistentPattern(GenericStack... inputs) {
        var stack = new ItemStack(Items.PAPER);
        // AE2's missing-content item is bound by the mod registry lifecycle, absent in a plain JVM.
        var encoded = mock(EncodedProcessingPattern.class);
        when(encoded.sparseInputs()).thenReturn(Arrays.asList(inputs));
        when(encoded.sparseOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
        stack.set(AEComponents.ENCODED_PROCESSING_PATTERN, encoded);
        return new AEProcessingPattern(AEItemKey.of(stack));
    }

    @Test void foreignPlannerUsesCapturedInterfaceItemsWithoutChangingRegisteredPatterns() throws Exception {
        var source = persistentPattern(new GenericStack(pickaxe(0), 1));
        var actual = pickaxe(131);
        var registered = service(source);
        var marked = new java.util.HashSet<>(MARKED);
        var foreign = ECOThunderboltPlanningBridge.forCalculation(registered, marked);
        marked.clear();
        var output = source.getPrimaryOutput().what();
        var adapted = foreign.getCraftingFor(output).iterator().next();
        assertInstanceOf(ECOComponentInsensitiveProcessingPattern.class, adapted);
        assertSame(adapted, foreign.getCraftingFor(output).iterator().next());
        assertFalse(source.getInputs()[0].isValid(actual, null));
        assertTrue(adapted.getInputs()[0].isValid(actual, null));
        assertFalse(adapted.getInputs()[0].isValid(AEItemKey.of(Items.DIAMOND_PICKAXE), null));
        // Exercise the released Thunderbolt planner, not just the adapter predicate.
        var inventory = new appeng.crafting.inv.CraftingSimulationState() {
            protected long simulateExtractParent(AEKey key, long amount) { return actual.equals(key) ? Math.min(1, amount) : 0; }
            protected Iterable<AEKey> findFuzzyParent(AEKey key) {
                return ECOProcessingComponentMatching.sameItem(actual, key) ? List.of(actual) : List.of();
            }
        };
        var result = com.moakiee.thunderbolt.core.crafting.planner.FastCraftingPlanner.tryAttempt(
            foreign, inventory, null, output, 1, false);
        assertTrue(result.handled());
        assertNotNull(result.plan());
        assertFalse(result.plan().simulation());
        assertEquals(1, result.plan().usedItems().get(actual));
        assertTrue(result.plan().patternTimes().containsKey(adapted));
    }

    @Test void foreignProcessingPolicySurvivesReloadAndPreservesSparseInputAmounts() {
        var source = persistentPattern(new GenericStack(pickaxe(0), 1), new GenericStack(pickaxe(0), 2));
        var task = (ECOComponentInsensitiveProcessingPattern) ECOComponentInsensitiveProcessingPattern.of(source, MARKED);
        var restored = (ECOComponentInsensitiveProcessingPattern) ECOComponentInsensitiveProcessingPattern.restore(
            new AEProcessingPattern(task.getDefinition()));
        assertEquals(source.getDefinition(), restored.ecoProviderPattern().getDefinition());
        assertEquals(task.getDefinition(), restored.getDefinition());
        var actual = pickaxe(131);
        var holders = new KeyCounter[] {new KeyCounter()};
        holders[0].add(actual, 3);
        var emitted = new KeyCounter();
        restored.pushInputsToExternalInventory(holders, emitted::add);
        assertEquals(3, emitted.get(actual));
        assertEquals(0, emitted.get(pickaxe(0)));
        // No permissive context may leak into subsequent unmarked dispatches.
        assertFalse(ECOProcessingComponentMatching.pushInputs(source, holders, (key, amount) -> fail()));
    }

    @Test void foreignProviderUsesPhysicalIdentityAndCapturedPolicyAfterInterfaceChanges() {
        var source = persistentPattern(new GenericStack(pickaxe(0), 1));
        var task = (ECOComponentInsensitiveProcessingPattern) ECOComponentInsensitiveProcessingPattern.of(source, MARKED);
        var actual = pickaxe(131);
        var holders = new KeyCounter[] {new KeyCounter()};
        holders[0].add(actual, 1);
        var emitted = new KeyCounter();
        var physical = mock(ICraftingProvider.class);
        when(physical.pushPattern(same(source), same(holders))).thenAnswer(call ->
            ECOProcessingComponentMatching.pushInputs(source, holders, emitted::add));
        assertSame(task.provider(physical), task.provider(physical));
        assertTrue(task.provider(physical).pushPattern(task, holders));
        assertEquals(1, emitted.get(actual));
        verify(physical).pushPattern(source, holders);
        assertFalse(ECOProcessingComponentMatching.pushInputs(source, holders, (key, amount) -> fail()));
    }

    @Test void foreignPolicyPreservesBatchAdmissionAndJobAwareProviderContracts() {
        var source = persistentPattern(new GenericStack(pickaxe(0), 1));
        var task = (ECOComponentInsensitiveProcessingPattern) ECOComponentInsensitiveProcessingPattern.of(source, MARKED);
        var physical = mock(ICraftingProvider.class, withSettings().extraInterfaces(
            com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider.class,
            cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider.class));
        var batch = (com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider) physical;
        var holders = new KeyCounter[] {new KeyCounter()}; holders[0].add(pickaxe(131), 1);
        var emitted = new KeyCounter();
        when(batch.pushBatch(same(source), same(holders), eq(1L))).thenAnswer(call ->
            ECOProcessingComponentMatching.pushInputs(source, holders, emitted::add) ? 0L : 1L);
        var wrapped = (com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider) task.provider(physical);
        assertEquals(0, wrapped.pushBatch(task, holders, 1));
        assertEquals(1, emitted.get(pickaxe(131)));
        var job = UUID.randomUUID();
        var jobProvider = (cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider) physical;
        when(jobProvider.pushPattern(same(source), same(holders), eq(job))).thenAnswer(call ->
            ECOProcessingComponentMatching.pushInputs(source, holders, emitted::add));
        assertTrue(((cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider) wrapped)
            .pushPattern(task, holders, job));
        assertEquals(2, emitted.get(pickaxe(131)));
        assertFalse(ECOProcessingComponentMatching.pushInputs(source, holders, (key, amount) -> fail()));
    }

    @Test void preparedForeignBatchRetainsThePolicyUntilCommit() throws Exception {
        try { Class.forName("com.moakiee.thunderbolt.api.crafting.batch.PreparedBatch"); }
        catch (ClassNotFoundException oldVersion) {
            org.junit.jupiter.api.Assumptions.abort("This Thunderbolt version predates prepared admission");
        }
        var source = persistentPattern(new GenericStack(pickaxe(0), 1));
        var task = (ECOComponentInsensitiveProcessingPattern) ECOComponentInsensitiveProcessingPattern.of(source, MARKED);
        var physical = mock(com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider.class);
        var holders = new KeyCounter[] {new KeyCounter()}; holders[0].add(pickaxe(131), 1);
        var emitted = new KeyCounter();
        var prepared = mock(com.moakiee.thunderbolt.api.crafting.batch.PreparedBatch.class);
        when(physical.prepareBatch(same(source), same(holders), eq(1L), isNull())).thenReturn(prepared);
        when(prepared.push(1)).thenAnswer(call ->
            ECOProcessingComponentMatching.pushInputs(source, holders, emitted::add) ? 0L : 1L);
        var wrapped = (com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider) task.provider(physical);
        assertEquals(0, wrapped.prepareBatch(task, holders, 1, null).push(1));
        assertEquals(1, emitted.get(pickaxe(131)));
        assertFalse(ECOProcessingComponentMatching.pushInputs(source, holders, (key, amount) -> fail()));
    }

    @Test void absentPolicyRetainsOriginalServiceAndUnsupportedPatterns() {
        var source = persistentPattern(new GenericStack(pickaxe(0), 1));
        var registered = service(source);
        assertSame(registered, ECOThunderboltPlanningBridge.forCalculation(registered, Set.of()));
        assertSame(source, ECOComponentInsensitiveProcessingPattern.of(source, Set.of(ResourceLocation.parse("minecraft:stone"))));
        var custom = mock(IPatternDetails.class);
        assertSame(custom, ECOComponentInsensitiveProcessingPattern.of(custom, MARKED));
        assertSame(source, ECOComponentInsensitiveProcessingPattern.restore(source));
    }

    @Test void interfacePolicyIsCapturedOnceBeforeForeignWorkerReads() {
        var settings = mock(appeng.api.networking.crafting.ICraftingService.class,
            withSettings().extraInterfaces(cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings.class));
        var network = (cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings) settings;
        var marked = new java.util.HashSet<>(MARKED);
        when(network.neoecoae$getFuzzyPlanningItemIds()).thenReturn(marked);
        var snapshot = ECOThunderboltPlanningBridge.capture(settings);
        marked.clear();
        assertEquals(MARKED, snapshot);
        verify(network).neoecoae$getFuzzyPlanningItemIds();
        assertEquals(Set.of(), ECOThunderboltPlanningBridge.capture(mock(appeng.api.networking.crafting.ICraftingService.class)));
    }

    private static CraftingService service(AEProcessingPattern... patterns) {
        var service = mock(CraftingService.class);
        var index = new java.util.LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (var pattern : patterns) index.put(pattern.getOutputs().getFirst().what(), List.of(pattern));
        when(service.getCraftingFor(any())).thenAnswer(call ->
            index.getOrDefault(call.getArgument(0), List.of()));
        when(service.getCraftables(any())).thenAnswer(call -> {
            appeng.api.storage.AEKeyFilter filter = call.getArgument(0);
            return index.keySet().stream().filter(filter::matches).collect(java.util.stream.Collectors.toSet());
        });
        return service;
    }

    private static ECOPlanningResult plan(AEProcessingPattern pattern, KeyCounter stock,
            Set<ResourceLocation> marked, long count) throws Exception {
        return plan(pattern, stock, marked, count, false);
    }

    private static ECOPlanningResult plan(AEProcessingPattern pattern, KeyCounter stock,
            Set<ResourceLocation> marked, long count, boolean lite) throws Exception {
        return new ECOCraftingPlannerService().createSession(service(pattern),
            pattern.getOutputs().getFirst().what(), stock, false, lite, marked)
            .plan(count, false, ECOCancellation.NONE);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void markedDamagedPickaxePlansAndDispatchesItsActualComponents(boolean lite) throws Exception {
        var expected = pickaxe(0);
        var actual = pickaxe(131); // Remaining durability 119/250, as in the reported screenshot.
        var pattern = pattern(new GenericStack(expected, 1));
        assertFalse(pattern.getInputs()[0].isValid(actual, null));
        var stock = new KeyCounter(); stock.add(actual, 1);
        var result = plan(pattern, stock, MARKED, 1, lite);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(1, result.plan().usedItems().get(actual));
        assertEquals(0, result.plan().usedItems().get(expected));
        assertTrue(result.plan().missingItems().isEmpty());
        assertSame(pattern, result.plan().patternTimes().keySet().iterator().next());
        assertEquals(MARKED, result.executionContract().fuzzyPlanningItemIds());
        assertEquals(lite, result.liteCraftingMode(), "ID matching preserves the requested planner mode");

        var level = mock(Level.class);
        var cpu = mock(ECOCraftingCPU.class);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getLevel()).thenReturn(level);
        var logic = new ECOCraftingCPULogic(cpu);
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(UUID.randomUUID());
        ExecutingCraftingJob job;
        try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(result.plan(), result.executionContract().executionPlan(),
                ignored -> {}, link, null);
        }
        job.fuzzyPlanningItemIds = result.executionContract().fuzzyPlanningItemIds();
        logic.setJobFromPersistence(job);
        logic.getInventory().insert(actual, 1, Actionable.MODULATE);
        var provider = mock(ICraftingProvider.class);
        var rejected = mock(ICraftingProvider.class);
        when(rejected.pushPattern(eq(pattern), any())).thenAnswer(call -> {
            KeyCounter[] inputs = call.getArgument(1);
            assertEquals(1, inputs[0].get(actual));
            assertEquals(0, logic.getInventory().list.get(actual));
            return false;
        });
        var received = new KeyCounter();
        when(provider.pushPattern(eq(pattern), any())).thenAnswer(call -> {
            KeyCounter[] inputs = call.getArgument(1);
            assertEquals(1, inputs[0].get(actual));
            // Plain JVM tests do not apply Mixins; call the exact emission implementation used by the hook.
            assertTrue(ECOProcessingComponentMatching.pushInputs(pattern, inputs, received::add));
            return true;
        });
        var service = service(pattern);
        when(service.getProviders(pattern)).thenReturn(List.of(rejected, provider));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(1, logic.executeNormalCrafting(64, service, energy, level));
        assertEquals(1, received.get(actual));
        assertEquals(0, received.get(expected));
        assertEquals(0, logic.getInventory().list.get(actual));
        assertEquals(0, job.tasks.get(pattern).value);
        assertEquals(1, job.waitingFor.list.get(AEItemKey.of(Items.IRON_BLOCK)));
        assertFalse(job.suspended, job.permanentExecutionError);
        verify(rejected).pushPattern(eq(pattern), any());
        assertFalse(ECOProcessingComponentMatching.pushInputs(pattern, new KeyCounter[] {stock}, (key, amount) ->
            fail("Dispatch scope leaked")));
    }

    @Test void liteKeepsExactAndIdMatchedDemandsSeparateForTheSameMaterial() throws Exception {
        var expected = pickaxe(0);
        var actual = pickaxe(131);
        var strictOutput = AEItemKey.of(Items.GOLD_INGOT);
        var fuzzyOutput = AEItemKey.of(Items.DIAMOND);
        var root = pattern(new GenericStack(strictOutput, 1), new GenericStack(fuzzyOutput, 1));
        var fuzzy = patternWithOutput(new GenericStack(fuzzyOutput, 1), new GenericStack(expected, 1));
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(expected, 1)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(eq(expected), any())).thenReturn(true);
        var strict = mock(IPatternDetails.class);
        when(strict.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(strict.getOutputs()).thenReturn(List.of(new GenericStack(strictOutput, 1)));
        var routes = Map.<AEKey, List<IPatternDetails>>of(root.getPrimaryOutput().what(), List.of(root),
            strictOutput, List.of(strict), fuzzyOutput, List.of(fuzzy));
        var service = mock(CraftingService.class);
        when(service.getCraftingFor(any(AEKey.class))).thenAnswer(call ->
            routes.getOrDefault(call.getArgument(0), List.of()));
        var stock = new KeyCounter();
        stock.add(actual, 2);
        var planner = new ECOCraftingPlannerService();
        var missing = planner.createSession(service, root.getPrimaryOutput().what(), stock, false, true, MARKED)
            .plan(1, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.MISSING_ITEMS, missing.status());
        assertTrue(missing.liteCraftingMode());
        assertEquals(java.math.BigInteger.ONE, missing.exactMissingItems().get(expected).toBigInteger());
        assertEquals(java.math.BigInteger.ONE, missing.exactUsedItems().get(actual).toBigInteger());
        stock.add(expected, 1);
        var funded = planner.createSession(service, root.getPrimaryOutput().what(), stock, false, true, MARKED)
            .plan(1, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, funded.status());
        assertTrue(funded.liteCraftingMode());
        assertNull(funded.executionPlanError());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unmarkedItemsAndOtherToolTypesStayExact(boolean lite) throws Exception {
        var pattern = pattern(new GenericStack(pickaxe(0), 1));
        var stock = new KeyCounter(); stock.add(pickaxe(131), 1);
        assertNotEquals(PlanningStatus.SUCCESS, plan(pattern, stock, Set.of(), 1, lite).status());
        stock.clear(); stock.add(AEItemKey.of(Items.DIAMOND_PICKAXE), 1);
        assertNotEquals(PlanningStatus.SUCCESS, plan(pattern, stock, MARKED, 1, lite).status());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingPlanSnapshotIncludesEveryReservedDamagedPickaxe(boolean differentDamage) throws Exception {
        var expected = pickaxe(0);
        var pattern = pattern(new GenericStack(expected, 1));
        var stock = new KeyCounter();
        for (int i = 0; i < 3; i++) stock.add(pickaxe(differentDamage ? 130 + i : 131), 1);
        var result = plan(pattern, stock, MARKED, 4);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(1, result.plan().missingItems().get(expected));
        assertEquals(3, result.exactUsedItems().values().stream()
            .mapToLong(amount -> amount.longValueExact()).sum());

        var snapshot = cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory.create(result);
        for (var entry : stock) {
            var node = snapshot.nodes().stream().filter(material -> material.key().equals(entry.getKey()))
                .findFirst().orElseThrow(() -> new AssertionError("Reserved damaged pickaxe missing from snapshot"));
            assertEquals(java.math.BigInteger.valueOf(entry.getLongValue()), node.fromInventoryBigInteger());
            assertEquals(java.math.BigInteger.ZERO, node.missingBigInteger());
            assertEquals(java.math.BigInteger.ZERO, node.toCraftBigInteger());
        }
        var missing = snapshot.nodes().stream().filter(node -> node.key().equals(expected)).findFirst().orElseThrow();
        assertEquals(java.math.BigInteger.ONE, missing.missingBigInteger());
        assertEquals(java.math.BigInteger.ZERO, missing.fromInventoryBigInteger());
    }

    @Test void markingDoesNotOverrideCraftingOrCustomRecipePredicates() {
        var expected = pickaxe(0);
        var actual = pickaxe(131);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(expected, 1)});
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(eq(expected), any())).thenReturn(true);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(actual, 1, Actionable.MODULATE);
        assertFalse(ECOProcessingComponentMatching.isValid(pattern, input, expected, actual, null));
        assertNull(ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
            new ECOCraftingInputPreview(inventory, pattern, Map.of(), new ECOCraftingRemainderCache(), MARKED),
            null, new KeyCounter(), new KeyCounter(), new ECOCraftingRemainderCache()));
        assertEquals(1, inventory.list.get(actual));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void finalRequestedOutputStillRequiresItsExactComponents(boolean lite) throws Exception {
        var pattern = pattern(new GenericStack(pickaxe(0), 1));
        var named = new ItemStack(Items.IRON_BLOCK);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact requested result"));
        var stock = new KeyCounter(); stock.add(pickaxe(131), 1);
        var marked = Set.of(ResourceLocation.parse("minecraft:iron_pickaxe"),
            ResourceLocation.parse("minecraft:iron_block"));
        var result = new ECOCraftingPlannerService().createSession(service(pattern), AEItemKey.of(named),
            stock, false, lite, marked).plan(1, false, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, result.status());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void intermediateProducerMayReturnAnotherComponentVariant(boolean lite) throws Exception {
        var actual = pickaxe(131);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var producer = patternWithOutput(new GenericStack(actual, 1), new GenericStack(ingot, 1));
        var consumer = pattern(new GenericStack(pickaxe(0), 1));
        var stock = new KeyCounter(); stock.add(ingot, 1);
        var result = new ECOCraftingPlannerService().createSession(service(producer, consumer),
            AEItemKey.of(Items.IRON_BLOCK), stock, false, lite, MARKED)
            .plan(1, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(Map.of(producer, 1L, consumer, 1L), result.plan().patternTimes());
        assertEquals(1, result.plan().usedItems().get(ingot));
        assertEquals(actual, producer.getOutputs().getFirst().what());
    }

    @Test void nativeCpuCannotAcceptAPlanRequiringProcessingOverrides() {
        var pattern = pattern(new GenericStack(pickaxe(0), 1));
        var stock = new KeyCounter(); stock.add(pickaxe(131), 1);
        var plan = new appeng.crafting.CraftingPlan(pattern.getOutputs().getFirst(), 1,
            false, false, stock, new KeyCounter(), new KeyCounter(), Map.of(pattern, 1L));
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, plan,
            mock(cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace.class),
            List.of(), List.of(), List.of(), 0);
        result.setFuzzyPlanningItemIds(MARKED);
        cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.register(plan, result);
        try {
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
            result.setFuzzyPlanningItemIds(Set.of(ResourceLocation.parse("minecraft:stone")));
            assertTrue(ECOExternalCpuSupport.supportsPlan(plan));
        } finally {
            cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.clear();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void multipleComponentVariantsSupplyTheFullProcessingAmount(boolean lite) throws Exception {
        var expected = pickaxe(0);
        var named = new ItemStack(Items.IRON_PICKAXE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("My pickaxe"));
        var actual = AEItemKey.of(named);
        var pattern = pattern(new GenericStack(expected, 2));
        var stock = new KeyCounter(); stock.add(actual, 1); stock.add(pickaxe(131), 1);
        var result = plan(pattern, stock, MARKED, 1, lite);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(actual, 1, Actionable.MODULATE);
        inventory.insert(pickaxe(131), 1, Actionable.MODULATE);
        var inputs = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
            new ECOCraftingInputPreview(inventory, pattern, Map.of(), new ECOCraftingRemainderCache(), MARKED),
            null, new KeyCounter(), new KeyCounter(), new ECOCraftingRemainderCache());
        assertNotNull(inputs);
        assertEquals(1, inputs[0].get(actual));
        assertEquals(1, inputs[0].get(pickaxe(131)));
        assertEquals(1, inventory.list.get(actual)); // Preview never debits the physical inventory.
    }

    @Test void sparseEmissionPreservesOrderAndDistinctComponentSlots() {
        var plain = pickaxe(0);
        var encodedDamaged = pickaxe(20);
        var first = pickaxe(131);
        var second = pickaxe(50);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var pattern = pattern(new GenericStack(plain, 1), null, new GenericStack(iron, 2),
            new GenericStack(plain, 1), new GenericStack(encodedDamaged, 1));
        var counters = new KeyCounter[pattern.getInputs().length];
        for (int slot = 0; slot < counters.length; slot++) {
            counters[slot] = new KeyCounter();
            var expected = pattern.getInputs()[slot].getPossibleInputs()[0].what();
            counters[slot].add(expected.equals(plain) ? first : expected.equals(encodedDamaged) ? second : iron,
                pattern.getInputs()[slot].getMultiplier());
        }
        var received = new java.util.ArrayList<GenericStack>();
        try (var ignored = ECOProcessingComponentMatching.openDispatch(MARKED)) {
            assertTrue(ECOProcessingComponentMatching.pushInputs(pattern, counters,
                (key, amount) -> received.add(new GenericStack(key, amount))));
        }
        assertEquals(List.of(new GenericStack(first, 1), new GenericStack(iron, 2),
            new GenericStack(first, 1), new GenericStack(second, 1)), received);
        assertEquals(2, counters[0].get(first)); // Emission does not mutate the provider's input counters.
    }

    @Test void invalidSparseEmissionCannotPartiallyInsertItems() {
        var plain = pickaxe(0);
        var pattern = pattern(new GenericStack(plain, 2));
        var incomplete = new KeyCounter(); incomplete.add(pickaxe(131), 1);
        try (var ignored = ECOProcessingComponentMatching.openDispatch(MARKED)) {
            assertThrows(IllegalArgumentException.class, () -> ECOProcessingComponentMatching.pushInputs(pattern,
                new KeyCounter[] {incomplete}, (key, amount) -> fail("Partial insertion")));
        }
    }

    @Test void failedExtractionKeepsActualInventoryAndProtectedStock() {
        var actual = pickaxe(131);
        var pattern = pattern(new GenericStack(pickaxe(0), 2));
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(actual, 2, Actionable.MODULATE);
        var inputs = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(pattern,
            new ECOCraftingInputPreview(inventory, pattern, Map.of(actual, PlannerAmount.ONE), new ECOCraftingRemainderCache(), MARKED),
            null, new KeyCounter(), new KeyCounter(), new ECOCraftingRemainderCache());
        assertNull(inputs);
        assertEquals(2, inventory.list.get(actual));
    }
}
