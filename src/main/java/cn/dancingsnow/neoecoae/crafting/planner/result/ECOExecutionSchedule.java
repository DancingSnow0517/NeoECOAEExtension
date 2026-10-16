package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.core.math.NEMath;
import cn.dancingsnow.neoecoae.core.graph.StronglyConnected;
import cn.dancingsnow.neoecoae.core.graph.TopoSort;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.ExecutionProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runtime component phases. The list is explicitly execution (supplier-to-consumer) order. */
public record ECOExecutionSchedule(List<ComponentExecutionPhase> phases, List<PhaseDependency> dependencies) {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");

    public ECOExecutionSchedule {


    }
    public ECOExecutionSchedule(List<ComponentExecutionPhase> phases) { this(phases, List.of()); }
    public record PhaseDependency(int producerPhase, int consumerPhase) { }
    public record ComponentExecutionPhase(int componentId, Type type, Set<IPatternDetails> patternSet,
            List<IPatternDetails> cycleWitness) {
        public ComponentExecutionPhase {  }
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
        PatternIndex executableTaskIndex = plannedTasks == null
            ? PatternIndex.from(components.stream().flatMap(component -> component.executionPatterns().stream()).toList())
            : PatternIndex.fromPlannedTasks(plannedTasks);
        List<IPatternDetails> executableTasks = executableTaskIndex.patterns();

        // A pattern used by a solved cycle owns its entire AE2 aggregate counter, including any additional DAG
        // demand for the same physical pattern. Once the concrete witness has run, the cycle phase deliberately
        // permits that aggregate remainder.
        PatternIndex cycleOwnedPatterns = new PatternIndex();
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            if (!component.cycleDisposition().executable()) continue;
            for (IPatternDetails source : component.executionPatterns()) {
                IPatternDetails executable = executablePattern(source, executableTaskIndex, plannedTasks != null);
                addPhysicalPattern(cycleOwnedPatterns, executable);
            }
        }

        var phases = new ArrayList<ComponentExecutionPhase>();
        int syntheticId = components.stream().mapToInt(ComponentPlanningResult::componentId).max().orElse(-1) + 1;
        PatternIndex assignedPatterns = new PatternIndex();
        for (int id : executionOrder) {
            var c = byId.get(id);
            if (c == null) continue;
            if (c.type() == ComponentPlanningResult.Type.CYCLIC
                    && !c.cycleDisposition().executable()) continue;
            Type type = (c.type() == ComponentPlanningResult.Type.CYCLIC && c.cycleDisposition().dynamic()) ? Type.DYNAMIC_CYCLE
                : c.type() == ComponentPlanningResult.Type.CYCLIC ? Type.CYCLE : Type.DAG;
            Set<IPatternDetails> patterns = new LinkedHashSet<>();
            for (IPatternDetails source : c.executionPatterns()) {
                IPatternDetails executable = executablePattern(source, executableTaskIndex, plannedTasks != null);
                if (executable == null || containsPhysicalPattern(assignedPatterns, executable)) continue;
                if (type == Type.DAG && containsPhysicalPattern(cycleOwnedPatterns, executable)) continue;
                patterns.add(executable);
                assignedPatterns.add(executable);
            }
            if (patterns.isEmpty()) continue;

            // A structural DAG component may also own locally resolved tool/catalyst producers.
            // With exact attribution, each physical task needs its own phase: a dependency between
            // two such tasks is not a self-loop, and an outside task may need to run between them.
            // Keep solved cycles atomic so their witness and seed contracts remain intact.
            if (type == Type.DAG && provenance != null) {
                boolean first = true;
                for (IPatternDetails pattern : patterns) {
                    phases.add(new ComponentExecutionPhase(first ? id : syntheticId++, type,
                        Set.of(pattern), List.of()));
                    first = false;
                }
                continue;
            }

            List<IPatternDetails> witness = new ArrayList<>();
            if (type == Type.CYCLE && c.cycleResult() != null) {
                PatternIndex phasePatterns = PatternIndex.from(patterns);
                for (var firing : c.cycleResult().executionWitness()) {
                    IPatternDetails executable = executablePattern(
                        firing.pattern().details(), executableTaskIndex, plannedTasks != null);
                    if (executable == null || !containsPhysicalPattern(phasePatterns, executable)) {
                        throw new IllegalStateException("Cycle witness contains a pattern absent from the executable plan");
                    }
                    witness.add(executable);
                }
            }
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
            for (IPatternDetails pattern : unassigned) {
                Set<IPatternDetails> patterns = Set.of(pattern);
                phases.add(new ComponentExecutionPhase(syntheticId++, Type.DAG, patterns, List.of()));
                assignedPatterns.add(pattern);
            }
        }
        OrderedSchedule ordered = orderByExecutableDependencies(phases, provenance, executableTaskIndex);
        return new ECOExecutionSchedule(ordered.phases(), ordered.dependencies());
    }

    /**
     * Numeric planning may switch to an alternate producer after the structural route was selected. Rebuild
     * phase edges from the final physical patterns so dependencies introduced by that alternate cannot remain
     * behind their consumers in the stale component order.
     */
    private static OrderedSchedule orderByExecutableDependencies(List<ComponentExecutionPhase> phases,
            ExecutionProvenance provenance, PatternIndex plannedTasks) {
        Map<AEKey, Set<Integer>> producersByKey = new LinkedHashMap<>();
        Map<AEKey, Set<Integer>> primaryProducersByKey = new LinkedHashMap<>();
        PatternIndex phaseOfPattern = new PatternIndex();
        Map<Integer, Integer> phaseOfComponent = new LinkedHashMap<>();
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            phaseOfComponent.put(phases.get(phaseIndex).componentId(), phaseIndex);
            for (IPatternDetails pattern : phases.get(phaseIndex).patternSet()) {
                phaseOfPattern.add(pattern, phaseIndex);
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
        Map<PhaseDependency, Set<String>> edgeKeys = new LinkedHashMap<>();
        for (int i = 0; i < phases.size(); i++) outgoing.add(new LinkedHashSet<>());
        if (provenance != null) {
            provenance.requireComplete();
            for (var allocation : provenance.allocations()) {
                var demand = provenance.demands().get(allocation.demandId());
                Integer consumer = switch (demand.kind()) {
                    case GOAL -> null;
                    case INPUT -> matchingPhase(demand.consumer(), phaseOfPattern);
                    case CYCLE_BOUNDARY -> phaseOfComponent.get(demand.componentId());
                };
                if (demand.kind() == cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand.Kind.GOAL)
                    continue;
                MaterialSource source = allocation.source();
                if (consumer == null) {
                    // A cycle fulfilled entirely by reserved stock has no executable phase or dependency.
                    if (demand.kind() == cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand.Kind.CYCLE_BOUNDARY
                            && (source instanceof MaterialSource.Stock || source instanceof MaterialSource.Emitted)) {
                        continue;
                    }
                    throw new IllegalStateException("Attributed consumer has no phase: demand=" + demand);
                }
                Integer producer;
                if (source instanceof MaterialSource.PatternOutput output) {
                    producer = matchingPhase(output.pattern(), phaseOfPattern);
                } else if (source instanceof MaterialSource.CycleOutput output) {
                    producer = phaseOfComponent.get(output.componentId());
                } else {
                    continue; // Stock and emitted resources have no recipe predecessor.
                }
                if (producer == null) {
                    throw new IllegalStateException("Attributed supplier has no phase: demand=" + demand
                        + " allocation=" + allocation);
                }
                if (producer.equals(consumer) && phases.get(consumer).type() == Type.DAG) {
                    throw new IllegalStateException("Unresolved selected task self-cycle: demand=" + demand
                        + " allocation=" + allocation);
                }
                addAttributedDependency(phases, outgoing, edgeKeys, producer, consumer,
                    demand.consumer(), allocation.material(), null, allocation.toString());
            }
        } else {
            // Compatibility only for old callers without numeric provenance.
            for (int consumer = 0; consumer < phases.size(); consumer++) {
                for (IPatternDetails pattern : phases.get(consumer).patternSet()) {
                    PatternSemantics semantics = semantic(pattern);
                    for (var input : semantics.consumedInputs()) {
                        AEKey key = input.key();
                        Set<Integer> fallbackProducers = primaryProducersByKey.getOrDefault(
                            key, producersByKey.getOrDefault(key, Set.of()));
                        for (int producer : fallbackProducers) {
                            addAttributedDependency(phases, outgoing, edgeKeys, producer, consumer,
                                pattern, key, semantics, "legacy");
                        }
                    }
                }
            }
        }

        // Natural order on phase indices: among ready phases, the lowest index is emitted first.
        TopoSort.Result<Integer> sorted = TopoSort.kahnOrdered(IntStream.range(0, phases.size()).boxed().toList(),
            i -> outgoing.get(i), Comparator.naturalOrder());
        List<Integer> orderedIndices = sorted.order();
        if (!sorted.acyclic()) {
            String cycles = describeResidualCycles(phases, outgoing, edgeKeys, orderedIndices);
            throw new IllegalStateException(
                "Final executable pattern dependencies contain a cycle outside a solved cycle phase: " + cycles);
        }
        List<ComponentExecutionPhase> ordered = orderedIndices.stream().map(i -> phases.get(i)).toList();
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
        return new OrderedSchedule(ordered, dependencies);
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

    // Visible for testing.
    static List<List<Integer>> stronglyConnectedComponents(Set<Integer> nodes, List<Set<Integer>> outgoing) {
        return StronglyConnected.components(nodes,
            node -> outgoing.get(node).stream().filter(nodes::contains).toList(), () -> {});
    }

    private static void addAttributedDependency(List<ComponentExecutionPhase> phases,
            List<Set<Integer>> outgoing, Map<PhaseDependency, Set<String>> edgeKeys,
            int producer, int consumer, IPatternDetails consumerPattern, AEKey key,
            PatternSemantics semantics, String source) {
        if (producer == consumer) return;
        edgeKeys.computeIfAbsent(new PhaseDependency(producer, consumer), ignored -> new LinkedHashSet<>())
            .add(key + " (" + source + ")");
        outgoing.get(producer).add(consumer);
    }

    private static Integer matchingPhase(IPatternDetails pattern, PatternIndex phaseOfPattern) {
        return phaseOfPattern.matchingPhase(pattern);
    }

    private static IPatternDetails executablePattern(IPatternDetails source, PatternIndex executableTasks,
            boolean requirePlannedTask) {
        return executableTasks.executablePattern(source, requirePlannedTask);
    }

    private static PatternSemantics semantic(IPatternDetails pattern) {
        PatternSemantics semantics = ECOPhaseScheduler.semantic(pattern);
        if (!semantics.supported()
                || semantics.executionRestriction() != PatternSemantics.ExecutionRestriction.NONE
                || semantics.matchingMode() == PatternSemantics.MatchingMode.FUZZY
                || semantics.matchingMode() == PatternSemantics.MatchingMode.UNKNOWN) {
            throw new IllegalStateException("Unsupported execution pattern semantics: "
                + semantics.unsupportedReason() + " restriction=" + semantics.executionRestriction());
        }
        return semantics;
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

    private static void validateScheduleTopology(List<PhaseDependency> dependencies) {
        for (PhaseDependency dependency : dependencies) {
            if (dependency.producerPhase() < dependency.consumerPhase()) continue;
            throw new IllegalStateException("Executable dependency is not in supplier-to-consumer order: "
                + dependency);
        }
    }

    private static void addPhysicalPattern(PatternIndex patterns, IPatternDetails candidate) {
        if (candidate != null) patterns.addIfAbsent(candidate);
    }

    private static boolean containsPhysicalPattern(PatternIndex patterns, IPatternDetails candidate) {
        return patterns.containsPhysicalPattern(candidate);
    }

    /**
     * Local physical-pattern index used by schedule construction and ordering. Instance lookup is deliberately
     * separate from identity lookup: AE2 can reconstruct a wrapper instance, while null or unsupported identities
     * must retain the original instance-only matching semantics.
     */
    private static final class PatternIndex {
        private final boolean plannedTaskIndex;
        private final List<IPatternDetails> entries = new ArrayList<>();
        private final IdentityHashMap<IPatternDetails, IPatternDetails> byInstance = new IdentityHashMap<>();
        private final Map<PlanIdentity.PatternIdentity, IdentityBucket> byIdentity = new LinkedHashMap<>();
        private final IdentityHashMap<IPatternDetails, PlanIdentity.PatternIdentity> identityByInstance =
            new IdentityHashMap<>();
        private final IdentityHashMap<IPatternDetails, Integer> phaseByInstance = new IdentityHashMap<>();
        private final Map<PlanIdentity.PatternIdentity, Integer> phaseByIdentity = new LinkedHashMap<>();
        private final Set<PlanIdentity.PatternIdentity> ambiguousPhaseOwnership = new HashSet<>();
        private final IdentityHashMap<IPatternDetails, Long> plannedCountsByInstance = new IdentityHashMap<>();
        private final Map<PlanIdentity.PatternIdentity, Long> plannedCountsByIdentity = new LinkedHashMap<>();

        private PatternIndex() {
            this(false);
        }

        private PatternIndex(boolean plannedTaskIndex) {
            this.plannedTaskIndex = plannedTaskIndex;
        }

        private static PatternIndex from(Iterable<IPatternDetails> patterns) {
            PatternIndex index = new PatternIndex();
            for (IPatternDetails pattern : patterns) index.add(pattern);
            return index;
        }

        private static PatternIndex fromPlannedTasks(Map<IPatternDetails, Long> plannedTasks) {
            PatternIndex index = new PatternIndex(true);
            for (var entry : plannedTasks.entrySet()) {
                Long count = entry.getValue();
                if (count != null && count > 0L) index.addPlanned(entry.getKey(), count);
            }
            return index;
        }

        private List<IPatternDetails> patterns() {
            return entries;
        }

        private void add(IPatternDetails pattern) {
            entries.add(pattern);
            if (!byInstance.containsKey(pattern)) byInstance.put(pattern, pattern);
            PlanIdentity.PatternIdentity identity = identityOf(pattern);
            if (identity != null) byIdentity.computeIfAbsent(identity, ignored -> new IdentityBucket()).add(pattern);
        }

        private void add(IPatternDetails pattern, int phase) {
            add(pattern);
            phaseByInstance.put(pattern, phase);
            PlanIdentity.PatternIdentity identity = identityOf(pattern);
            if (identity != null) {
                Integer owner = phaseByIdentity.putIfAbsent(identity, phase);
                if (owner != null && owner != phase) ambiguousPhaseOwnership.add(identity);
            }
        }

        private void addIfAbsent(IPatternDetails pattern) {
            if (!containsPhysicalPattern(pattern)) add(pattern);
        }

        private void addPlanned(IPatternDetails pattern, long count) {
            add(pattern);
            PlanIdentity.PatternIdentity identity = identityOf(pattern);
            if (identity == null) {
                plannedCountsByInstance.merge(pattern, count, NEMath::saturatingAdd);
            } else {
                plannedCountsByIdentity.merge(identity, count, NEMath::saturatingAdd);
            }
        }

        private boolean containsPhysicalPattern(IPatternDetails candidate) {
            if (byInstance.containsKey(candidate)) return true;
            return lookupByIdentity(identityOf(candidate)) != null;
        }

        private IPatternDetails executablePattern(IPatternDetails source, boolean requirePlannedTask) {
            if (source == null) return null;
            if (byInstance.containsKey(source)) return byInstance.get(source);
            IdentityBucket matches = lookupByIdentity(identityOf(source));
            if (matches != null && matches.count == 1) return matches.first;
            return requirePlannedTask ? null : source;
        }

        private Integer matchingPhase(IPatternDetails pattern) {
            if (phaseByInstance.containsKey(pattern)) return phaseByInstance.get(pattern);
            PlanIdentity.PatternIdentity identity = identityOf(pattern);
            if (identity == null) return null;
            if (ambiguousPhaseOwnership.contains(identity)) {
                throw new IllegalStateException("Attributed pattern is owned by multiple phases: " + pattern);
            }
            return phaseByIdentity.get(identity);
        }

        /** Planned-task coverage with the same physical identity rules as schedule construction. */
        private long plannedCount(IPatternDetails pattern) {
            PlanIdentity.PatternIdentity identity = identityOf(pattern);
            if (identity == null) {
                Long count = plannedCountsByInstance.get(pattern);
                return count == null ? 0L : count;
            }
            Long count = plannedCountsByIdentity.get(identity);
            return count == null ? 0L : count;
        }

        private boolean hasPlannedTask(IPatternDetails pattern) {
            return plannedTaskIndex ? plannedCount(pattern) > 0L : containsPhysicalPattern(pattern);
        }

        private IdentityBucket lookupByIdentity(PlanIdentity.PatternIdentity identity) {
            return identity == null ? null : byIdentity.get(identity);
        }

        private PlanIdentity.PatternIdentity identityOf(IPatternDetails pattern) {
            if (identityByInstance.containsKey(pattern)) return identityByInstance.get(pattern);
            PlanIdentity.PatternIdentity identity = pattern == null ? null : PlanIdentity.patternIdentityFor(pattern);
            identityByInstance.put(pattern, identity);
            return identity;
        }

        private static final class IdentityBucket {
            private IPatternDetails first;
            private int count;

            private void add(IPatternDetails pattern) {
                if (count++ == 0) first = pattern;
            }
        }
    }
}
