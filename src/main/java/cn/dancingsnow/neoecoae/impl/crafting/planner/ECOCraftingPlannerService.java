package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.UnsupportedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.List;

public final class ECOCraftingPlannerService {
    private final CraftingNetworkCompiler compiler = new CraftingNetworkCompiler();
    private final CraftingGraphBuilder graphBuilder = new CraftingGraphBuilder();
    private final TarjanSccAnalyzer sccAnalyzer = new TarjanSccAnalyzer();
    private final ComponentPlanner componentPlanner = new ComponentPlanner(
        new AcyclicCraftingSolver(), new UnsupportedCycleSolver());
    private final AE2CraftingPlanBridge bridge = new AE2CraftingPlanBridge();

    /** Per-calculation session: structural compilation is reused by all AE2 CRAFT_LESS probes. */
    public final class Session {
        private final ICraftingService craftingService;
        private final AEKey goal;
        private final KeyCounter inventory;
        private final boolean cyclePlanningEnabled;
        private CompiledNetwork compiled;
        private CondensationGraph condensation;

        private Session(ICraftingService craftingService, AEKey goal, KeyCounter inventory,
                boolean cyclePlanningEnabled) {
            this.craftingService = craftingService;
            this.goal = goal;
            this.inventory = copy(inventory);
            this.cyclePlanningEnabled = cyclePlanningEnabled;
        }

        public ECOPlanningResult plan(long amount, boolean simulation, ECOCancellation cancellation)
                throws InterruptedException {
            long startedNanos = System.nanoTime();
            try {
                if (compiled == null) compiled = compiler.compile(craftingService, goal, cancellation);
                if (condensation == null) {
                    var graph = graphBuilder.build(compiled, cancellation);
                    var sccs = sccAnalyzer.analyze(graph, cancellation);
                    condensation = CondensationGraph.build(graph, sccs, cancellation);
                }
                var solved = componentPlanner.plan(compiled, condensation, inventory, amount,
                    cyclePlanningEnabled, cancellation);
                boolean multiplePaths = compiled.producers().values().stream().anyMatch(list -> list.size() > 1);
                var plan = switch (solved.status()) {
                    case SUCCESS, MISSING_ITEMS -> bridge.success(goal, amount,
                        simulation || solved.status() != PlanningStatus.SUCCESS, multiplePaths, solved.state());
                    case PARTIAL, CYCLE_UNRESOLVED -> bridge.partial(goal, amount, multiplePaths, solved.state());
                    case CYCLE_UNSUPPORTED, CANCELLED, AMOUNT_OVERFLOW -> bridge.unsupported(goal, amount);
                    default -> null;
                };
                var result = new ECOPlanningResult(solved.status(), plan, solved.trace(), solved.cycles(),
                    solved.components(),
                    elapsedSince(startedNanos));
                attach(result);
                return result;
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                ECOPlanTrace trace = new ECOPlanTrace();
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.INTERNAL_ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
                return new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, trace, List.of(), List.of(),
                    elapsedSince(startedNanos));
            }
        }

        private void attach(ECOPlanningResult result) {
            if ((Object) result.plan() instanceof ECOCraftingPlanDiagnostics diagnostics) {
                diagnostics.neoecoae$setPlanningResult(result);
            }
        }

        private long elapsedSince(long startedNanos) {
            long now = System.nanoTime();
            return now >= startedNanos ? now - startedNanos : 0L;
        }
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory) {
        return new Session(service, goal, inventory, false);
    }

    public Session createSession(ICraftingService service, AEKey goal, KeyCounter inventory,
            boolean cyclePlanningEnabled) {
        return new Session(service, goal, inventory, cyclePlanningEnabled);
    }

    private static KeyCounter copy(KeyCounter source) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) if (entry.getLongValue() > 0) result.add(entry.getKey(), entry.getLongValue());
        return result;
    }
}
