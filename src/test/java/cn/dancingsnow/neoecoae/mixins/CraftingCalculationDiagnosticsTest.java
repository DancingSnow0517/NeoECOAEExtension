package cn.dancingsnow.neoecoae.mixins;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class CraftingCalculationDiagnosticsTest {
    private static final BigInteger WIDE = BigInteger.TEN.pow(24);

    @Test
    void nativeFailureDoesNotSkipTheFollowingExactSimulation() throws Exception {
        var calculation = new Calculation();
        var goal = new TestKey();
        var session = new ECOCraftingPlannerService().createSession(null, goal, new KeyCounter());
        // Use a compiled no-provider graph to exercise the real solver without a Forge world or registry.
        var network = new CompiledNetwork(goal, Map.of(goal, List.of()), Set.of(), 0, 0);
        var graph = new CraftingGraphBuilder().build(network, () -> {});
        var components = new TarjanSccAnalyzer().analyze(graph, () -> {});
        setSessionField(session, "compiled", network);
        setSessionField(session, "condensation", CondensationGraph.build(graph, components, () -> {}));
        setField(calculation, "neoecoae$plannerSession", session);

        var realAttempt = new CallbackInfoReturnable<CraftingPlan>("runCraftAttempt", true);
        runAttempt(calculation, false, realAttempt);
        ECOPlanningResult first = calculation.neoecoae$getLastPlanningResult();
        assertNotNull(first);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, first.status());
        assertFalse(realAttempt.isCancelled(), "The compatibility planner must still run");

        // AE2 retries with simulate=true after the native non-simulated attempt returns null.
        finishAttempt(calculation, false, new CallbackInfoReturnable<CraftingPlan>("runCraftAttempt", false, null));
        var simulation = new CallbackInfoReturnable<CraftingPlan>("runCraftAttempt", true);
        runAttempt(calculation, true, simulation);

        ECOPlanningResult simulated = calculation.neoecoae$getLastPlanningResult();
        assertNotSame(first, simulated, "The simulated attempt must carry its own exact report");
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, simulated.status());
        assertEquals(new BigInteger("73786976294838206464"), simulated.theoreticalBytes());
        assertFalse(simulation.isCancelled());
    }

    @Test
    void simulatedFallbackRetainsWideBytesAndMaterialAmounts() throws Exception {
        var calculation = new Calculation();
        var goal = new TestKey();
        var diagnostic = wideDiagnostic(goal);
        setField(calculation, "neoecoae$lastPlanningResult", diagnostic);
        ICraftingPlan nativePlan = diagnosticPlan(goal, 1L, true);

        finishCalculation(calculation, nativePlan);

        var attached = ((ECOCraftingPlanDiagnostics) nativePlan).neoecoae$getPlanningResult();
        assertSame(diagnostic, attached);
        assertEquals(WIDE, HostText.craftingPlanBytes(attached.theoreticalBytes(), nativePlan.bytes()));
        var material = CraftingGraphSnapshotFactory.create(attached).nodes().get(0);
        assertEquals(WIDE, material.missingBigInteger());
        assertEquals(Long.MAX_VALUE, material.missing());
        assertTrue(nativePlan.simulation(), "Report recovery must not turn a missing plan into an executable plan");
        assertNull(ECOPlanningResultRegistry.find(nativePlan), "Fallback diagnostics are not execution metadata");
    }

    @Test
    void finalPlanCannotInheritTheReportForADifferentRequestedAmount() throws Exception {
        var calculation = new Calculation();
        var goal = new TestKey();
        setField(calculation, "neoecoae$lastPlanningResult", wideDiagnostic(goal));
        ICraftingPlan differentRequest = diagnosticPlan(goal, 2L, false);

        finishCalculation(calculation, differentRequest);

        assertNull(((ECOCraftingPlanDiagnostics) differentRequest).neoecoae$getPlanningResult());
    }

    private static ECOPlanningResult wideDiagnostic(AEKey goal) {
        var trace = new ECOPlanTrace();
        trace.addNode(new PlanTraceNode(
                        PlanTraceNode.Kind.GOAL,
                        goal,
                        null,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        PlanTraceNode.Selection.NOT_APPLICABLE,
                        null)
                .withExact(WIDE, BigInteger.ZERO, BigInteger.ZERO, WIDE, BigInteger.ZERO));
        var shell = new CraftingPlan(
                new GenericStack(goal, 1L),
                0L,
                true,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
        var result = new ECOPlanningResult(
                PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, shell, trace, List.of(), 2_000_000L);
        result.setTheoreticalBytes(PlannerAmount.of(WIDE));
        return result;
    }

    private static ICraftingPlan diagnosticPlan(AEKey goal, long amount, boolean simulation) {
        var missing = new KeyCounter();
        missing.set(goal, Long.MAX_VALUE);
        var delegate = new CraftingPlan(
                new GenericStack(goal, amount),
                Long.MAX_VALUE,
                simulation,
                false,
                new KeyCounter(),
                new KeyCounter(),
                missing,
                Map.of());
        var report = new AtomicReference<ECOPlanningResult>();
        return (ICraftingPlan) Proxy.newProxyInstance(
                CraftingCalculationDiagnosticsTest.class.getClassLoader(),
                new Class<?>[] {ICraftingPlan.class, ECOCraftingPlanDiagnostics.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("neoecoae$getPlanningResult")) return report.get();
                    if (method.getName().equals("neoecoae$setPlanningResult")) {
                        report.set((ECOPlanningResult) args[0]);
                        return null;
                    }
                    return method.invoke(delegate, args);
                });
    }

    private static void runAttempt(
            Calculation calculation, boolean simulate, CallbackInfoReturnable<CraftingPlan> result) throws Exception {
        var hook = CraftingCalculationMixin.class.getDeclaredMethod(
                "runEcoDagAttempt", boolean.class, long.class, CallbackInfoReturnable.class);
        hook.setAccessible(true);
        hook.invoke(calculation, simulate, Long.MAX_VALUE, result);
    }

    private static void finishAttempt(
            Calculation calculation, boolean simulate, CallbackInfoReturnable<CraftingPlan> result) throws Exception {
        var hook = CraftingCalculationMixin.class.getDeclaredMethod(
                "attachEcoDiagnosticToNativePlan", boolean.class, long.class, CallbackInfoReturnable.class);
        hook.setAccessible(true);
        hook.invoke(calculation, simulate, Long.MAX_VALUE, result);
    }

    private static void finishCalculation(Calculation calculation, ICraftingPlan result) throws Exception {
        var nativeCalls = new AtomicInteger();
        Operation<ICraftingPlan> original = args -> {
            nativeCalls.incrementAndGet();
            return result;
        };
        var hook = CraftingCalculationMixin.class.getDeclaredMethod(
                "attachEcoDiagnosticToFinalPublicPlan", Operation.class);
        hook.setAccessible(true);
        assertSame(result, hook.invoke(calculation, original));
        assertEquals(1, nativeCalls.get());
    }

    private static void setField(Calculation calculation, String name, Object value) throws Exception {
        var field = CraftingCalculationMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(calculation, value);
    }

    private static void setSessionField(ECOCraftingPlannerService.Session session, String name, Object value)
            throws Exception {
        var field = ECOCraftingPlannerService.Session.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(session, value);
    }

    private static final class Calculation extends CraftingCalculationMixin {
        @Override
        void handlePausing() {}
    }

    private static final class TestKey extends AEKey {
        private static final AEKeyType TYPE =
                new AEKeyType(
                        ResourceLocation.fromNamespaceAndPath("test", "diagnostic"), TestKey.class, Component.empty()) {
                    @Override
                    public int getAmountPerByte() {
                        return 1;
                    }

                    @Override
                    public AEKey readFromPacket(FriendlyByteBuf buffer) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public AEKey loadKeyFromTag(CompoundTag tag) {
                        throw new UnsupportedOperationException();
                    }
                };

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", "diagnostic");
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("diagnostic");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
