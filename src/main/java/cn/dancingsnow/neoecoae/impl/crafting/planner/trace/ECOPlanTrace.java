package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explanation-only DTO. Solver decisions never depend on this object. */
public final class ECOPlanTrace {
    private final List<PlanTraceNode> nodes = new ArrayList<>();
    private final List<PlanTraceEdge> edges = new ArrayList<>();
    private final List<ComponentTrace> components = new ArrayList<>();
    private final List<CycleTrace> cycles = new ArrayList<>();
    private final List<PlannerDiagnostic> diagnostics = new ArrayList<>();

    public void addNode(PlanTraceNode node) { nodes.add(node); }
    public void addEdge(PlanTraceEdge edge) { edges.add(edge); }
    public void addComponent(ComponentTrace component) { components.add(component); }
    public void addCycle(CycleTrace cycle) { cycles.add(cycle); }
    public void addDiagnostic(PlannerDiagnostic diagnostic) { diagnostics.add(diagnostic); }
    public List<PlanTraceNode> nodes() { return Collections.unmodifiableList(nodes); }
    public List<PlanTraceEdge> edges() { return Collections.unmodifiableList(edges); }
    public List<ComponentTrace> components() { return Collections.unmodifiableList(components); }
    public List<CycleTrace> cycles() { return Collections.unmodifiableList(cycles); }
    public List<PlannerDiagnostic> diagnostics() { return Collections.unmodifiableList(diagnostics); }
}
