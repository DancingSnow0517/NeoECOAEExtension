package cn.dancingsnow.neoecoae.core.graph.presentation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects the selected dependency route into a browseable tree without changing the planner DAG.
 * Shared source nodes become independent lightweight occurrences, while deep occurrences become folders.
 */
public final class CompactTreeProjection<N extends LayoutGraph.Node, L extends LayoutGraph.Link, T extends CompactTreeProjection.TreeNode> {
    private final Factory<N, L, T> factory;
    public static final int DEFAULT_DEPTH = 4;
    public static final int MAX_PROJECTED_NODES = 20_000;

    public CompactTreeProjection(Factory<N, L, T> factory) { this.factory = factory; }

    public Result<N, L, T> project(LayoutGraph source, int maxDepth,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths) {
        return project(source, maxDepth, expandedPaths, fullyExpandedPaths, Set.of());
    }

    public Result<N, L, T> project(LayoutGraph source, int maxDepth,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths, Set<String> collapsedPaths) {
        if (!source.nodes().containsKey(source.rootId())) {
            return null;
        }
        int depthLimit = Math.max(0, maxDepth);
        Map<Integer, List<Dependency>> dependencies = dependencies(source);
        Map<Integer, N> nodes = new LinkedHashMap<>();
        Map<Integer, T> treeNodes = new LinkedHashMap<>();
        List<L> links = new ArrayList<>();
        Set<Integer> usedIds = new HashSet<>(source.nodes().keySet());
        IdAllocator ids = new IdAllocator(usedIds);
        Set<Integer> emittedSources = new HashSet<>();
        emittedSources.add(source.rootId());

        String rootPath = path("", source.rootId(), 0);
        int rootId = addVisibleNode(source, source.rootId(), -1, rootPath, 0, false, nodes, treeNodes);
        expand(source, source.rootId(), rootId, rootPath, 0, depthLimit, false, dependencies,
            expandedPaths, fullyExpandedPaths, collapsedPaths, emittedSources, ids, nodes, treeNodes, links);
        return new Result<>(nodes, links, treeNodes, rootId);
    }

    /** Returns one stable source-id path, including root and target, for search auto-expansion. */
    public static List<String> pathTo(LayoutGraph source, int targetSourceId) {
        if (!source.nodes().containsKey(source.rootId())) return List.of();
        Map<Integer, List<Dependency>> dependencies = dependencies(source);
        Deque<PathFrame> queue = new ArrayDeque<>();
        String rootPath = path("", source.rootId(), 0);
        queue.add(new PathFrame(source.rootId(), rootPath, null));
        Set<Integer> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            PathFrame frame = queue.removeFirst();
            if (frame.sourceId() == targetSourceId) return reconstructPath(frame);
            if (!visited.add(frame.sourceId())) continue;
            List<Dependency> children = dependencies.getOrDefault(frame.sourceId(), List.of());
            for (int i = 0; i < children.size(); i++) {
                Dependency child = children.get(i);
                String childPath = path(frame.path(), child.childId(), i);
                queue.addLast(new PathFrame(child.childId(), childPath, frame));
            }
        }
        return List.of();
    }

    private void expand(LayoutGraph source, int sourceId, int parentId, String parentPath, int depth,
            int depthLimit, boolean fullyExpanded, Map<Integer, List<Dependency>> dependencies,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths, Set<String> collapsedPaths,
            Set<Integer> emittedSources, IdAllocator ids,
            Map<Integer, N> nodes, Map<Integer, T> treeNodes,
            List<L> links) {
        if (nodes.size() >= MAX_PROJECTED_NODES) return;
        Deque<ExpansionFrame> stack = new ArrayDeque<>();
        stack.push(new ExpansionFrame(sourceId, parentId, parentPath, depth, fullyExpanded,
            dependencies.getOrDefault(sourceId, List.of()), 0));
        while (!stack.isEmpty() && nodes.size() < MAX_PROJECTED_NODES) {
            ExpansionFrame frame = stack.peek();
            if (frame.index() >= frame.children().size()) {
                stack.pop();
                continue;
            }
            int i = frame.index();
            frame.increment();
            Dependency dependency = frame.children().get(i);
            String childPath = path(frame.parentPath(), dependency.childId(), i);
            int childDepth = frame.depth() + 1;
            boolean childHasChildren = !dependencies.getOrDefault(dependency.childId(), List.of()).isEmpty();
            boolean childFullyExpanded = frame.fullyExpanded() || fullyExpandedPaths.contains(childPath)
                || fullyExpandedPaths.contains(frame.parentPath());
            boolean childExpanded = expandedPaths.contains(childPath) || childFullyExpanded;
            int childId = ids.next();
            LayoutGraph.Node childSource = source.nodes().get(dependency.childId());
            if (childSource == null) continue;
            boolean reference = !emittedSources.add(dependency.childId())
                || isPathCycle(frame.parentPath(), dependency.childId());

            if (reference) {
                nodes.put(childId, factory.referenceNode(childId, childSource));
                treeNodes.put(childId, factory.treeNode(childId, dependency.childId(), frame.parentId(), childPath,
                    childDepth, false, true, 0, 0, false,
                    childSource.layoutKind() == LayoutGraph.Kind.CYCLE_GROUP
                        || childSource.layoutKind() == LayoutGraph.Kind.CYCLE_CLUSTER, false));
            } else if (childHasChildren && (childDepth > depthLimit || collapsedPaths.contains(childPath)) && !childExpanded) {
                T summary = summarizeFolder(source, childId, dependency.childId(), frame.parentId(), childPath,
                    childDepth, dependencies);
                nodes.put(childId, factory.folderNode(childId, childSource));
                treeNodes.put(childId, summary);
            } else {
                addVisibleNode(source, dependency.childId(), frame.parentId(), childPath, childDepth, false,
                    nodes, treeNodes, childId);
            }
            links.add(factory.link(frame.parentId(), childId, dependency.amount(), dependency.kind(),
                dependency.selected(), dependency.materialNodeId()));

            if (!treeNodes.get(childId).folder() && !treeNodes.get(childId).reference() && childHasChildren) {
                stack.push(new ExpansionFrame(dependency.childId(), childId, childPath, childDepth,
                    childFullyExpanded, dependencies.getOrDefault(dependency.childId(), List.of()), 0));
            }
        }
    }

    private static boolean isPathCycle(String path, int sourceId) {
        return path.contains("/" + sourceId + ":") || path.endsWith("/" + sourceId);
    }

    private static List<String> reconstructPath(PathFrame frame) {
        Deque<String> result = new ArrayDeque<>();
        for (PathFrame current = frame; current != null; current = current.parent()) result.addFirst(current.path());
        return new ArrayList<>(result);
    }

    private int addVisibleNode(LayoutGraph source, int sourceId, int parentId, String path, int depth,
            boolean folder, Map<Integer, N> nodes,
            Map<Integer, T> treeNodes) {
        return addVisibleNode(source, sourceId, parentId, path, depth, folder, nodes, treeNodes, sourceId);
    }

    private int addVisibleNode(LayoutGraph source, int sourceId, int parentId, String path, int depth,
            boolean folder, Map<Integer, N> nodes,
            Map<Integer, T> treeNodes, int projectedId) {
        LayoutGraph.Node sourceNode = source.nodes().get(sourceId);
        nodes.put(projectedId, factory.copyNode(projectedId, sourceNode));
        treeNodes.put(projectedId, factory.treeNode(projectedId, sourceId, parentId, path, depth, folder, false,
            0, 0, false, sourceNode.layoutKind() == LayoutGraph.Kind.CYCLE_GROUP
                || sourceNode.layoutKind() == LayoutGraph.Kind.CYCLE_CLUSTER, false));
        return projectedId;
    }

    private T summarizeFolder(LayoutGraph source, int id, int sourceId, int parentId,
            String path, int depth, Map<Integer, List<Dependency>> dependencies) {
        int count = 0;
        int maxDepth = 0;
        boolean missing = false;
        boolean cycle = false;
        boolean unsupported = false;
        Set<Integer> seen = new HashSet<>();
        Set<Integer> active = new HashSet<>();
        Deque<SummaryFrame> stack = new ArrayDeque<>();
        stack.push(new SummaryFrame(sourceId, 0, false));
        while (!stack.isEmpty()) {
            SummaryFrame frame = stack.pop();
            if (frame.exit()) {
                active.remove(frame.sourceId());
                continue;
            }
            if (active.contains(frame.sourceId())) {
                cycle = true;
                maxDepth = Math.max(maxDepth, frame.depth());
                continue;
            }
            if (!seen.add(frame.sourceId())) continue;
            active.add(frame.sourceId());
            count++;
            maxDepth = Math.max(maxDepth, frame.depth());
            LayoutGraph.Node node = source.nodes().get(frame.sourceId());
            if (node != null) {
                cycle |= node.layoutKind() == LayoutGraph.Kind.CYCLE_GROUP
                    || node.layoutKind() == LayoutGraph.Kind.CYCLE_CLUSTER;
                missing |= node.missing();
                unsupported |= node.unsupported();
            }
            stack.push(new SummaryFrame(frame.sourceId(), frame.depth(), true));
            List<Dependency> children = dependencies.getOrDefault(frame.sourceId(), List.of());
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new SummaryFrame(children.get(i).childId(), frame.depth() + 1, false));
            }
        }
        return factory.treeNode(id, sourceId, parentId, path, depth, true, false, Math.max(0, count - 1),
            maxDepth, missing, cycle, unsupported);
    }

    private static Map<Integer, List<Dependency>> dependencies(LayoutGraph source) {
        Map<Integer, List<LayoutGraph.Link>> outgoing = new HashMap<>();
        for (LayoutGraph.Link link : source.links()) {
            outgoing.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>()).add(link);
        }
        boolean hasSelected = source.links().stream().anyMatch(LayoutGraph.Link::selected);
        Map<Integer, List<Dependency>> result = new HashMap<>();
        for (LayoutGraph.Node node : source.nodes().values()) {
            if (node.layoutKind() == LayoutGraph.Kind.PATTERN) continue;
            List<Dependency> children = new ArrayList<>();
            for (LayoutGraph.Link link : outgoing.getOrDefault(node.id(), List.of())) {
                if (hasSelected && !link.selected()) continue;
                LayoutGraph.Node target = source.nodes().get(link.toId());
                if (target == null) continue;
                if (target.layoutKind() == LayoutGraph.Kind.PATTERN) {
                    for (LayoutGraph.Link input : outgoing.getOrDefault(target.id(), List.of())) {
                        if (hasSelected && !input.selected()) continue;
                        LayoutGraph.Node child = source.nodes().get(input.toId());
                        if (child != null && child.layoutKind() != LayoutGraph.Kind.PATTERN) {
                            children.add(new Dependency(child.id(), input.amount(), input.layoutKind(),
                                link.selected() && input.selected(), input.materialNodeId()));
                        }
                    }
                } else {
                    children.add(new Dependency(target.id(), link.amount(), link.layoutKind(), link.selected(),
                        link.materialNodeId()));
                }
            }
            children.sort(Comparator.comparing((Dependency value) -> label(source.nodes().get(value.childId())))
                .thenComparingInt(Dependency::childId).thenComparingLong(Dependency::amount)
                .thenComparing(value -> value.kind().name()).thenComparingInt(Dependency::materialNodeId));
            LinkedHashSet<String> dedupe = new LinkedHashSet<>();
            children.removeIf(value -> !dedupe.add(value.childId() + ":" + value.amount() + ":" + value.kind()
                + ":" + value.materialNodeId()));
            result.put(node.id(), children);
        }
        return result;
    }

    private static String label(LayoutGraph.Node node) {
        return node == null ? "" : node.label();
    }

    private static String path(String parent, int sourceId, int ordinal) {
        return parent + "/" + sourceId + ":" + ordinal;
    }

    public interface TreeNode {
        boolean folder();
        boolean reference();
    }
    public record Result<N, L, T>(Map<Integer, N> nodes, List<L> links, Map<Integer, T> treeNodes, int rootId) {}
    public interface Factory<N extends LayoutGraph.Node, L extends LayoutGraph.Link, T extends TreeNode> {
        N copyNode(int id, LayoutGraph.Node source);
        N referenceNode(int id, LayoutGraph.Node source);
        N folderNode(int id, LayoutGraph.Node source);
        L link(int from, int to, long amount, LayoutGraph.EdgeKind kind, boolean selected, int material);
        T treeNode(int id, int source, int parent, String path, int depth, boolean folder, boolean reference,
            int hiddenNodes, int hiddenDepth, boolean missing, boolean cycle, boolean unsupported);
    }

    private record Dependency(int childId, long amount, LayoutGraph.EdgeKind kind, boolean selected,
            int materialNodeId) {}
    private record PathFrame(int sourceId, String path, PathFrame parent) {}

    private static final class ExpansionFrame {
        private final int sourceId;
        private final int parentId;
        private final String parentPath;
        private final int depth;
        private final boolean fullyExpanded;
        private final List<Dependency> children;
        private int index;

        private ExpansionFrame(int sourceId, int parentId, String parentPath, int depth, boolean fullyExpanded,
                List<Dependency> children, int index) {
            this.sourceId = sourceId;
            this.parentId = parentId;
            this.parentPath = parentPath;
            this.depth = depth;
            this.fullyExpanded = fullyExpanded;
            this.children = children;
            this.index = index;
        }

        private int index() { return index; }
        private void increment() { index++; }
        private int parentId() { return parentId; }
        private int depth() { return depth; }
        private String parentPath() { return parentPath; }
        private boolean fullyExpanded() { return fullyExpanded; }
        private List<Dependency> children() { return children; }
    }
    private record SummaryFrame(int sourceId, int depth, boolean exit) {}

    private static final class IdAllocator {
        private final Set<Integer> used;
        private int next = Integer.MAX_VALUE;

        private IdAllocator(Set<Integer> used) { this.used = used; }

        private int next() {
            while (used.contains(next)) next--;
            int result = next--;
            used.add(result);
            return result;
        }
    }
}
