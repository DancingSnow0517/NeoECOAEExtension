package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapters;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.PriorityQueue;
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
    public enum Type { DAG, CYCLE }

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
            if (!ECOExecutionRequirement.componentIsOrdered(component)) continue;
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
                    && !ECOExecutionRequirement.componentIsOrdered(c)) continue;
            Type type = c.type() == ComponentPlanningResult.Type.CYCLIC ? Type.CYCLE : Type.DAG;
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
            phases.add(new ComponentExecutionPhase(id, type, patterns, witness));
        }

        if (plannedTasks != null) {
            List<IPatternDetails> unassigned = executableTasks.stream()
                .filter(pattern -> !containsPhysicalPattern(assignedPatterns, pattern))
                .toList();
            if (!unassigned.isEmpty()) {
                throw new IllegalStateException(
                    "Execution schedule does not cover " + unassigned.size() + " planned pattern(s)");
            }
        }
        OrderedSchedule ordered = orderByExecutableDependencies(phases, components);
        return new ECOExecutionSchedule(ordered.phases(), ordered.dependencies());
    }

    /**
     * Numeric planning may switch to an alternate producer after the structural route was selected. Rebuild
     * phase edges from the final physical patterns so dependencies introduced by that alternate cannot remain
     * behind their consumers in the stale component order.
     */
    private static OrderedSchedule orderByExecutableDependencies(
            List<ComponentExecutionPhase> phases, List<ComponentPlanningResult> components) {
        if (phases.size() < 2) return new OrderedSchedule(List.copyOf(phases), List.of());

        Map<AEKey, List<Integer>> outputProducers = new HashMap<>();
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            for (IPatternDetails pattern : phases.get(phaseIndex).patternSet()) {
                for (var output : producedAndReturned(pattern)) {
                    if (output == null || output.what() == null || output.amount() <= 0L) continue;
                    List<Integer> producers = outputProducers.computeIfAbsent(
                        output.what(), ignored -> new ArrayList<>());
                    if (!producers.contains(phaseIndex)) producers.add(phaseIndex);
                }
            }
        }

        // requiredOutputs identifies which selected pattern is the intentional producer for a material. This
        // disambiguates incidental byproducts when several planned patterns happen to emit the same key.
        Map<AEKey, Integer> selectedProducer = new HashMap<>();
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            if (phases.get(phaseIndex).type() != Type.CYCLE) continue;
            for (IPatternDetails pattern : phases.get(phaseIndex).patternSet()) {
                for (var output : producedAndReturned(pattern)) {
                    if (output != null && output.what() != null && output.amount() > 0L) {
                        selectedProducer.putIfAbsent(output.what(), phaseIndex);
                    }
                }
            }
        }
        for (ComponentPlanningResult component : components) {
            for (AEKey key : component.requiredOutputs().keySet()) {
                Integer owner = null;
                for (IPatternDetails pattern : component.executionPatterns()) {
                    if (!produces(pattern, key)) continue;
                    int phaseIndex = phaseIndexFor(phases, pattern);
                    if (phaseIndex < 0) continue;
                    if (owner != null && owner != phaseIndex) {
                        owner = null;
                        break;
                    }
                    owner = phaseIndex;
                }
                if (owner != null) selectedProducer.put(key, owner);
            }
        }

        List<Set<Integer>> outgoing = new ArrayList<>(phases.size());
        int[] indegree = new int[phases.size()];
        for (int i = 0; i < phases.size(); i++) outgoing.add(new LinkedHashSet<>());
        for (int consumer = 0; consumer < phases.size(); consumer++) {
            int consumerPhase = consumer;
            for (IPatternDetails pattern : phases.get(consumer).patternSet()) {
                PatternSemantics semantics = semantic(pattern);
                for (var input : semantics.consumedInputs()) {
                    AEKey key = input.key();
                    Integer selected = selectedProducer.get(key);
                    List<Integer> producers;
                    if (selected != null && selected != consumerPhase) {
                        producers = List.of(selected);
                    } else if (selected != null) {
                        // A pattern may return/re-emit one of its own inputs. That self-production is not a
                        // substitute for a different planned producer of the same key; retain the other producer
                        // edges so a dynamic/catalyst pattern cannot be placed before its actual supplier.
                        producers = outputProducers.getOrDefault(key, List.of()).stream()
                            .filter(producer -> producer != consumerPhase)
                            .toList();
                    } else {
                        producers = outputProducers.getOrDefault(key, List.of());
                    }
                    for (int producer : producers) {
                        logDependency(phases, producer, consumerPhase, pattern, key, semantics,
                            addDependency(outgoing, indegree, producer, consumerPhase));
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
            throw new IllegalStateException(
                "Final executable pattern dependencies contain a cycle outside a solved cycle phase");
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
        return new OrderedSchedule(List.copyOf(ordered), List.copyOf(dependencies));
    }

    private record OrderedSchedule(List<ComponentExecutionPhase> phases,
            List<PhaseDependency> dependencies) { }

    private static boolean addDependency(List<Set<Integer>> outgoing, int[] indegree,
            int producer, int consumer) {
        if (producer == consumer) return false;
        if (outgoing.get(producer).add(consumer)) {
            indegree[consumer]++;
            return true;
        }
        return false;
    }

    private static boolean produces(IPatternDetails pattern, AEKey key) {
        for (var output : producedAndReturned(pattern)) {
            if (output != null && output.what() != null && output.amount() > 0L && key.equals(output.what())) {
                return true;
            }
        }
        return false;
    }

    private static int phaseIndexFor(List<ComponentExecutionPhase> phases, IPatternDetails pattern) {
        for (int i = 0; i < phases.size(); i++) {
            if (containsPhysicalPattern(phases.get(i).patternSet(), pattern)) return i;
        }
        return -1;
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
        PatternSemanticAdapter adapter = PatternSemanticAdapters.find(PatternSemanticAdapters.defaults(), pattern);
        if (adapter == null) return new cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter()
            .analyze(pattern);
        try {
            return adapter.analyze(pattern);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(pattern, null,
                "SEMANTIC_ANALYSIS_FAILED:" + rejected.getClass().getSimpleName());
        }
    }

    private static List<GenericStack> producedAndReturned(IPatternDetails pattern) {
        PatternSemantics semantics = semantic(pattern);
        List<GenericStack> result = new ArrayList<>(semantics.producedOutputs());
        result.addAll(semantics.returnedOutputs());
        try {
            // Keep the raw AE2 output contract as a compatibility backstop for an integration adapter that can
            // describe inputs but not every concrete output. Duplicates only affect the local producer lookup.
            result.addAll(pattern.getOutputs());
        } catch (RuntimeException ignored) {
            // A malformed pattern cannot add a dependency edge.
        }
        return result;
    }

    private static void logDependency(List<ComponentExecutionPhase> phases, int producer, int consumer,
            IPatternDetails consumerPattern, AEKey key, PatternSemantics semantics, boolean edgeAdded) {
        if (!LOGGER.isDebugEnabled()) return;
        IPatternDetails producerPattern = phases.get(producer).patternSet().stream()
            .filter(pattern -> producedAndReturned(pattern).stream().anyMatch(output -> output != null
                && key.equals(output.what())))
            .findFirst()
            .orElseGet(() -> phases.get(producer).patternSet().stream().findFirst().orElse(null));
        LOGGER.debug("[ECO-DEPENDENCY] producer={} consumer={} key={} producerPhase={} consumerPhase={} "
                + "edgeAdded={} matchMode={} definition={}",
            producerPattern, consumerPattern, key, producer, consumer, edgeAdded, semantics.matchingMode(),
            semantics.physicalDefinition());
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
