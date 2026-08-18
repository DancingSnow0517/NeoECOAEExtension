package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.api.crafting.IECOPlannerInputPolicy;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOCycleTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOHyperflowResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOPlanningSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

class ECOAE2PlanAssemblerTest {
    @AfterEach
    void clearPlanCache() {
        ECOAE2CraftingPlanCache.clear();
    }

    @Test
    void exactPlanCacheIsInventorySensitiveAndReturnsDefensiveCopies() {
        TestKey source = new TestKey("cached_source");
        TestKey target = new TestKey("cached_target");
        IPatternDetails pattern = pattern("cached_recipe");
        ECOAE2PatternVariant variant = new ECOAE2PatternVariant(pattern, 0, List.of());
        var operation = new ECOPlanningOperation<AEKey, ECOAE2PatternVariant>(
            variant, Map.of(source, 1L), Map.of(target, 1L)
        );
        var firstSnapshot = new ECOAE2PlanningSnapshot(
            new ECOPlanningProblem<>(List.of(operation), Map.of(source, 2L), Map.of(target, 1L)),
            target, 1L, false, Map.of(), false, false
        );
        var changedInventory = new ECOAE2PlanningSnapshot(
            new ECOPlanningProblem<>(List.of(operation), Map.of(source, 3L), Map.of(target, 1L)),
            target, 1L, false, Map.of(), false, false
        );
        KeyCounter used = new KeyCounter();
        used.add(source, 1L);
        CraftingPlan plan = new CraftingPlan(
            new GenericStack(target, 1L), 64L, false, false,
            used, new KeyCounter(), new KeyCounter(), Map.of(pattern, 1L)
        );

        ECOAE2CraftingPlanCache.put(
            firstSnapshot, CalculationStrategy.REPORT_MISSING_ITEMS, plan
        );
        CraftingPlan firstHit = ECOAE2CraftingPlanCache.get(
            firstSnapshot, CalculationStrategy.REPORT_MISSING_ITEMS
        ).orElseThrow();
        firstHit.usedItems().add(source, 10L);
        CraftingPlan secondHit = ECOAE2CraftingPlanCache.get(
            firstSnapshot, CalculationStrategy.REPORT_MISSING_ITEMS
        ).orElseThrow();

        assertEquals(1L, secondHit.usedItems().get(source));
        assertTrue(ECOAE2CraftingPlanCache.get(
            changedInventory, CalculationStrategy.REPORT_MISSING_ITEMS
        ).isEmpty());
    }

    @Test
    void missingSimulationRetainsCraftChainSummary() {
        TestKey source = new TestKey("source");
        TestKey intermediate = new TestKey("intermediate");
        TestKey target = new TestKey("target");
        IPatternDetails sourceToIntermediate = pattern("source_to_intermediate");
        IPatternDetails intermediateToTarget = pattern("intermediate_to_target");
        ECOAE2PatternVariant first = new ECOAE2PatternVariant(sourceToIntermediate, 0, List.of());
        ECOAE2PatternVariant second = new ECOAE2PatternVariant(intermediateToTarget, 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(first, Map.of(source, 1L), Map.of(intermediate, 1L)),
                new ECOPlanningOperation<>(second, Map.of(intermediate, 1L), Map.of(target, 1L))
            ),
            Map.of(source, 3L),
            Map.of(target, 1L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 1L, false, Map.of(), false, false
        );
        Map<ECOAE2PatternVariant, Long> executions = new LinkedHashMap<>();
        executions.put(first, 1L);
        executions.put(second, 1L);
        var candidate = new ECOPlanCandidate<>(executions, 0L, 0L, 1L, 0L);

        var plan = ECOAE2PlanAssembler.missingSimulationPlan(
            snapshot, candidate, Map.of(source, 1L), 64L
        );

        assertTrue(plan.simulation());
        assertEquals(1L, plan.usedItems().get(source));
        assertEquals(1L, plan.missingItems().get(source));
        assertEquals(Map.of(sourceToIntermediate, 1L, intermediateToTarget, 1L), plan.patternTimes());
    }

    @Test
    void cycleMissingReportUsesExactSeedAmounts() {
        TestKey seed = new TestKey("seed");
        TestKey ordinaryDependency = new TestKey("ordinary_dependency");
        TestKey raw = new TestKey("raw");
        TestKey target = new TestKey("target");
        ECOAE2PatternVariant grow = new ECOAE2PatternVariant(pattern("grow"), 0, List.of());
        ECOAE2PatternVariant makeDependency = new ECOAE2PatternVariant(
            pattern("make_dependency"), 0, List.of()
        );
        ECOAE2PatternVariant deliver = new ECOAE2PatternVariant(pattern("deliver"), 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(
                    grow,
                    Map.of(seed, 1L, ordinaryDependency, 1L),
                    Map.of(seed, 2L)
                ),
                new ECOPlanningOperation<>(
                    makeDependency, Map.of(raw, 1L), Map.of(ordinaryDependency, 1L)
                ),
                new ECOPlanningOperation<>(deliver, Map.of(seed, 1L), Map.of(target, 1L))
            ),
            Map.of(),
            Map.of(target, 1L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 1L, false, Map.of(), false, false
        );
        var candidate = new ECOPlanCandidate<>(
            Map.of(grow, 1L, makeDependency, 1L, deliver, 1L), 0L, 0L, 1L, 0L
        );
        var result = new ECOHyperflowResult<>(
            ECOHyperflowResult.Status.MISSING_SOURCES,
            candidate,
            1L,
            Optional.of(new ECOCycleTrace<>(Set.of(grow), Set.of(grow), Map.of(seed, 1L)))
        );

        var plan = ECOAE2PlanAssembler.assemble(snapshot, result).orElseThrow();

        assertTrue(plan.simulation());
        assertEquals(1L, plan.missingItems().get(seed));
        assertEquals(1L, plan.missingItems().get(raw));
        assertEquals(0L, plan.missingItems().get(ordinaryDependency));
    }

    @Test
    void cycleFallbackDoesNotUseUpperRecipeDemandAsBootstrapSeed() {
        TestKey seed = new TestKey("seed");
        TestKey target = new TestKey("target");
        ECOAE2PatternVariant grow = new ECOAE2PatternVariant(pattern("grow"), 0, List.of());
        ECOAE2PatternVariant consume = new ECOAE2PatternVariant(pattern("consume"), 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(grow, Map.of(seed, 1L), Map.of(seed, 2L)),
                new ECOPlanningOperation<>(consume, Map.of(seed, 64L), Map.of(target, 1L))
            ),
            Map.of(),
            Map.of(target, 1L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 1L, false, Map.of(), false, false
        );
        var result = new ECOHyperflowResult<>(
            ECOHyperflowResult.Status.MISSING_SOURCES,
            new ECOPlanCandidate<>(Map.of(grow, 64L, consume, 1L), 0L, 0L, 0L, 0L),
            1L,
            Optional.of(new ECOCycleTrace<>(Set.of(grow), Set.of(grow), Map.of()))
        );

        var plan = ECOAE2PlanAssembler.assemble(snapshot, result).orElseThrow();

        assertEquals(1L, plan.missingItems().get(seed));
    }

    @Test
    void fullPlannerReportsOneSeedWhenUpperRequestStartsAtSixtyFour() {
        TestKey seed = new TestKey("seed");
        TestKey target = new TestKey("target");
        ECOAE2PatternVariant grow = new ECOAE2PatternVariant(pattern("grow"), 0, List.of());
        ECOAE2PatternVariant consume = new ECOAE2PatternVariant(pattern("consume"), 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(grow, Map.of(seed, 1L), Map.of(seed, 2L)),
                new ECOPlanningOperation<>(consume, Map.of(seed, 1L), Map.of(target, 1L))
            ),
            Map.of(),
            Map.of(target, 64L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 64L, false, Map.of(), false, false
        );

        var result = ECOPlanningSolver.solve(
            problem, new ECOSolveBudget(10_000, 32, 2, TimeUnit.SECONDS.toNanos(5))
        );
        var plan = ECOAE2PlanAssembler.assemble(snapshot, result).orElseThrow();

        assertEquals(ECOHyperflowResult.Status.MISSING_SOURCES, result.status(),
            () -> "status=" + result.status()
                + " executions=" + result.candidate().executions()
                + " shortfall=" + result.candidate().sourceShortfall()
                + " trace=" + result.cycleTrace());
        assertEquals(64L, result.candidate().executions().get(grow));
        assertEquals(64L, result.candidate().executions().get(consume));
        assertEquals(1L, plan.missingItems().get(seed),
            () -> "missing=" + plan.missingItems() + " used=" + plan.usedItems());
        assertEquals(0L, plan.usedItems().get(seed));
    }

    @Test
    void nestedSelfGrowthAssemblerDoesNotReportSixtyFourSeeds() {
        TestKey seed = new TestKey("seed");
        TestKey target = new TestKey("target");
        ECOAE2PatternVariant growSeed = new ECOAE2PatternVariant(pattern("grow_seed"), 0, List.of());
        ECOAE2PatternVariant growTarget = new ECOAE2PatternVariant(pattern("grow_target"), 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(growSeed, Map.of(seed, 1L), Map.of(seed, 2L)),
                new ECOPlanningOperation<>(growTarget, Map.of(target, 1L, seed, 64L), Map.of(target, 2L))
            ),
            Map.of(),
            Map.of(target, 64L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 64L, false, Map.of(), false, false
        );

        var result = ECOPlanningSolver.solve(
            problem, new ECOSolveBudget(10_000, 32, 2, TimeUnit.SECONDS.toNanos(5))
        );
        var plan = ECOAE2PlanAssembler.assemble(snapshot, result).orElseThrow();

        assertEquals(ECOHyperflowResult.Status.MISSING_SOURCES, result.status(),
            () -> "status=" + result.status()
                + " executions=" + result.candidate().executions()
                + " trace=" + result.cycleTrace());
        assertEquals(1L, plan.missingItems().get(seed),
            () -> "missing=" + plan.missingItems()
                + " used=" + plan.usedItems()
                + " executions=" + result.candidate().executions()
                + " trace=" + result.cycleTrace());
        assertEquals(1L, plan.missingItems().get(target),
            () -> "missing=" + plan.missingItems());
    }

    @Test
    void selfIncreasingPatternMaterializesOneTemplateIntoTwo() {
        TestKey template = new TestKey("upgrade_template");
        TestKey diamond = new TestKey("diamond");
        TestKey netherrack = new TestKey("netherrack");
        var pattern = new FixedPattern(
            List.of(
                new FixedInput(template, 1L),
                new FixedInput(diamond, 7L),
                new FixedInput(netherrack, 1L)
            ),
            template,
            2L
        );
        var assessment = ECOAE2PatternCompatibility.assess(pattern, craftingServiceStub(), null);

        assertTrue(assessment.compatible(), assessment.rejection());
        var expansion = ECOAE2PatternMaterializer.expand(
            pattern, assessment, Map.of(template, 1L), craftingServiceStub(), null
        );

        assertEquals(1, expansion.operations().size());
        var operation = expansion.operations().getFirst();
        assertEquals(Map.of(template, 1L, diamond, 7L, netherrack, 1L), operation.inputs());
        assertEquals(Map.of(template, 2L), operation.outputs());
    }

    @Test
    void selfIncreasingPatternSuppliesThirtyTwoThousandUpperCrafts() {
        TestKey template = new TestKey("upgrade_template");
        TestKey diamond = new TestKey("diamond");
        TestKey netherrack = new TestKey("netherrack");
        TestKey target = new TestKey("target");
        var growPattern = new FixedPattern(
            List.of(
                new FixedInput(template, 1L),
                new FixedInput(diamond, 7L),
                new FixedInput(netherrack, 1L)
            ),
            template,
            2L
        );
        var assessment = ECOAE2PatternCompatibility.assess(growPattern, craftingServiceStub(), null);
        var grow = ECOAE2PatternMaterializer.expand(
            growPattern, assessment, Map.of(template, 64L), craftingServiceStub(), null
        ).operations().getFirst();
        IPatternDetails upperPattern = pattern("upper");
        ECOAE2PatternVariant upper = new ECOAE2PatternVariant(upperPattern, 0, List.of());
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(
                new ECOPlanningOperation<>(upper, Map.of(template, 1L), Map.of(target, 1L)),
                grow
            ),
            Map.of(template, 64L, diamond, 228_928L, netherrack, 32_704L),
            Map.of(target, 32_768L)
        );
        var snapshot = new ECOAE2PlanningSnapshot(
            problem, target, 32_768L, false, Map.of(), false, false
        );

        var result = ECOPlanningSolver.solve(
            problem, new ECOSolveBudget(100_000, 64, 5, TimeUnit.SECONDS.toNanos(10))
        );
        var plan = ECOAE2PlanAssembler.assemble(snapshot, result).orElseThrow();

        assertEquals(ECOHyperflowResult.Status.COMPLETE, result.status());
        assertEquals(32_704L, plan.patternTimes().get(growPattern));
        assertEquals(32_768L, plan.patternTimes().get(upperPattern));
        assertEquals(0L, plan.missingItems().get(template));
    }

    @Test
    void requestedInventoryRemainsReachableAsACycleSeed() throws Exception {
        ECOPlanningOperation<String, String> grow = new ECOPlanningOperation<>(
            "grow", Map.of("target", 1L), Map.of("target", 2L)
        );
        ECOPlanningProblem<String, String> problem = new ECOPlanningProblem<>(
            List.of(grow), Map.of("target", 1L), Map.of("target", 4L)
        );
        ECOPlanningGraph<String, String> graph = new ECOPlanningGraph<>(problem.operations());
        Method route = ECOPlanningSolver.class.getDeclaredMethod(
            "inventoryExecutableTargetRoute", ECOPlanningProblem.class, ECOPlanningGraph.class
        );
        route.setAccessible(true);

        Object inventoryRoute = route.invoke(null, problem, graph);
        Method reachesTarget = inventoryRoute.getClass().getDeclaredMethod("reachesTarget");
        reachesTarget.setAccessible(true);

        assertTrue((boolean) reachesTarget.invoke(inventoryRoute));
    }

    @Test
    void optimizesAcrossAllProducersInAnAcyclicGraph() {
        ECOPlanningOperation<String, String> indirectTarget = new ECOPlanningOperation<>(
            "indirect_target", Map.of("intermediate", 1L), Map.of("target", 1L)
        );
        ECOPlanningOperation<String, String> makeIntermediate = new ECOPlanningOperation<>(
            "make_intermediate", Map.of("ore", 1L), Map.of("intermediate", 1L)
        );
        ECOPlanningOperation<String, String> directTarget = new ECOPlanningOperation<>(
            "direct_target", Map.of("ore", 1L), Map.of("target", 1L)
        );
        ECOPlanningProblem<String, String> problem = new ECOPlanningProblem<>(
            List.of(indirectTarget, makeIntermediate, directTarget),
            Map.of("ore", 1L),
            Map.of("target", 1L)
        );

        ECOHyperflowResult<String> result = ECOPlanningSolver.solve(
            problem, new ECOSolveBudget(10_000, 32, 2, TimeUnit.SECONDS.toNanos(5))
        );

        assertEquals(ECOHyperflowResult.Status.COMPLETE, result.status());
        assertEquals(Map.of("direct_target", 1L), result.candidate().executions());
    }

    @Test
    void configuredFuzzyItemRejectsComponentDependentRemainders() {
        ComponentKey template = new ComponentKey("tool", 0);
        ComponentKey storedVariant = new ComponentKey("tool", 1);
        ComponentKey container = new ComponentKey("container", 0);
        var pattern = new FuzzyPattern(
            new FuzzyInput(template, Map.of(storedVariant, container))
        );
        var assessment = ECOAE2PatternCompatibility.assess(pattern, craftingServiceStub(), null);

        var rejection = org.junit.jupiter.api.Assertions.assertThrows(
            ECOAE2PatternMaterializer.PatternRejection.class,
            () -> ECOAE2PatternMaterializer.expand(
                pattern,
                assessment,
                Map.of(storedVariant, 1L),
                craftingServiceStub(),
                null,
                Set.of(template.getId())
            )
        );

        assertTrue(rejection.context().contains("configured_fuzzy_remaining_key_mismatch"));
    }

    @Test
    void uselessDynamicPatternOptsIntoComponentVariantPlanning() {
        ComponentKey template = new ComponentKey("tool", 0);
        ComponentKey storedVariant = new ComponentKey("tool", 1);
        var pattern = new UselessDynamicPattern(new DynamicInput(template));
        var assessment = ECOAE2PatternCompatibility.assess(pattern, craftingServiceStub(), null);

        assertTrue(assessment.compatible(), assessment.rejection());
        assertEquals(
            cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern.InputSemantics
                .MIXABLE_ALTERNATIVES,
            assessment.inputSemantics()
        );
        var expansion = ECOAE2PatternMaterializer.expand(
            pattern,
            assessment,
            Map.of(storedVariant, 4L),
            craftingServiceStub(),
            null
        );

        assertTrue(expansion.operations().stream()
            .anyMatch(operation -> operation.inputs().containsKey(storedVariant)));
    }

    @Test
    void legacyUselessDynamicPatternWithoutTagMethodsRemainsCompatible() {
        ComponentKey template = new ComponentKey("legacy_tool", 0);
        var pattern = new LegacyUselessDynamicPattern(new DynamicInput(template));

        var assessment = ECOAE2PatternCompatibility.assess(pattern, craftingServiceStub(), null);

        assertTrue(assessment.compatible(), assessment.rejection());
    }

    @Test
    void usedItemsSaturateUnlimitedOutputs() {
        ComponentKey input = new ComponentKey("honeycomb", 0);
        ComponentKey honey = new ComponentKey("honey", 0);
        ComponentKey wax = new ComponentKey("wax", 0);
        ComponentKey output = new ComponentKey("redstone", 0);
        ECOAE2PatternVariant variant = new ECOAE2PatternVariant(pattern("omniversal"), 0, List.of());
        var operation = new ECOPlanningOperation<AEKey, ECOAE2PatternVariant>(
            variant,
            Map.of(input, 4L),
            Map.of(honey, 400L, wax, 4L, output, 3L)
        );
        var problem = new ECOPlanningProblem<AEKey, ECOAE2PatternVariant>(
            List.of(operation),
            Map.of(input, Long.MAX_VALUE, honey, Long.MAX_VALUE, wax, 3_971_846_019L),
            Map.of(output, 1_000L),
            Set.of(input, honey)
        );
        var candidate = new ECOPlanCandidate<>(Map.of(variant, 334L), 0L, 0L, 0L, 0L);
        var steps = List.<cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduleEntry<ECOAE2PatternVariant>>of(
            new cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOScheduledStep<>(variant, 334L)
        );

        var used = ECOAE2PlanAssembler.calculateUsedItems(problem, candidate, Map.of(), steps)
            .orElseThrow();

        assertEquals(1_336L, used.get(input));
        assertEquals(0L, used.get(honey));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fuzzyUnlimitedVariantDominatesFiniteVariantsWithoutOverflow() throws Exception {
        ComponentKey unlimited = new ComponentKey("fuzzy_unlimited", 1);
        ComponentKey finite = new ComponentKey("fuzzy_unlimited", 2);
        Map<ResourceLocation, AEKey> representatives = new LinkedHashMap<>();
        representatives.put(unlimited.getId(), unlimited);
        Method normalizeUnlimited = ECOAE2SnapshotFactory.class.getDeclaredMethod(
            "normalizePlannerUnlimitedInventory", Set.class, Map.class, Set.class
        );
        normalizeUnlimited.setAccessible(true);
        Set<AEKey> normalizedUnlimited = (Set<AEKey>) normalizeUnlimited.invoke(
            null, Set.of(unlimited), representatives, Set.of(unlimited.getId())
        );
        Method normalizeInventory = ECOAE2SnapshotFactory.class.getDeclaredMethod(
            "normalizePlannerInventory", Map.class, Set.class, Map.class, Set.class
        );
        normalizeInventory.setAccessible(true);

        Map<AEKey, Long> normalized = (Map<AEKey, Long>) normalizeInventory.invoke(
            null,
            Map.of(unlimited, Long.MAX_VALUE, finite, 7L),
            normalizedUnlimited,
            representatives,
            Set.of(unlimited.getId())
        );

        assertEquals(1, normalized.size());
        assertEquals(Long.MAX_VALUE, normalized.values().iterator().next());
    }

    @Test
    void graphCacheKeysIncludeLevelIdentity() throws Exception {
        for (String name : List.of("GraphKey", "InventoryGraphKey")) {
            Class<?> key = Class.forName(ECOAE2SnapshotFactory.class.getName() + "$" + name);
            assertTrue(java.util.Arrays.stream(key.getRecordComponents())
                .anyMatch(component -> component.getName().equals("level")));
        }
    }

    private static IPatternDetails pattern(String id) {
        return (IPatternDetails) Proxy.newProxyInstance(
            ECOAE2PlanAssemblerTest.class.getClassLoader(),
            new Class<?>[] { IPatternDetails.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "test:" + id;
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static ICraftingService craftingServiceStub() {
        return (ICraftingService) Proxy.newProxyInstance(
            ECOAE2PlanAssemblerTest.class.getClassLoader(),
            new Class<?>[] { ICraftingService.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private record FixedInput(AEKey key, long amount) implements IPatternDetails.IInput {
        @Override
        public appeng.api.stacks.GenericStack[] getPossibleInputs() {
            return new appeng.api.stacks.GenericStack[] {
                new appeng.api.stacks.GenericStack(key, amount)
            };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.equals(input);
        }

        @Override
        public AEKey getRemainingKey(AEKey input) {
            return null;
        }
    }

    private record FixedPattern(List<FixedInput> inputs, AEKey output, long outputAmount)
        implements IPatternDetails, cn.dancingsnow.neoecoae.api.crafting.IECOPlannerCompatiblePattern {
        @Override
        public InputSemantics getECOPlannerInputSemantics() {
            return InputSemantics.CANONICAL_ONLY;
        }

        @Override
        public IInput[] getInputs() {
            return inputs.toArray(IInput[]::new);
        }

        @Override
        public List<appeng.api.stacks.GenericStack> getOutputs() {
            return List.of(new appeng.api.stacks.GenericStack(output, outputAmount));
        }

        @Override
        public appeng.api.stacks.AEItemKey getDefinition() {
            return null;
        }
    }

    private record FuzzyInput(ComponentKey template, Map<AEKey, AEKey> remainders)
        implements IPatternDetails.IInput {
        @Override
        public appeng.api.stacks.GenericStack[] getPossibleInputs() {
            return new appeng.api.stacks.GenericStack[] {
                new appeng.api.stacks.GenericStack(template, 1L)
            };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return template.equals(input);
        }

        @Override
        public AEKey getRemainingKey(AEKey input) {
            return remainders.get(input);
        }
    }

    private record FuzzyPattern(FuzzyInput input)
        implements IPatternDetails, IECOPlannerInputPolicy {
        @Override
        public MatchMode getPlannerInputMatchMode(int slot, IInput input) {
            return MatchMode.STRICT;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { input };
        }

        @Override
        public appeng.api.stacks.GenericStack getPrimaryOutput() {
            return getOutputs().getFirst();
        }

        @Override
        public List<appeng.api.stacks.GenericStack> getOutputs() {
            return List.of(new appeng.api.stacks.GenericStack(new ComponentKey("product", 0), 1L));
        }

        @Override
        public appeng.api.stacks.AEItemKey getDefinition() {
            return null;
        }
    }

    private record DynamicInput(ComponentKey template) implements IPatternDetails.IInput {
        @Override
        public appeng.api.stacks.GenericStack[] getPossibleInputs() {
            return new appeng.api.stacks.GenericStack[] {
                new appeng.api.stacks.GenericStack(template, 1L)
            };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input instanceof ComponentKey key && template.item.equals(key.item);
        }

        @Override
        public AEKey getRemainingKey(AEKey input) {
            return null;
        }
    }

    private record UselessDynamicPattern(DynamicInput input) implements IPatternDetails {
        @Override
        public IInput[] getInputs() {
            return new IInput[] { input };
        }

        @Override
        public List<appeng.api.stacks.GenericStack> getOutputs() {
            return List.of(new appeng.api.stacks.GenericStack(new ComponentKey("product", 0), 1L));
        }

        @Override
        public appeng.api.stacks.AEItemKey getDefinition() {
            return null;
        }

        public String dynamicPatternIdentity() {
            return "useless_mod:test";
        }

        public boolean isItemIdInput(int slot) {
            return slot == 0;
        }

        public boolean isTagInput(int slot) {
            return false;
        }

        public boolean isFluidTagInput(int slot) {
            return false;
        }

        public boolean isItemIdOutput(int slot) {
            return false;
        }

        public boolean usesDynamicOutputs() {
            return false;
        }
    }

    private record LegacyUselessDynamicPattern(DynamicInput input) implements IPatternDetails {
        @Override
        public IInput[] getInputs() {
            return new IInput[] { input };
        }

        @Override
        public List<appeng.api.stacks.GenericStack> getOutputs() {
            return List.of(new appeng.api.stacks.GenericStack(new ComponentKey("product", 0), 1L));
        }

        @Override
        public appeng.api.stacks.AEItemKey getDefinition() {
            return null;
        }

        public String dynamicPatternIdentity() {
            return "useless_mod:legacy_test";
        }

        public boolean isItemIdInput(int slot) {
            return slot == 0;
        }

        public boolean isItemIdOutput(int slot) {
            return false;
        }

        public boolean usesDynamicOutputs() {
            return false;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class TestKey extends AEKey {
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return AEKeyType.items();
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", id);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }
    }

    private static final class ComponentKey extends AEKey {
        private final String item;
        private final int state;

        private ComponentKey(String item, int state) {
            this.item = item;
            this.state = state;
        }

        @Override
        public AEKeyType getType() {
            return AEKeyType.items();
        }

        @Override
        public AEKey dropSecondary() {
            return new ComponentKey(item, 0);
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return item;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", item);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(item + ":" + state);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return state != 0;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ComponentKey key && item.equals(key.item) && state == key.state;
        }

        @Override
        public int hashCode() {
            return 31 * item.hashCode() + state;
        }
    }
}
