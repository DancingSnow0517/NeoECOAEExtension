package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.PriorityQueue;

/** Runtime component phases. The list is explicitly execution (supplier-to-consumer) order. */
public record ECOExecutionSchedule(List<ComponentExecutionPhase> phases) {
    public ECOExecutionSchedule { phases = List.copyOf(phases); }
    public record ComponentExecutionPhase(int componentId, Type type, Set<IPatternDetails> patternSet,
            List<IPatternDetails> cycleWitness) {
        public ComponentExecutionPhase { patternSet = Set.copyOf(patternSet); cycleWitness = List.copyOf(cycleWitness); }
    }
    public enum Type { DAG, CYCLE }

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

        boolean orderedCycle = phases.stream().anyMatch(phase -> phase.type() == Type.CYCLE);
        if (orderedCycle && plannedTasks != null) {
            List<IPatternDetails> unassigned = executableTasks.stream()
                .filter(pattern -> !containsPhysicalPattern(assignedPatterns, pattern))
                .toList();
            if (!unassigned.isEmpty()) {
                throw new IllegalStateException(
                    "Cycle execution schedule does not cover " + unassigned.size() + " planned pattern(s)");
            }
        }
        return new ECOExecutionSchedule(orderByExecutableDependencies(phases, components));
    }

    /**
     * Numeric planning may switch to an alternate producer after the structural route was selected. Rebuild
     * phase edges from the final physical patterns so dependencies introduced by that alternate cannot remain
     * behind their consumers in the stale component order.
     */
    private static List<ComponentExecutionPhase> orderByExecutableDependencies(
            List<ComponentExecutionPhase> phases, List<ComponentPlanningResult> components) {
        if (phases.size() < 2) return List.copyOf(phases);

        Map<AEKey, List<Integer>> outputProducers = new HashMap<>();
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            for (IPatternDetails pattern : phases.get(phaseIndex).patternSet()) {
                for (var output : pattern.getOutputs()) {
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
                for (var output : pattern.getOutputs()) {
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
            for (IPatternDetails pattern : phases.get(consumer).patternSet()) {
                for (var input : pattern.getInputs()) {
                    if (input == null) continue;
                    var possibleInputs = input.getPossibleInputs();
                    if (possibleInputs == null || possibleInputs.length == 0 || possibleInputs[0] == null
                            || possibleInputs[0].what() == null) continue;
                    AEKey key = possibleInputs[0].what();
                    Integer selected = selectedProducer.get(key);
                    if (selected != null) {
                        addDependency(outgoing, indegree, selected, consumer);
                    } else {
                        for (int producer : outputProducers.getOrDefault(key, List.of())) {
                            addDependency(outgoing, indegree, producer, consumer);
                        }
                    }
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < indegree.length; i++) if (indegree[i] == 0) ready.add(i);
        List<ComponentExecutionPhase> ordered = new ArrayList<>(phases.size());
        while (!ready.isEmpty()) {
            int phaseIndex = ready.remove();
            ordered.add(phases.get(phaseIndex));
            for (int dependent : outgoing.get(phaseIndex)) {
                if (--indegree[dependent] == 0) ready.add(dependent);
            }
        }
        if (ordered.size() != phases.size()) {
            throw new IllegalStateException(
                "Final executable pattern dependencies contain a cycle outside a solved cycle phase");
        }
        return List.copyOf(ordered);
    }

    private static void addDependency(List<Set<Integer>> outgoing, int[] indegree,
            int producer, int consumer) {
        if (producer == consumer) return;
        if (outgoing.get(producer).add(consumer)) indegree[consumer]++;
    }

    private static boolean produces(IPatternDetails pattern, AEKey key) {
        for (var output : pattern.getOutputs()) {
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
