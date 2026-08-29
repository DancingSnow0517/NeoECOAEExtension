package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.CycleDetector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.ReachabilityScanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.List;

public final class ECOCraftingPlannerService {
    private final CraftingNetworkCompiler compiler = new CraftingNetworkCompiler();
    private final ReachabilityScanner reachability = new ReachabilityScanner();
    private final CycleDetector cycleDetector = new CycleDetector();
    private final AcyclicCraftingSolver solver = new AcyclicCraftingSolver();
    private final AE2CraftingPlanBridge bridge = new AE2CraftingPlanBridge();

    /** Per-calculation session: structural compilation is reused by all AE2 CRAFT_LESS probes. */
    public final class Session {
        private final ICraftingService craftingService;
        private final AEKey goal;
        private final KeyCounter inventory;
        private CompiledNetwork compiled;

        private Session(ICraftingService craftingService, AEKey goal, KeyCounter inventory) {
            this.craftingService = craftingService;
            this.goal = goal;
            this.inventory = copy(inventory);
        }

        public ECOPlanningResult plan(long amount, boolean simulation, ECOCancellation cancellation)
                throws InterruptedException {
            long startedNanos = System.nanoTime();
            try {
                if (compiled == null) compiled = compiler.compile(craftingService, goal, cancellation);
                var reachable = reachability.scan(compiled, cancellation);
                var cycleResult = cycleDetector.detect(compiled, reachable, cancellation);
                if (cycleResult.cyclic()) {
                    var cycles = cycleResult.cycles().stream()
                        .map(cycle -> cycle.withAvailableAmounts(inventory))
                        .toList();
                    ECOPlanTrace trace = new ECOPlanTrace();
                    for (var cycle : cycles) {
                        trace.addCycle(cycle);
                        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.CYCLE_GROUP, null, null, 0, 0, 0, 0, 0,
                            PlanTraceNode.Selection.UNSUPPORTED, "UNSUPPORTED_CYCLE"));
                    }
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_UNSUPPORTED,
                        "检测到循环配方，当前版本暂不支持循环规划。"));
                    var result = new ECOPlanningResult(PlanningStatus.CYCLE_UNSUPPORTED,
                        bridge.unsupported(goal, amount), trace, cycles, elapsedSince(startedNanos));
                    attach(result);
                    return result;
                }
                var solved = solver.solve(compiled, cycleResult.route(), inventory, amount, cancellation);
                boolean multiplePaths = compiled.producers().values().stream().anyMatch(list -> list.size() > 1);
                var plan = switch (solved.status()) {
                    case SUCCESS, MISSING_ITEMS -> bridge.success(goal, amount,
                        simulation || solved.status() != PlanningStatus.SUCCESS, multiplePaths, solved.state());
                    case CYCLE_UNSUPPORTED, CANCELLED, AMOUNT_OVERFLOW -> bridge.unsupported(goal, amount);
                    default -> null;
                };
                var result = new ECOPlanningResult(solved.status(), plan, solved.trace(), List.of(),
                    elapsedSince(startedNanos));
                attach(result);
                return result;
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                ECOPlanTrace trace = new ECOPlanTrace();
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.INTERNAL_ERROR,
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
                return new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, trace, List.of(),
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
        return new Session(service, goal, inventory);
    }

    private static KeyCounter copy(KeyCounter source) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) if (entry.getLongValue() > 0) result.add(entry.getKey(), entry.getLongValue());
        return result;
    }
}
