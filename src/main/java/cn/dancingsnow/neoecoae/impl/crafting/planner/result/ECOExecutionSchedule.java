package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.impl.crafting.planner.provenance.MaterialSource;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.PriorityQueue;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runtime component phases. The list is explicitly execution (supplier-to-consumer) order. */
public record ECOExecutionSchedule(List<ComponentExecutionPhase> phases, List<PhaseDependency> dependencies) {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");

    public ECOExecutionSchedule {
        phases = List.copyOf(phases);
        dependencies = List.copyOf(dependencies);
    }
    public ECOExecutionSchedule(List<ComponentExecutionPhase> phases) { this(phases, List.of()); }
    public record PhaseDependency(int producerPhase, int consumerPhase) { }
    public record ComponentExecutionPhase(int componentId, Type type, Set<IPatternDetails> patternSet,
            List<IPatternDetails> cycleWitness) {
        public ComponentExecutionPhase { patternSet = Set.copyOf(patternSet); cycleWitness = List.copyOf(cycleWitness); }
    }
    public enum Type { DAG, CYCLE, DYNAMIC_CYCLE }

    /** Builds the final physical graph from compiler-normalized semantics. */
    public static SelectedExecutionGraph selectedGraph(Map<IPatternDetails, PatternSemantics> selectedSemantics) {
        return SelectedExecutionGraph.build(selectedSemantics);
    }

    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder) {
        return from(components, executionOrder, null);
    }

    /**
     * Builds a runtime schedule from selected work only. Component candidates describe the structural graph,
     * but AE2 stores one aggregate counter per physical pattern; putting a candidate or the same physical
     * pattern into multiple phases can therefore gate real work at a phase whose inputs are not ready yet.
     */
    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, Long> plannedTasks) {
        return from(components, executionOrder, plannedTasks, null);
    }

    public static ECOExecutionSchedule from(List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, Long> plannedTasks, ExecutionProvenance provenance) {
        Map<Integer, ComponentPlanningResult> byId = components.stream().collect(
            java.util.stream.Collectors.toMap(ComponentPlanningResult::componentId, c -> c));
        List<IPatternDetails> executableTasks = plannedTasks == null
            ? components.stream().flatMap(component -> component.executionPatterns().stream()).toList()
            : plannedTasks.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .map(Map.Entry::getKey)
                .toList();

        // A pattern used by a solved cycle owns its entire AE2 aggregate counter, including any additional DAG
        // demand for the same physical pattern. Once the concrete witness has run, the cycle phase deliberately
        // permits that aggregate remainder.
        List<IPatternDetails> cycleOwnedPatterns = new ArrayList<>();
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            if (!ECOExecutionRequirement.componentIsExecutableCycle(component)) continue;
            for (IPatternDetails source : component.executionPatterns()) {
                IPatternDetails executable = executablePattern(source, executableTasks, plannedTasks != null);
                addPhysicalPattern(cycleOwnedPatterns, executable);
            }
        }

        var phases = new ArrayList<ComponentExecutionPhase>();
        List<IPatternDetails> assignedPatterns = new ArrayList<>();
        for (int id : executionOrder) {
            var c = byId.get(id);
            if (c == null) continue;
            if (c.type() == ComponentPlanningResult.Type.CYCLIC
                    && !ECOExecutionRequirement.componentIsExecutableCycle(c)) continue;
            Type type = ECOExecutionRequirement.componentIsDynamic(c) ? Type.DYNAMIC_CYCLE
                : c.type() == ComponentPlanningResult.Type.CYCLIC ? Type.CYCLE : Type.DAG;
            Set<IPatternDetails> patterns = new LinkedHashSet<>();
            for (IPatternDetails source : c.executionPatterns()) {
                IPatternDetails executable = executablePattern(source, executableTasks, plannedTasks != null);
                if (executable == null || containsPhysicalPattern(assignedPatterns, executable)) continue;
                if (type == Type.DAG && containsPhysicalPattern(cycleOwnedPatterns, executable)) continue;
                patterns.add(executable);
                assignedPatterns.add(executable);
            }
            if (patterns.isEmpty()) continue;

            List<IPatternDetails> witness = new ArrayList<>();
            if (type == Type.CYCLE && c.cycleResult() != null) {
                for (var firing : c.cycleResult().executionWitness()) {
                    IPatternDetails executable = executablePattern(
                        firing.pattern().details(), executableTasks, plannedTasks != null);
                    if (executable == null || !containsPhysicalPattern(patterns, executable)) {
                        throw new IllegalStateException("Cycle witness contains a pattern absent from the executable plan");
                    }
                    witness.add(executable);
                }
            }
            logPhaseGeneration(phases.size(), id, type, patterns);
            phases.add(new ComponentExecutionPhase(id, type, patterns, witness));
        }

        if (plannedTasks != null) {
            List<IPatternDetails> unassigned = executableTasks.stream()
                .filter(pattern -> !containsPhysicalPattern(assignedPatterns, pattern))
                .toList();
            if (!unassigned.isEmpty() && provenance == null) {
                throw new IllegalStateException(
                    "Execution schedule does not cover " + unassigned.size() + " planned pattern(s)");
            }
            int syntheticId = components.stream().mapToInt(ComponentPlanningResult::componentId).max().orElse(-1) + 1;
            for (IPatternDetails pattern : unassigned) {
                Set<IPatternDetails> patterns = Set.of(pattern);
                logPhaseGeneration(phases.size(), syntheticId, Type.DAG, patterns);
                phases.add(new ComponentExecutionPhase(syntheticId++, Type.DAG, patterns, List.of()));
                assignedPatterns.add(pattern);
            }
        }
        OrderedSchedule ordered = orderByExecutableDependencies(phases, provenance, executableTasks);
        return new ECOExecutionSchedule(ordered.phases(), ordered.dependencies());
    }

    /**
     * Numeric planning may switch to an alternate producer after the structural route was selected. Rebuild
     * phase edges from the final physical patterns so dependencies introduced by that alternate cannot remain
     * behind their consumers in the stale component order.
     */
    private static OrderedSchedule orderByExecutableDependencies(List<ComponentExecutionPhase> phases,
            ExecutionProvenance provenance, List<IPatternDetails> plannedTasks) {
        Map<AEKey, Set<Integer>> producersByKey = new LinkedHashMap<>();
        Map<AEKey, Set<Integer>> primaryProducersByKey = new LinkedHashMap<>();
        Map<IPatternDetails, Integer> phaseOfPattern = new java.util.IdentityHashMap<>();
        Map<Integer, Integer> phaseOfComponent = new LinkedHashMap<>();
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            phaseOfComponent.put(phases.get(phaseIndex).componentId(), phaseIndex);
            for (IPatternDetails pattern : phases.get(phaseIndex).patternSet()) {
                phaseOfPattern.put(pattern, phaseIndex);
                for (var output : producedOutputs(pattern)) {
                    if (output == null || output.what() == null || output.amount() <= 0L) continue;
                    producersByKey.computeIfAbsent(output.what(), ignored -> new LinkedHashSet<>())
                        .add(phaseIndex);
                }
                GenericStack primary = primaryOutput(pattern);
                if (primary != null && primary.what() != null && primary.amount() > 0L) {
                    primaryProducersByKey.computeIfAbsent(primary.what(), ignored -> new LinkedHashSet<>())
                        .add(phaseIndex);
                }
            }
        }

        List<Set<Integer>> outgoing = new ArrayList<>(phases.size());
        int[] indegree = new int[phases.size()];
        Map<PhaseDependency, Set<String>> edgeKeys = new LinkedHashMap<>();
        for (int i = 0; i < phases.size(); i++) outgoing.add(new LinkedHashSet<>());
        for (int consumer = 0; consumer < phases.size(); consumer++) {
            int consumerPhase = consumer;
            for (IPatternDetails pattern : phases.get(consumer).patternSet()) {
                PatternSemantics semantics = semantic(pattern);
                for (var input : semantics.consumedInputs()) {
                    AEKey key = input.key();
                    if (provenance == null || !provenance.covers(key)) {
                        if (LOGGER.isDebugEnabled()) LOGGER.debug(
                            "[ECO-DEPENDENCY] fallback key={} consumerPattern={} reason=NO_ATTRIBUTION", key, pattern);
                        Set<Integer> fallbackProducers = primaryProducersByKey.getOrDefault(
                            key, producersByKey.getOrDefault(key, Set.of()));
                        for (int producer : fallbackProducers) {
                            addAttributedDependency(phases, outgoing, indegree, edgeKeys, producer, consumerPhase,
                                pattern, key, semantics, "fallback");
                        }
                        continue;
                    }
                    for (MaterialSource source : provenance.suppliersOf(key)) {
                        Integer producer = null;
                        String kind;
                        if (source instanceof MaterialSource.PatternOutput output) {
                            producer = matchingPhase(output.pattern(), phaseOfPattern);
                            kind = output.primary() ? "primary of " + output.pattern()
                                : "byproduct of " + output.pattern();
                            if (producer == null && containsPhysicalPattern(plannedTasks, output.pattern())) {
                                throw new IllegalStateException("Attributed supplier has no phase: key=" + key
                                    + " pattern=" + output.pattern());
                            }
                        } else if (source instanceof MaterialSource.CycleOutput output) {
                            producer = phaseOfComponent.get(output.componentId());
                            kind = "cycle " + output.componentId();
                        } else {
                            continue;
                        }
                        if (producer != null) addAttributedDependency(phases, outgoing, indegree, edgeKeys,
                            producer, consumerPhase, pattern, key, semantics, kind);
                    }
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < indegree.length; i++) if (indegree[i] == 0) ready.add(i);
        List<ComponentExecutionPhase> ordered = new ArrayList<>(phases.size());
        List<Integer> orderedIndices = new ArrayList<>(phases.size());
        while (!ready.isEmpty()) {
            int phaseIndex = ready.remove();
            ordered.add(phases.get(phaseIndex));
            orderedIndices.add(phaseIndex);
            for (int dependent : outgoing.get(phaseIndex)) {
                if (--indegree[dependent] == 0) ready.add(dependent);
            }
        }
        if (ordered.size() != phases.size()) {
            String cycles = describeResidualCycles(phases, outgoing, edgeKeys, orderedIndices);
            LOGGER.warn("[ECO-DEPENDENCY] rejected final phase graph: {}", cycles);
            throw new IllegalStateException(
                "Final executable pattern dependencies contain a cycle outside a solved cycle phase: " + cycles);
        }
        int[] remapped = new int[phases.size()];
        for (int i = 0; i < orderedIndices.size(); i++) remapped[orderedIndices.get(i)] = i;
        List<PhaseDependency> dependencies = new ArrayList<>();
        for (int producer = 0; producer < outgoing.size(); producer++) {
            for (int consumer : outgoing.get(producer)) {
                dependencies.add(new PhaseDependency(remapped[producer], remapped[consumer]));
            }
        }
        dependencies.sort(java.util.Comparator.comparingInt(PhaseDependency::consumerPhase)
            .thenComparingInt(PhaseDependency::producerPhase));
        validateScheduleTopology(dependencies);
        return new OrderedSchedule(List.copyOf(ordered), List.copyOf(dependencies));
    }

    private record OrderedSchedule(List<ComponentExecutionPhase> phases,
            List<PhaseDependency> dependencies) { }

    /**
     * Names every residual cycle: its member phases with their patterns, and each intra-cycle edge with the
     * item keys that created it. The scheduler derives edges from every physical output, so a phase that
     * re-emits an upstream material as a byproduct shows up here as an edge back into its own supplier.
     */
    private static String describeResidualCycles(List<ComponentExecutionPhase> phases, List<Set<Integer>> outgoing,
            Map<PhaseDependency, Set<String>> edgeKeys, List<Integer> orderedIndices) {
        Set<Integer> remaining = new LinkedHashSet<>();
        for (int i = 0; i < phases.size(); i++) if (!orderedIndices.contains(i)) remaining.add(i);
        List<List<Integer>> cycles = stronglyConnectedComponents(remaining, outgoing).stream()
            .filter(scc -> scc.size() > 1).toList();
        StringBuilder text = new StringBuilder("remainingPhases=").append(remaining.size())
            .append(" cyclicGroups=").append(cycles.size());
        for (int group = 0; group < cycles.size(); group++) {
            List<Integer> members = new ArrayList<>(cycles.get(group));
            members.sort(null);
            text.append("; cycle#").append(group).append(" phases=[");
            for (int i = 0; i < members.size(); i++) {
                ComponentExecutionPhase phase = phases.get(members.get(i));
                if (i > 0) text.append(", ");
                text.append('p').append(members.get(i)).append('(').append(phase.type())
                    .append(" component=").append(phase.componentId())
                    .append(" patterns=").append(phase.patternSet()).append(')');
            }
            text.append("] edges=[");
            boolean first = true;
            for (int producer : members) {
                for (int consumer : outgoing.get(producer)) {
                    if (!members.contains(consumer)) continue;
                    if (!first) text.append(", ");
                    first = false;
                    text.append('p').append(producer).append("->p").append(consumer).append(" via ")
                        .append(edgeKeys.getOrDefault(new PhaseDependency(producer, consumer), Set.of()));
                }
            }
            text.append(']');
        }
        return text.toString();
    }

    private static List<List<Integer>> stronglyConnectedComponents(Set<Integer> nodes, List<Set<Integer>> outgoing) {
        List<List<Integer>> result = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>(), low = new HashMap<>();
        Set<Integer> onStack = new HashSet<>();
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        int[] next = {0};
        for (int node : nodes) if (!index.containsKey(node)) tarjan(node, nodes, outgoing, index, low, onStack, stack, next, result);
        return result;
    }

    private static void tarjan(int v, Set<Integer> nodes, List<Set<Integer>> outgoing, Map<Integer,Integer> index,
            Map<Integer,Integer> low, Set<Integer> onStack, java.util.ArrayDeque<Integer> stack, int[] next,
            List<List<Integer>> result) {
        index.put(v, next[0]); low.put(v, next[0]++); stack.push(v); onStack.add(v);
        for (int w : outgoing.get(v)) if (nodes.contains(w)) {
            if (!index.containsKey(w)) { tarjan(w,nodes,outgoing,index,low,onStack,stack,next,result); low.put(v, Math.min(low.get(v), low.get(w))); }
            else if (onStack.contains(w)) low.put(v, Math.min(low.get(v), index.get(w)));
        }
        if (low.get(v).equals(index.get(v))) { List<Integer> scc = new ArrayList<>(); int w; do { w=stack.pop(); onStack.remove(w); scc.add(w); } while (w!=v); result.add(scc); }
    }

    private static boolean addDependency(List<Set<Integer>> outgoing, int[] indegree,
            int producer, int consumer) {
        if (producer == consumer) return false;
        if (outgoing.get(producer).add(consumer)) {
            indegree[consumer]++;
            return true;
        }
        return false;
    }

    private static void addAttributedDependency(List<ComponentExecutionPhase> phases,
            List<Set<Integer>> outgoing, int[] indegree, Map<PhaseDependency, Set<String>> edgeKeys,
            int producer, int consumer, IPatternDetails consumerPattern, AEKey key,
            PatternSemantics semantics, String source) {
        if (producer == consumer) return;
        edgeKeys.computeIfAbsent(new PhaseDependency(producer, consumer), ignored -> new LinkedHashSet<>())
            .add(key + " (" + source + ")");
        logDependency(phases, producer, consumer, consumerPattern, key, semantics,
            addDependency(outgoing, indegree, producer, consumer));
    }

    private static Integer matchingPhase(IPatternDetails pattern, Map<IPatternDetails, Integer> phaseOfPattern) {
        Integer direct = phaseOfPattern.get(pattern);
        if (direct != null) return direct;
        Integer match = null;
        for (var entry : phaseOfPattern.entrySet()) {
            if (!PlanIdentity.samePattern(entry.getKey(), pattern)) continue;
            if (match != null && !match.equals(entry.getValue())) {
                throw new IllegalStateException("Attributed pattern is owned by multiple phases: " + pattern);
            }
            match = entry.getValue();
        }
        return match;
    }

    private static IPatternDetails executablePattern(IPatternDetails source, List<IPatternDetails> executableTasks,
            boolean requirePlannedTask) {
        if (source == null) return null;
        for (IPatternDetails task : executableTasks) if (task == source) return task;
        List<IPatternDetails> matches = executableTasks.stream()
            .filter(task -> ECOPhaseScheduler.samePattern(source, task))
            .toList();
        if (matches.size() == 1) return matches.getFirst();
        return requirePlannedTask ? null : source;
    }

    private static PatternSemantics semantic(IPatternDetails pattern) {
        return ECOPhaseScheduler.semantic(pattern);
    }

    /** True recipe outputs only. Input remainders are deliberately not phase producers. */
    private static List<GenericStack> producedOutputs(IPatternDetails pattern) {
        PatternSemantics semantics = semantic(pattern);
        List<GenericStack> result = new ArrayList<>(semantics.producedOutputs());
        GenericStack primary = primaryOutput(pattern);
        if (primary != null) result.add(primary);
        return result;
    }

    private static GenericStack primaryOutput(IPatternDetails pattern) {
        try {
            return pattern.getPrimaryOutput();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void logDependency(List<ComponentExecutionPhase> phases, int producer, int consumer,
            IPatternDetails consumerPattern, AEKey key, PatternSemantics semantics, boolean edgeAdded) {
        if (!LOGGER.isDebugEnabled()) return;
        IPatternDetails producerPattern = phases.get(producer).patternSet().stream()
            .filter(pattern -> producedOutputs(pattern).stream().anyMatch(output -> output != null
                && key.equals(output.what())))
            .findFirst()
            .orElseGet(() -> phases.get(producer).patternSet().stream().findFirst().orElse(null));
        LOGGER.debug("[ECO-DEPENDENCY] producerPattern={} consumerPattern={} key={} producerPhase={} consumerPhase={} "
                + "edgeAdded={} matchMode={} definition={}",
            producerPattern, consumerPattern, key, producer, consumer, edgeAdded, semantics.matchingMode(),
            semantics.physicalDefinition());
    }

    private static void logPhaseGeneration(int phaseIndex, int componentId, Type type,
            Set<IPatternDetails> patterns) {
        if (!LOGGER.isDebugEnabled()) return;
        for (IPatternDetails pattern : patterns) {
            PatternSemantics semantics = semantic(pattern);
            LOGGER.debug("[ECO-PHASE-GENERATION] phaseIndex={} phaseType={} componentId={} pattern={} inputs={} outputs={}",
                phaseIndex, type, componentId, pattern, semantics.consumedInputs(), producedOutputs(pattern));
        }
    }

    private static void validateScheduleTopology(List<PhaseDependency> dependencies) {
        for (PhaseDependency dependency : dependencies) {
            if (dependency.producerPhase() < dependency.consumerPhase()) continue;
            throw new IllegalStateException("Executable dependency is not in supplier-to-consumer order: "
                + dependency);
        }
    }

    private static void addPhysicalPattern(List<IPatternDetails> patterns, IPatternDetails candidate) {
        if (candidate != null && !containsPhysicalPattern(patterns, candidate)) patterns.add(candidate);
    }

    private static boolean containsPhysicalPattern(Iterable<IPatternDetails> patterns, IPatternDetails candidate) {
        for (IPatternDetails pattern : patterns) {
            if (ECOPhaseScheduler.samePattern(pattern, candidate)) return true;
        }
        return false;
    }
}
