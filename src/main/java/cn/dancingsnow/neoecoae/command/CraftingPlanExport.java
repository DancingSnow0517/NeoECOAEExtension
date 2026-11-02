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
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
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
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final int MAX_STOCK_MEMBERS = 12;

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
        if (plan == null || grid == null || !ARMED.remove(player.getUUID())) return;
        try {
            Path dir = player.server.getWorldPath(LevelResource.ROOT).resolve("eco-plan-exports");
            Files.createDirectories(dir);
            Path file = dir.resolve("plan-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4) + ".txt");
            Files.writeString(file, render(grid, plan, result, options));
            player.sendSystemMessage(Component.literal("ECO plan exported: " + file.toAbsolutePath()));
        } catch (IOException | RuntimeException e) {
            player.sendSystemMessage(Component.literal("ECO plan export failed: " + e));
        }
    }

    static String render(IGrid grid, ICraftingPlan plan, @Nullable ECOPlanningResult result,
            @Nullable ECOPlannerOptions options) {
        return new Report(grid, plan, result, options).render();
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
        private final Map<IPatternDetails, BigInteger> tasks = new LinkedHashMap<>();
        private final Map<AEKey, BigInteger> used;
        private final Map<AEKey, BigInteger> missing;
        private final Map<AEKey, BigInteger> emitted;
        private final Map<AEKey, BigInteger> produced = new HashMap<>();
        private final Map<AEKey, BigInteger> consumed = new HashMap<>();
        private final Map<AEKey, Boolean> producedOnlyAsByproduct = new HashMap<>();
        private final KeyCounter stock;

        Report(IGrid grid, ICraftingPlan plan, @Nullable ECOPlanningResult result, @Nullable ECOPlannerOptions options) {
            this.grid = grid;
            this.plan = plan;
            this.result = result;
            this.options = options;
            this.goal = plan.finalOutput();
            collectPatterns();
            collectTasks();
            ECOExactCraftingPlan exact = plan instanceof ECOExactCraftingPlan exactPlan ? exactPlan : null;
            this.used = materials(result == null ? null : result.exactUsedItems(), plan.usedItems(),
                exact == null ? Map.of() : exact.deferredStock());
            this.missing = materials(result == null ? null : result.exactMissingItems(), plan.missingItems(), Map.of());
            this.emitted = materials(result == null ? null : result.exactEmittedItems(), plan.emittedItems(),
                exact == null ? Map.of() : exact.deferredEmitted());
            KeyCounter cached;
            try {
                cached = grid.getStorageService().getCachedInventory();
            } catch (RuntimeException e) {
                cached = new KeyCounter();
            }
            this.stock = cached;
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
                result.exactPatternTimes().forEach((pattern, amount) -> tasks.put(pattern, amount.toBigInteger()));
            } else if (plan instanceof ECOExactCraftingPlan exact) {
                tasks.putAll(exact.exactTasks());
            } else {
                plan.patternTimes().forEach((pattern, times) -> tasks.put(pattern, BigInteger.valueOf(times)));
            }
        }

        private static Map<AEKey, BigInteger> materials(@Nullable Map<AEKey, PlannerAmount> exact, KeyCounter counter,
                Map<AEKey, BigInteger> deferred) {
            var amounts = new LinkedHashMap<AEKey, BigInteger>();
            if (exact != null && !exact.isEmpty()) {
                exact.forEach((key, amount) -> {
                    if (amount.signum() > 0) amounts.put(key, amount.toBigInteger());
                });
                return amounts;
            }
            for (var entry : counter) {
                amounts.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
            deferred.forEach((key, amount) -> amounts.merge(key, amount, BigInteger::add));
            amounts.values().removeIf(amount -> amount.signum() <= 0);
            return amounts;
        }

        private void balance() {
            tasks.forEach((pattern, times) -> {
                AEKey primary = pattern.getPrimaryOutput().what();
                for (GenericStack output : pattern.getOutputs()) {
                    produced.merge(output.what(), times.multiply(BigInteger.valueOf(output.amount())), BigInteger::add);
                    boolean byproduct = !output.what().equals(primary);
                    producedOnlyAsByproduct.merge(output.what(), byproduct, Boolean::logicalAnd);
                }
                for (IPatternDetails.IInput input : pattern.getInputs()) {
                    GenericStack[] possible = input.getPossibleInputs();
                    if (possible.length == 0) continue;
                    BigInteger amount = BigInteger.valueOf(possible[0].amount())
                        .multiply(BigInteger.valueOf(input.getMultiplier()));
                    consumed.merge(possible[0].what(), times.multiply(amount), BigInteger::add);
                }
            });
        }

        private BigInteger surplus(AEKey key) {
            BigInteger value = produced.getOrDefault(key, BigInteger.ZERO).add(used.getOrDefault(key, BigInteger.ZERO))
                .subtract(consumed.getOrDefault(key, BigInteger.ZERO));
            return key.equals(goal.what()) ? value.subtract(BigInteger.valueOf(goal.amount())) : value;
        }

        String render() {
            var out = new StringBuilder();
            out.append("# ECO crafting export\n");
            out.append("# Sections: ORDER, PLANNER, PATTERNS, PRODUCER ORDER, CRAFTS, USED FROM STORAGE, MISSING, EMITTED,\n");
            out.append("#   SURPLUS, IMBALANCED, CYCLES, KEYS.\n");
            out.append("# Pattern line: P<id> [type] inputs -> outputs, per single craft; 'a|b' = substitutes.\n");
            out.append("# Keys: item id; other types are prefixed (f: fluid, chemical: ...); 'id#xxxx' = item with NBT.\n");
            out.append("# Stock 'avail' is the network content when the plan was shown; 'inf' = unbounded storage.\n");
            appendOrder(out);
            appendPlanner(out);
            appendPatterns(out);
            appendProducerOrder(out);
            appendCrafts(out);
            appendUsed(out);
            appendAmounts(out, "MISSING", missing);
            appendAmounts(out, "EMITTED (level emitters)", emitted);
            appendSurplus(out);
            appendCycles(out);
            if (!names.legend.isEmpty()) {
                out.append("\n## KEYS\n");
                names.legend.forEach((tag, desc) -> out.append(tag).append(" = ").append(desc).append('\n'));
            }
            return out.toString();
        }

        private void appendOrder(StringBuilder out) {
            out.append("\n## ORDER\n");
            out.append("goal: ").append(goal.amount()).append(' ').append(names.of(goal.what())).append('\n');
            out.append("simulation: ").append(plan.simulation())
                .append(plan.simulation() ? " (missing inputs, not executable)" : "").append('\n');
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
            // This port has no background plan improvements, so the confirm screen keeps its first result.
            out.append("result: status=").append(result.status())
                .append(" calculationMs=").append(result.calculationNanos() / 1_000_000L)
                .append(" components=").append(result.components().size()).append(" cyclic=").append(cyclic)
                .append(" (first result shown in the confirm screen)\n");
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
            out.append("cycle components:\n");
            for (ComponentPlanningResult component : result.components()) {
                if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
                out.append("  C").append(component.componentId()).append(' ').append(component.status())
                    .append(" cycle=").append(component.cycleStatus())
                    .append(" disposition=").append(component.cycleDisposition());
                CycleSolveResult solved = component.cycleResult();
                if (solved != null) {
                    out.append(" solve=").append(solved.status())
                        .append(" firings=").append(solved.plannerTotalFirings());
                }
                out.append(" required=").append(amounts(component.requiredOutputs()));
                if (solved != null) {
                    out.append(" stockTaken=").append(amounts(solved.requiredSeed()));
                    if (!solved.seedShortfall().isEmpty()) out.append(" seedShortfall=").append(amounts(solved.seedShortfall()));
                    if (!solved.externalDemand().isEmpty()) out.append(" externalDemand=").append(amounts(solved.externalDemand()));
                }
                if (!component.externalMissingItems().isEmpty()) {
                    out.append(" externalMissing=").append(amounts(component.externalMissingItems()));
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

        private void appendUsed(StringBuilder out) {
            if (used.isEmpty()) return;
            var rows = new TreeMap<String, String>();
            used.forEach((key, amount) -> {
                long available = stock.get(key);
                boolean all = available != Long.MAX_VALUE && amount.compareTo(BigInteger.valueOf(available)) >= 0;
                rows.put(names.of(key), "used=" + amount + " avail=" + available(available) + (all ? " (all)" : ""));
            });
            out.append("\n## USED FROM STORAGE (used=taken by this job, avail=network stock; (all) = job takes every copy)\n");
            rows.forEach((name, row) -> out.append(row).append(' ').append(name).append('\n'));
        }

        private void appendAmounts(StringBuilder out, String title, Map<AEKey, BigInteger> amounts) {
            if (amounts.isEmpty()) return;
            var rows = new TreeMap<String, BigInteger>();
            amounts.forEach((key, amount) -> rows.merge(names.of(key), amount, BigInteger::add));
            out.append("\n## ").append(title).append('\n');
            rows.forEach((name, amount) -> out.append(amount).append(' ').append(name).append('\n'));
        }

        private void appendSurplus(StringBuilder out) {
            var keys = new LinkedHashSet<AEKey>();
            keys.addAll(produced.keySet());
            keys.addAll(used.keySet());
            var positive = new ArrayList<Map.Entry<AEKey, BigInteger>>();
            var negative = new ArrayList<Map.Entry<AEKey, BigInteger>>();
            for (AEKey key : keys) {
                BigInteger value = surplus(key);
                if (value.signum() > 0) positive.add(Map.entry(key, value));
                else if (value.signum() < 0) negative.add(Map.entry(key, value));
            }
            positive.sort(Map.Entry.<AEKey, BigInteger>comparingByValue().reversed());
            if (!positive.isEmpty()) {
                out.append("\n## SURPLUS (produced + taken from stock - consumed = left over after the job;")
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
                out.append("\n## IMBALANCED (consumed more than produced + stock; usually fuzzy or substitute matching)\n");
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
                Set<ResourceLocation> fuzzy = options == null ? Set.of() : options.fuzzyPlanningItemIds();
                var cached = CompiledStructureCache.get(service, CompiledStructureCache.revision(service),
                    AE2PatternIntrospection.reloadGeneration(), goal.what(), cycles, fuzzy);
                if (cached != null) {
                    condensation = cached.condensation();
                    source = "cached planner structure";
                } else {
                    var network = new CraftingNetworkCompiler().compile(service, goal.what(), cycles, fuzzy,
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
            int listed = 0;
            for (AEKey member : members) {
                if (++listed > MAX_STOCK_MEMBERS) {
                    out.append(" ... ").append(members.size() - MAX_STOCK_MEMBERS).append(" more");
                    break;
                }
                out.append(' ').append(names.of(member)).append(" avail=").append(available(stock.get(member)))
                    .append(" used=").append(used.getOrDefault(member, BigInteger.ZERO))
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

        private String amounts(Map<AEKey, Long> amounts) {
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
                for (GenericStack possible : input.getPossibleInputs()) alternatives.add(names.of(possible.what()));
                GenericStack[] possible = input.getPossibleInputs();
                long amount = possible.length == 0 ? 0 : possible[0].amount() * input.getMultiplier();
                inputs.add(amount + " " + String.join("|", alternatives));
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
            var queue = new ArrayList<AEKey>();
            var visited = new HashSet<AEKey>();
            queue.add(goal.what());
            while (!queue.isEmpty()) {
                AEKey key = queue.remove(0);
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

    private static String available(long amount) {
        return amount == Long.MAX_VALUE ? "inf" : Long.toString(amount);
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

        String of(AEKey key) {
            return cache.computeIfAbsent(key, this::compute);
        }

        private String compute(AEKey key) {
            String base = key instanceof AEItemKey ? key.getId().toString()
                : key.getType().getId().getPath() + ":" + key.getId();
            if (!(key instanceof AEItemKey item) || !item.hasTag()) return base;
            String tag = base + "#" + String.format("%04x", item.hashCode() & 0xFFFF);
            String nbt = String.valueOf(item.getTag());
            if (nbt.length() > 240) nbt = nbt.substring(0, 240) + "...";
            legend.putIfAbsent(tag, key.getDisplayName().getString() + " " + nbt);
            return tag;
        }
    }
}
