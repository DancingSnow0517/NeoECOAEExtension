package cn.dancingsnow.neoecoae.command;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.integration.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledStructureCache;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CycleStructure;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.trace.QuantityBoundTrace;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * One-shot export of the network's patterns and the next crafting plan a player calculates, as compact text
 * intended to be read by people or language models. Read-only: it never touches the plan, stock or providers.
 */
public final class CraftingPlanExport {
    private static final Set<UUID> ARMED = ConcurrentHashMap.newKeySet();
    private static final int MAX_DIAGNOSTIC_CODES = 40;
    private static final int MAX_MESSAGE = 220;
    private static final int MAX_LISTED = 200;
    private static final int MAX_SURPLUS = 40;
    private static final int MAX_LOOPS = 4;

    private CraftingPlanExport() {}

    public static void arm(ServerPlayer player) {
        ARMED.add(player.getUUID());
    }

    public static boolean isArmed(ServerPlayer player) {
        return ARMED.contains(player.getUUID());
    }

    /** Called on the server thread when a craft-confirm menu receives a calculated plan. */
    public static void onPlanReady(ServerPlayer player, IGrid grid, ICraftingPlan plan,
            @Nullable ECOPlanningResult result, @Nullable ECOPlannerOptions options) {
        onPlanReady(player, grid, plan, result, options, null);
    }

    public static void onPlanReady(ServerPlayer player, IGrid grid, ICraftingPlan plan,
            @Nullable ECOPlanningResult result, @Nullable ECOPlannerOptions options, @Nullable PlannerInventorySnapshot stock) {
        if (plan == null || grid == null || !ARMED.remove(player.getUUID())) return;
        try {
            Path dir = player.server.getWorldPath(LevelResource.ROOT).resolve("eco-plan-exports");
            Files.createDirectories(dir);
            Path file = dir.resolve("plan-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4) + ".txt");
            Files.writeString(file, render(grid, plan, result, options, stock));
            player.sendSystemMessage(Component.literal("ECO plan exported: " + file.toAbsolutePath()));
        } catch (IOException | RuntimeException e) {
            player.sendSystemMessage(Component.literal("ECO plan export failed: " + e));
        }
    }

    static String render(IGrid grid, ICraftingPlan plan, @Nullable ECOPlanningResult result,
            @Nullable ECOPlannerOptions options, @Nullable PlannerInventorySnapshot stock) {
        return new Report(grid, plan, result, options, stock).render();
    }

    private static final class Report {
        private final IGrid grid;
        private final ICraftingPlan plan;
        private final @Nullable ECOPlanningResult result;
        private final @Nullable ECOPlannerOptions options;
        private final GenericStack goal;
        private final KeyNames names = new KeyNames();
        private final Map<IPatternDetails, String> ids = new LinkedHashMap<>();
        private final Map<AEKey, List<IPatternDetails>> producerOrder = new LinkedHashMap<>();
        private final Set<AEKey> consumedByNetwork = new HashSet<>();
        private final Map<IPatternDetails, PlannerAmount> tasks = new LinkedHashMap<>();
        private final Map<AEKey, PlannerAmount> used;
        private final Map<AEKey, PlannerAmount> missing;
        private final Map<AEKey, PlannerAmount> emitted;
        private final Map<AEKey, PlannerAmount> produced = new HashMap<>();
        private final Map<AEKey, PlannerAmount> consumed = new HashMap<>();
        private final Map<AEKey, Boolean> producedOnlyAsByproduct = new HashMap<>();
        private final @Nullable PlannerInventorySnapshot stock;
        private final List<QuantityBoundTrace.Node> proofNodes;

        Report(IGrid grid, ICraftingPlan plan, @Nullable ECOPlanningResult result, @Nullable ECOPlannerOptions options,
                @Nullable PlannerInventorySnapshot stock) {
            this.grid = grid;
            this.plan = plan;
            this.result = result;
            this.options = options;
            this.stock = stock;
            var proof = result == null ? null : result.trace().quantityBound();
            this.proofNodes = proof == null ? List.of() : proof.nodes();
            this.goal = plan.finalOutput();
            collectPatterns();
            collectTasks();
            ECOExactCraftingPlan exact = plan instanceof ECOExactCraftingPlan exactPlan ? exactPlan : null;
            this.used = materials(result == null ? null : result.exactUsedItems(), plan.usedItems(),
                exact == null ? Map.of() : exact.deferredStock());
            this.missing = materials(result == null ? null : result.exactMissingItems(), plan.missingItems(), Map.of());
            this.emitted = materials(result == null ? null : result.exactEmittedItems(), plan.emittedItems(),
                exact == null ? Map.of() : exact.deferredEmitted());
            balance();
        }

        private void collectPatterns() {
            ICraftingService service = grid.getCraftingService();
            var all = new ArrayList<IPatternDetails>();
            var seen = new HashSet<IPatternDetails>();
            for (AEKey key : service.getCraftables(k -> true)) {
                var producers = new ArrayList<IPatternDetails>();
                for (IPatternDetails pattern : service.getCraftingFor(key)) {
                    if (!producers.contains(pattern)) producers.add(pattern);
                    if (seen.add(pattern)) all.add(pattern);
                }
                producerOrder.put(key, producers);
            }
            all.sort(Comparator.comparing((IPatternDetails p) -> names.of(p.getPrimaryOutput().what()))
                .thenComparing(p -> p.getOutputs().length));
            for (IPatternDetails pattern : plan.patternTimes().keySet()) {
                if (seen.add(pattern)) all.add(pattern);
            }
            if (result != null) {
                for (IPatternDetails pattern : result.exactPatternTimes().keySet()) {
                    if (seen.add(pattern)) all.add(pattern);
                }
                for (var component : result.components()) for (IPatternDetails pattern : component.patterns()) {
                    if (seen.add(pattern)) all.add(pattern);
                }
            }
            // Providers may have changed since planning. Keep the identity of every route cited by the proof.
            for (var node : proofNodes) for (var route : node.routes()) {
                if (seen.add(route.pattern())) all.add(route.pattern());
            }
            for (IPatternDetails pattern : all) {
                ids.put(pattern, "P" + (ids.size() + 1));
                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    for (GenericStack possible : input.getPossibleInputs()) consumedByNetwork.add(possible.what());
                }
            }
        }

        private void collectTasks() {
            if (result != null && !result.exactPatternTimes().isEmpty()) {
                tasks.putAll(result.exactPatternTimes());
            } else if (plan instanceof ECOExactCraftingPlan exact) {
                exact.exactTasks().forEach((pattern, amount) -> tasks.put(pattern, PlannerAmount.of(amount)));
            } else {
                plan.patternTimes().forEach((pattern, times) -> tasks.put(pattern, PlannerAmount.of(times)));
            }
        }

        private static Map<AEKey, PlannerAmount> materials(@Nullable Map<AEKey, PlannerAmount> exact, KeyCounter counter,
                Map<AEKey, BigInteger> deferred) {
            var amounts = new LinkedHashMap<AEKey, PlannerAmount>();
            if (exact != null && !exact.isEmpty()) {
                exact.forEach((key, amount) -> {
                    if (amount.signum() > 0) amounts.put(key, amount);
                });
                return amounts;
            }
            for (var entry : counter) {
                amounts.merge(entry.getKey(), PlannerAmount.of(entry.getLongValue()), PlannerAmount::add);
            }
            deferred.forEach((key, amount) -> amounts.merge(key, PlannerAmount.of(amount), PlannerAmount::add));
            amounts.values().removeIf(amount -> amount.signum() <= 0);
            return amounts;
        }

        private void balance() {
            tasks.forEach((pattern, times) -> {
                AEKey primary = pattern.getPrimaryOutput().what();
                for (GenericStack output : pattern.getOutputs()) {
                    produced.merge(output.what(), times.multiply(output.amount()), PlannerAmount::add);
                    boolean byproduct = !output.what().equals(primary);
                    producedOnlyAsByproduct.merge(output.what(), byproduct, Boolean::logicalAnd);
                }
                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible.length == 0) continue;
                    PlannerAmount amount = PlannerAmount.of(possible[0].amount()).multiply(input.getMultiplier());
                    consumed.merge(possible[0].what(), times.multiply(amount), PlannerAmount::add);
                }
            });
        }

        private PlannerAmount surplus(AEKey key) {
            PlannerAmount value = produced.getOrDefault(key, PlannerAmount.ZERO).add(used.getOrDefault(key, PlannerAmount.ZERO))
                .add(emitted.getOrDefault(key, PlannerAmount.ZERO)).subtract(consumed.getOrDefault(key, PlannerAmount.ZERO));
            return key.equals(goal.what()) ? value.subtract(goal.amount()) : value;
        }

        String render() {
            var out = new StringBuilder();
            out.append("# ECO crafting export v2\n");
            out.append("# Sections: ORDER, PLANNER, PATTERNS, PRODUCER ORDER, CRAFTS, USED FROM STORAGE, MISSING, EMITTED,\n");
            out.append("#   SURPLUS, IMBALANCED, CYCLES, QUANTITY BOUND PROOF, INVENTORY, KEYS.\n");
            out.append("# Pattern line: P<id> [type] inputs -> outputs, per single craft; 'a|b' = substitutes sharing an amount unless overridden.\n");
            out.append("# Keys: item id; other types are prefixed (e.g. f: fluid); labels are unique within this file; KEYS shows NBT previews.\n");
            out.append("# Network patterns/order are read at confirmation; stock is not resampled there.\n");
            out.append("# Task balance = declared production + planned stock + emitted supply - first-choice inputs - goal.\n");
            out.append("# This vector ignores substitutions actually chosen, tool/return semantics and execution receipts; not a minimum shopping list.\n");
            appendOrder(out);
            appendPlanner(out);
            appendPatterns(out);
            appendProducerOrder(out);
            appendCrafts(out);
            appendUsed(out);
            appendAmounts(out, "MISSING", missing);
            appendAmounts(out, "EMITTED (planned external supply, not a receipt)", emitted);
            appendSurplus(out);
            appendCycles(out);
            appendQuantityBound(out);
            appendInventory(out);
            if (!names.legend.isEmpty()) {
                out.append("\n## KEYS\n");
                names.legend.forEach((tag, desc) -> out.append(tag).append(" = ").append(desc).append('\n'));
            }
            return out.toString();
        }

        private void appendOrder(StringBuilder out) {
            out.append("\n## ORDER\n");
            out.append("goal: ").append(goal.amount()).append(' ').append(names.of(goal.what())).append('\n');
            out.append("actualWithdrawal: unobserved (confirmation preview)\n");
            out.append("simulation: ").append(plan.simulation())
                .append(plan.simulation() ? " (preview, not an execution receipt)" : "").append('\n');
            if (plan.multiplePaths()) out.append("multiplePaths: true\n");
        }

        private void appendPlanner(StringBuilder out) {
            out.append("\n## PLANNER\n");
            if (options == null) {
                out.append("options: unknown (the request carried no ECO planner options)\n");
            } else {
                out.append("options: cyclePlanning=").append(options.cyclePlanningEnabled())
                    .append(" ignoreSubstitutions=").append(options.ignorePatternSubstitutions())
                    .append(" fuzzyItems=").append(options.fuzzyPlanningItemIds().stream().map(ResourceLocation::toString)
                        .sorted().toList()).append('\n');
            }
            if (result == null) {
                out.append("result: no ECO planning result attached (AE2 or another planner produced this plan)\n");
                return;
            }
            long cyclic = result.components().stream()
                .filter(component -> component.type() == ComponentPlanningResult.Type.CYCLIC).count();
            out.append("result: status=").append(result.status())
                .append(" calculationMs=").append(result.calculationNanos() / 1_000_000L)
                .append(" components=").append(result.components().size()).append(" cyclic=").append(cyclic)
                .append(" (displayed plan)\n");
            var counts = new LinkedHashMap<String, Integer>();
            var first = new LinkedHashMap<String, String>();
            for (PlannerDiagnostic diagnostic : result.trace().diagnostics()) {
                String code = diagnostic.code().name();
                counts.merge(code, 1, Integer::sum);
                first.putIfAbsent(code, diagnostic.message());
            }
            if (!counts.isEmpty()) {
                out.append("diagnostics:\n");
                int listed = 0;
                for (var entry : counts.entrySet()) {
                    if (++listed > MAX_DIAGNOSTIC_CODES) {
                        out.append("  ... ").append(counts.size() - MAX_DIAGNOSTIC_CODES).append(" more codes\n");
                        break;
                    }
                    out.append("  ").append(entry.getKey());
                    if (entry.getValue() > 1) out.append(" x").append(entry.getValue());
                    String message = first.get(entry.getKey());
                    if (message != null && !message.isBlank()) out.append(": ").append(truncate(message));
                    out.append('\n');
                }
            }
            if (cyclic == 0) return;
            out.append("cycle components (requiredSeed = startup requirement, stockReserved = planned allocation; externalMissingAE2 = long projection):\n");
            for (ComponentPlanningResult component : result.components()) {
                if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
                out.append("  C").append(component.componentId()).append(' ').append(component.status())
                    .append(" cycle=").append(component.cycleStatus())
                    .append(" disposition=").append(component.cycleDisposition());
                CycleSolveResult solved = component.cycleResult();
                if (solved != null) {
                    out.append(" solve=").append(solved.status())
                        .append(" firings=").append(solved.executionCountKnowledge() == ExecutionCountKnowledge.EXACT
                                ? solved.plannerTotalFirings().toString() : "unknown");
                }
                out.append(" required=").append(amounts(component.plannerRequiredOutputs()))
                    .append(" stockReserved=").append(amounts(component.plannerStockReservations()));
                if (solved != null) {
                    out.append(" requiredSeed=").append(amounts(solved.exactRequiredSeed()));
                    if (!solved.exactSeedShortfall().isEmpty()) out.append(" seedShortfall=").append(amounts(solved.exactSeedShortfall()));
                    if (!solved.exactExternalDemand().isEmpty()) out.append(" externalDemand=").append(amounts(solved.exactExternalDemand()));
                }
                if (!component.externalMissingItems().isEmpty()) {
                    out.append(" externalMissingAE2=").append(amounts(component.externalMissingItems()));
                }
                out.append(" patterns=").append(idList(component.patterns()));
                if (component.diagnostic() != null && !component.diagnostic().isBlank()) {
                    out.append(" note=").append(truncate(component.diagnostic()));
                }
                out.append('\n');
            }
        }

        private void appendPatterns(StringBuilder out) {
            out.append("\n## PATTERNS (").append(ids.size()).append(")\n");
            ids.forEach((pattern, id) -> out.append(id).append(' ').append(describe(pattern)).append('\n'));
        }

        private void appendProducerOrder(StringBuilder out) {
            var rows = new TreeMap<String, String>();
            producerOrder.forEach((key, producers) -> {
                if (producers.size() < 2) return;
                rows.put(names.of(key), producers.stream().map(pattern -> ids.getOrDefault(pattern, "P?")
                    + (pattern.getPrimaryOutput().what().equals(key) ? "" : "*")).collect(Collectors.joining(" > ")));
            });
            if (rows.isEmpty()) return;
            out.append("\n## PRODUCER ORDER (AE getCraftingFor order, keys with 2+ producers; * = byproduct of that pattern)\n");
            int listed = 0;
            for (var row : rows.entrySet()) {
                if (++listed > MAX_LISTED) {
                    out.append("... ").append(rows.size() - MAX_LISTED).append(" more keys\n");
                    break;
                }
                out.append(row.getKey()).append(": ").append(row.getValue()).append('\n');
            }
        }

        private void appendCrafts(StringBuilder out) {
            out.append("\n## CRAFTS (pattern x times, top-down from goal)\n");
            for (IPatternDetails pattern : orderTopDown()) {
                out.append(ids.getOrDefault(pattern, "P?")).append(" x").append(tasks.get(pattern))
                    .append("  -> ").append(names.of(pattern.getPrimaryOutput().what())).append('\n');
            }
        }

        private String available(AEKey key) {
            if (stock == null) return "unknown";
            return stock.isUnbounded(key) ? "inf" : stock.amountOf(key).toString();
        }

        private void appendUsed(StringBuilder out) {
            if (used.isEmpty()) return;
            out.append("\n## USED FROM STORAGE (planned, not withdrawn; avail from INVENTORY)\n");
            used.entrySet().stream().sorted(Comparator.comparing(entry -> names.of(entry.getKey()))).forEach(entry -> {
                AEKey key = entry.getKey();
                boolean all = stock != null && !stock.isUnbounded(key) && entry.getValue().equals(stock.amountOf(key));
                out.append("used=").append(entry.getValue()).append(" avail=").append(available(key))
                    .append(all ? " (all) " : " ").append(names.of(key)).append('\n');
            });
        }

        private void appendInventory(StringBuilder out) {
            // Includes every named input (also unused substitutes), output, ledger key and proof key, not just USED.
            var keys = new HashSet<>(names.cache.keySet());
            keys.addAll(producerOrder.keySet());
            if (stock != null) {
                keys.addAll(stock.amounts().keySet());
                keys.addAll(stock.unboundedKeys());
            }
            if (result != null) {
                result.trace().nodes().forEach(node -> { if (node.key() != null) keys.add(node.key()); });
                result.trace().edges().forEach(edge -> { keys.add(edge.from()); keys.add(edge.to()); });
                result.trace().components().forEach(component -> keys.addAll(component.members()));
                result.trace().cycles().forEach(cycle -> keys.addAll(cycle.members()));
                result.cycles().forEach(cycle -> keys.addAll(cycle.keys()));
                for (var component : result.components()) {
                    keys.addAll(component.plannerRequiredOutputs().keySet());
                    keys.addAll(component.plannerStockReservations().keySet());
                    keys.addAll(component.externalMissingItems().keySet());
                }
            }
            out.append("\n## INVENTORY (source=").append(stock == null ? "unavailable" : "ECO_REQUEST_START")
                .append("; captureServerTick=").append(stock == null || stock.captureServerTick() == null
                    ? "unknown" : stock.captureServerTick().toString())
                .append("; scope=all snapshot keys + declared pattern I/O + plan/trace keys; keys=").append(keys.size())
                .append("; 0=known absent, inf=explicit unbounded source, unknown=no matching request snapshot)\n");
            keys.stream().sorted(Comparator.comparing(names::of)).forEach(key -> out.append("avail=")
                .append(available(key)).append(' ').append(names.of(key)).append('\n'));
        }

        private void appendQuantityBound(StringBuilder out) {
            if (result == null) return;
            var proof = result.trace().quantityBound();
            if (proof == null) {
                if (result.trace().diagnostics().stream().anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND)) {
                    out.append("\n## QUANTITY BOUND PROOF\nunavailable: detailed proof not captured for this request\n");
                }
                return;
            }
            out.append("\n## QUANTITY BOUND PROOF (optimistic upper bound, not an executable maximum)\n");
            out.append("# upper = stock + sum(firingsUpper * outputPerFiring); a stock-only node has no producers.\n");
            out.append("# firingsUpper = floor(sum(floor(input.upper / units)) / multiplier); compiled slots are zero-based, quantities normalized.\n");
            out.append("goal=").append(names.of(proof.goal().key())).append(" requested=").append(proof.requested())
                .append(" root=B").append(proof.goal().revision()).append('\n');
            for (var node : proofNodes) {
                out.append('B').append(node.revision()).append(" key=").append(names.of(node.key()))
                    .append(" stock=").append(node.stock()).append(" upper=").append(node.upper())
                    .append(node.routes().isEmpty() ? " source=stock-only\n" : " source=routes\n");
                for (var route : node.routes()) {
                    out.append("  route=").append(ids.get(route.pattern())).append(" firingsUpper=").append(route.firingsUpper())
                        .append(" outputPerFiring=").append(route.outputPerFiring()).append(" limitingSlot=").append(route.slot())
                        .append(" multiplier=").append(route.multiplier()).append('\n');
                    for (var input : route.alternatives()) {
                        out.append("    alternative=").append(names.of(input.supply().key())).append(" units=").append(input.units())
                            .append(" bound=B").append(input.supply().revision()).append('\n');
                    }
                }
            }
        }

        private void appendAmounts(StringBuilder out, String title, Map<AEKey, PlannerAmount> amounts) {
            if (amounts.isEmpty()) return;
            var rows = new TreeMap<String, PlannerAmount>();
            amounts.forEach((key, amount) -> rows.merge(names.of(key), amount, PlannerAmount::add));
            out.append("\n## ").append(title).append('\n');
            rows.forEach((name, amount) -> out.append(amount).append(' ').append(name).append('\n'));
        }

        private void appendSurplus(StringBuilder out) {
            var keys = new LinkedHashSet<AEKey>();
            keys.addAll(produced.keySet());
            keys.addAll(used.keySet());
            keys.addAll(consumed.keySet());
            keys.addAll(emitted.keySet());
            keys.add(goal.what());
            var positive = new ArrayList<Map.Entry<AEKey, PlannerAmount>>();
            var negative = new ArrayList<Map.Entry<AEKey, PlannerAmount>>();
            for (AEKey key : keys) {
                PlannerAmount value = surplus(key);
                if (value.signum() > 0) positive.add(Map.entry(key, value));
                else if (value.signum() < 0) negative.add(Map.entry(key, value));
            }
            positive.sort(Map.Entry.<AEKey, PlannerAmount>comparingByValue().reversed());
            if (!positive.isEmpty()) {
                out.append("\n## SURPLUS (positive declared task balance after goal delivery;")
                    .append(" [sink] = no network pattern consumes it, [byproduct] = only made as a secondary output)\n");
                int listed = 0;
                for (var entry : positive) {
                    if (++listed > MAX_SURPLUS) {
                        out.append("... ").append(positive.size() - MAX_SURPLUS).append(" more keys with small remainders\n");
                        break;
                    }
                    AEKey key = entry.getKey();
                    out.append(entry.getValue()).append(' ').append(names.of(key));
                    if (!consumedByNetwork.contains(key)) out.append(" [sink]");
                    if (producedOnlyAsByproduct.getOrDefault(key, false)) out.append(" [byproduct]");
                    out.append('\n');
                }
            }
            if (!negative.isEmpty()) {
                out.append("\n## IMBALANCED (negative declared task balance, shown as positive deficits; not necessarily MISSING)\n");
                negative.sort(Map.Entry.comparingByValue());
                for (var entry : negative) {
                    out.append(entry.getValue().negate()).append(' ').append(names.of(entry.getKey())).append('\n');
                }
            }
        }

        private void appendCycles(StringBuilder out) {
            CondensationGraph condensation;
            String source;
            try {
                ICraftingService service = grid.getCraftingService();
                boolean cycles = options == null || options.cyclePlanningEnabled();
                boolean lite = result != null ? result.liteCraftingMode() : options != null && options.ignorePatternSubstitutions();
                Set<ResourceLocation> fuzzy = options == null ? Set.of() : options.fuzzyPlanningItemIds();
                var cached = CompiledStructureCache.get(service, CompiledStructureCache.revision(service),
                    AE2PatternIntrospection.reloadGeneration(), goal.what(), cycles, lite, fuzzy);
                if (cached != null) {
                    condensation = cached.condensation();
                    source = "cached planner structure";
                } else {
                    var network = new CraftingNetworkCompiler().compile(service, goal.what(), cycles, lite, fuzzy,
                        ECOCancellation.NONE);
                    var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
                    condensation = CondensationGraph.build(graph,
                        new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
                    source = "recompiled planner structure";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.append("\n## CYCLES\nunavailable: interrupted\n");
                return;
            } catch (RuntimeException e) {
                out.append("\n## CYCLES\nunavailable: ").append(e).append('\n');
                return;
            }
            List<CycleComponent> components = condensation.cycles();
            out.append("\n## CYCLES (planner structural SCCs reachable from the goal: ").append(components.size())
                .append(", ").append(source).append(")\n");
            if (components.isEmpty()) return;
            out.append("# class: RECIPE = material loop through primary outputs; BYPRODUCT_VIEW = loop needs a pattern\n");
            out.append("#   used for a byproduct; ORDER_ONLY = only byproduct-ordering edges (~), no material loop.\n");
            out.append("# gain = material multiplier per lap (sampled loops): GROWTH > 1, CONSERVATIVE = 1, LOSSY < 1.\n");
            out.append("# loop edges: Pn = consumes via primary output, Pn* = via a byproduct output, ~Pn = byproduct ordering.\n");
            int index = 0;
            for (CycleComponent component : components) {
                appendCycle(out, ++index, component);
            }
        }

        private void appendCycle(StringBuilder out, int index, CycleComponent component) {
            List<AEKey> members = component.members();
            CycleStructure.Classification shape = CycleStructure.classify(members, component.internalEdges(),
                MAX_LOOPS);
            Set<IPatternDetails> details = new LinkedHashSet<>();
            for (CompiledPattern pattern : component.patterns()) details.add(pattern.details());
            out.append('S').append(index).append(' ').append(shape.kind()).append(' ').append(shape.growth());
            if (shape.maxGain() != null) out.append(" maxGain=").append(shape.maxGain());
            out.append(" members=").append(members.size()).append(" patterns=").append(idList(details)).append('\n');
            out.append("  planned: ").append(planned(details)).append('\n');
            for (CycleStructure.Loop loop : shape.loops()) {
                out.append("  loop: ").append(describeLoop(loop.edges()));
                if (loop.gain() != null) out.append("  gain=").append(loop.gain());
                out.append('\n');
            }
            out.append("  stock:");
            for (AEKey member : members) {
                out.append(' ').append(names.of(member)).append(" avail=").append(available(member))
                    .append(" used=").append(used.getOrDefault(member, PlannerAmount.ZERO))
                    .append(" surplus=").append(surplus(member)).append(';');
            }
            out.append('\n');
        }

        private String planned(Set<IPatternDetails> details) {
            if (result == null) return "n/a (no ECO planning result)";
            var matches = new ArrayList<String>();
            for (ComponentPlanningResult component : result.components()) {
                if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
                if (component.patterns().stream().noneMatch(details::contains)) continue;
                matches.add("C" + component.componentId() + "(" + component.status() + "," + component.cycleStatus()
                    + "," + component.cycleDisposition() + ")");
            }
            return matches.isEmpty() ? "no cycle component in this plan (cut by route choice or stock)"
                : String.join(" ", matches);
        }

        private String describeLoop(List<CraftingGraphEdge> loop) {
            var sb = new StringBuilder(names.of(loop.get(loop.size() - 1).requiredInput()));
            for (int i = loop.size() - 1; i >= 0; i--) {
                CraftingGraphEdge edge = loop.get(i);
                sb.append(" -").append(label(edge)).append("-> ").append(names.of(edge.producer()));
            }
            return sb.toString();
        }

        private String label(CraftingGraphEdge edge) {
            String id = ids.getOrDefault(edge.pattern().details(), "P?");
            return switch (CycleStructure.kind(edge)) {
                case ORDERING -> "~" + id;
                case FEEDBACK -> "^" + id;
                case CONSUMPTION -> CycleStructure.primaryView(edge) ? id : id + "*";
            };
        }

        private String idList(Collection<IPatternDetails> patterns) {
            return patterns.stream().map(pattern -> ids.getOrDefault(pattern, "P?"))
                .sorted(Comparator.comparingInt(CraftingPlanExport::idNumber)).distinct()
                .collect(Collectors.joining(","));
        }

        private String amounts(Map<AEKey, ?> amounts) {
            if (amounts.isEmpty()) return "{}";
            return amounts.entrySet().stream().map(entry -> names.of(entry.getKey()) + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
        }

        private String describe(IPatternDetails pattern) {
            var sb = new StringBuilder();
            String type = pattern.getClass().getSimpleName().replaceFirst("^AE", "").replaceFirst("Pattern$", "");
            sb.append('[').append(type.isEmpty() ? "pattern" : type.toLowerCase(Locale.ROOT)).append("] ");
            var inputs = new ArrayList<String>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                var alternatives = new ArrayList<String>();
                GenericStack[] possible = input.getPossibleInputs();
                long multiplier = input.getMultiplier();
                PlannerAmount first = possible.length == 0 ? PlannerAmount.ZERO
                    : PlannerAmount.of(possible[0].amount()).multiply(multiplier);
                for (GenericStack candidate : possible) {
                    PlannerAmount amount = PlannerAmount.of(candidate.amount()).multiply(multiplier);
                    alternatives.add((first.equals(amount) ? "" : amount + " ") + names.of(candidate.what()));
                }
                inputs.add(first + " " + String.join("|", alternatives));
            }
            sb.append(inputs.isEmpty() ? "-" : String.join(" + ", inputs)).append(" -> ");
            var outputs = new ArrayList<String>();
            for (GenericStack output : pattern.getOutputs()) outputs.add(output.amount() + " " + names.of(output.what()));
            sb.append(String.join(" + ", outputs));
            return sb.toString();
        }

        /** Walk from the goal through pattern inputs so the chain reads root first; unreachable tasks follow. */
        private List<IPatternDetails> orderTopDown() {
            var byOutput = new LinkedHashMap<AEKey, List<IPatternDetails>>();
            for (IPatternDetails pattern : tasks.keySet()) {
                for (GenericStack output : pattern.getOutputs()) {
                    byOutput.computeIfAbsent(output.what(), k -> new ArrayList<>()).add(pattern);
                }
            }
            var ordered = new LinkedHashSet<IPatternDetails>();
            var queue = new ArrayDeque<AEKey>();
            var visited = new HashSet<AEKey>();
            queue.add(goal.what());
            while (!queue.isEmpty()) {
                AEKey key = queue.removeFirst();
                if (!visited.add(key)) continue;
                for (IPatternDetails pattern : byOutput.getOrDefault(key, List.of())) {
                    if (!ordered.add(pattern)) continue;
                    for (IPatternDetails.IInput input : pattern.getInputs()) {
                        for (GenericStack possible : input.getPossibleInputs()) queue.add(possible.what());
                    }
                }
            }
            ordered.addAll(tasks.keySet());
            return new ArrayList<>(ordered);
        }
    }

    private static int idNumber(String id) {
        try {
            return Integer.parseInt(id.substring(1));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String truncate(String message) {
        String flat = message.replace('\n', ' ');
        return flat.length() > MAX_MESSAGE ? flat.substring(0, MAX_MESSAGE) + "..." : flat;
    }

    /** Compact key names; NBT-bearing items get a short tag explained once in the KEYS legend. */
    private static final class KeyNames {
        private final Map<AEKey, String> cache = new HashMap<>();
        private final Map<String, String> legend = new TreeMap<>();
        private final Set<String> assigned = new HashSet<>();

        String of(AEKey key) {
            return cache.computeIfAbsent(key, this::compute);
        }

        private String compute(AEKey key) {
            String base = key instanceof AEItemKey ? key.getId().toString()
                : key.getType().getId().getPath() + ":" + key.getId();
            boolean tagged = !key.equals(key.dropSecondary());
            String stem = tagged ? base + "#" + String.format("%04x", key.hashCode() & 0xFFFF) : base;
            String tag = stem;
            for (int suffix = 2; !assigned.add(tag); suffix++) tag = stem + "~" + suffix;
            if (tagged || !tag.equals(base)) {
                String nbt = key.toTagGeneric().toString();
                legend.put(tag, nbt.length() <= 240 ? nbt : nbt.substring(0, 240) + "...");
            }
            return tag;
        }
    }
}
