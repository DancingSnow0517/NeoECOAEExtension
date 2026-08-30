package cn.dancingsnow.neoecoae.client.craftinggraph;

import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
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
public final class CompactTreeProjection {
    public static final int DEFAULT_DEPTH = 4;
    public static final int MAX_PROJECTED_NODES = 20_000;

    private CompactTreeProjection() {}

    public static ClientCraftingGraph project(ClientCraftingGraph source, int maxDepth,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths) {
        return project(source, maxDepth, expandedPaths, fullyExpandedPaths, Set.of());
    }

    public static ClientCraftingGraph project(ClientCraftingGraph source, int maxDepth,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths, Set<String> collapsedPaths) {
        if (source.view() != ClientCraftingGraph.View.MAIN || !source.nodes().containsKey(source.rootId())) {
            return source;
        }
        int depthLimit = Math.max(0, maxDepth);
        Map<Integer, List<Dependency>> dependencies = dependencies(source);
        Map<Integer, ClientCraftingGraph.Node> nodes = new LinkedHashMap<>();
        Map<Integer, CompactTreeNode> treeNodes = new LinkedHashMap<>();
        List<ClientCraftingGraph.Link> links = new ArrayList<>();
        Set<Integer> usedIds = new HashSet<>(source.nodes().keySet());
        IdAllocator ids = new IdAllocator(usedIds);
        Set<Integer> emittedSources = new HashSet<>();
        emittedSources.add(source.rootId());

        String rootPath = path("", source.rootId(), 0);
        int rootId = addVisibleNode(source, source.rootId(), -1, rootPath, 0, false, nodes, treeNodes);
        expand(source, source.rootId(), rootId, rootPath, 0, depthLimit, false, dependencies,
            expandedPaths, fullyExpandedPaths, collapsedPaths, emittedSources, ids, nodes, treeNodes, links);
        return ClientCraftingGraph.compact(source, nodes, links, treeNodes, rootId);
    }

    /** Returns one stable source-id path, including root and target, for search auto-expansion. */
    public static List<String> pathTo(ClientCraftingGraph source, int targetSourceId) {
        if (source.view() != ClientCraftingGraph.View.MAIN || !source.nodes().containsKey(source.rootId())) return List.of();
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

    private static void expand(ClientCraftingGraph source, int sourceId, int parentId, String parentPath, int depth,
            int depthLimit, boolean fullyExpanded, Map<Integer, List<Dependency>> dependencies,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths, Set<String> collapsedPaths,
            Set<Integer> emittedSources, IdAllocator ids,
            Map<Integer, ClientCraftingGraph.Node> nodes, Map<Integer, CompactTreeNode> treeNodes,
            List<ClientCraftingGraph.Link> links) {
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
            ClientCraftingGraph.Node childSource = source.nodes().get(dependency.childId());
            if (childSource == null) continue;
            boolean reference = !emittedSources.add(dependency.childId())
                || isPathCycle(frame.parentPath(), dependency.childId());

            if (reference) {
                nodes.put(childId, new ClientCraftingGraph.Node(childId, ClientCraftingGraph.Kind.REFERENCE,
                    "↪ " + childSource.label(), childSource.key(), childSource.material(), childSource.pattern(),
                    childSource.cycle()));
                treeNodes.put(childId, new CompactTreeNode(childId, dependency.childId(), frame.parentId(), childPath,
                    childDepth, false, true, 0, 0, false,
                    childSource.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP, false));
            } else if (childHasChildren && (childDepth > depthLimit || collapsedPaths.contains(childPath)) && !childExpanded) {
                CompactTreeNode summary = summarizeFolder(source, childId, dependency.childId(), frame.parentId(), childPath,
                    childDepth, dependencies);
                nodes.put(childId, new ClientCraftingGraph.Node(childId, ClientCraftingGraph.Kind.FOLDER,
                    childSource.label(), null, null, null, null));
                treeNodes.put(childId, summary);
            } else {
                addVisibleNode(source, dependency.childId(), frame.parentId(), childPath, childDepth, false,
                    nodes, treeNodes, childId);
            }
            links.add(new ClientCraftingGraph.Link(frame.parentId(), childId, dependency.amount(), dependency.kind(),
                dependency.selected()));

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
        return List.copyOf(result);
    }

    private static int addVisibleNode(ClientCraftingGraph source, int sourceId, int parentId, String path, int depth,
            boolean folder, Map<Integer, ClientCraftingGraph.Node> nodes,
            Map<Integer, CompactTreeNode> treeNodes) {
        return addVisibleNode(source, sourceId, parentId, path, depth, folder, nodes, treeNodes, sourceId);
    }

    private static int addVisibleNode(ClientCraftingGraph source, int sourceId, int parentId, String path, int depth,
            boolean folder, Map<Integer, ClientCraftingGraph.Node> nodes,
            Map<Integer, CompactTreeNode> treeNodes, int projectedId) {
        ClientCraftingGraph.Node sourceNode = source.nodes().get(sourceId);
        nodes.put(projectedId, copyNode(projectedId, sourceNode));
        treeNodes.put(projectedId, new CompactTreeNode(projectedId, sourceId, parentId, path, depth, folder, false,
            0, 0, false, sourceNode.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP, false));
        return projectedId;
    }

    private static ClientCraftingGraph.Node copyNode(int id, ClientCraftingGraph.Node source) {
        return new ClientCraftingGraph.Node(id, source.kind(), source.label(), source.key(), source.material(),
            source.pattern(), source.cycle());
    }

    private static CompactTreeNode summarizeFolder(ClientCraftingGraph source, int id, int sourceId, int parentId,
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
            ClientCraftingGraph.Node node = source.nodes().get(frame.sourceId());
            if (node != null) {
                cycle |= node.kind() == ClientCraftingGraph.Kind.CYCLE_GROUP;
                if (node.material() != null) {
                    missing |= node.material().status() == CraftingGraphSnapshot.MaterialStatus.MISSING;
                    unsupported |= node.material().status() == CraftingGraphSnapshot.MaterialStatus.UNSUPPORTED;
                }
            }
            stack.push(new SummaryFrame(frame.sourceId(), frame.depth(), true));
            List<Dependency> children = dependencies.getOrDefault(frame.sourceId(), List.of());
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new SummaryFrame(children.get(i).childId(), frame.depth() + 1, false));
            }
        }
        return new CompactTreeNode(id, sourceId, parentId, path, depth, true, false, Math.max(0, count - 1),
            maxDepth, missing, cycle, unsupported);
    }

    private static Map<Integer, List<Dependency>> dependencies(ClientCraftingGraph source) {
        Map<Integer, List<ClientCraftingGraph.Link>> outgoing = new HashMap<>();
        for (ClientCraftingGraph.Link link : source.links()) {
            outgoing.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>()).add(link);
        }
        boolean hasSelected = source.links().stream().anyMatch(ClientCraftingGraph.Link::selected);
        Map<Integer, List<Dependency>> result = new HashMap<>();
        for (ClientCraftingGraph.Node node : source.nodes().values()) {
            if (node.kind() == ClientCraftingGraph.Kind.PATTERN) continue;
            List<Dependency> children = new ArrayList<>();
            for (ClientCraftingGraph.Link link : outgoing.getOrDefault(node.id(), List.of())) {
                if (hasSelected && !link.selected()) continue;
                ClientCraftingGraph.Node target = source.nodes().get(link.toId());
                if (target == null) continue;
                if (target.kind() == ClientCraftingGraph.Kind.PATTERN) {
                    for (ClientCraftingGraph.Link input : outgoing.getOrDefault(target.id(), List.of())) {
                        if (hasSelected && !input.selected()) continue;
                        ClientCraftingGraph.Node child = source.nodes().get(input.toId());
                        if (child != null && child.kind() != ClientCraftingGraph.Kind.PATTERN) {
                            children.add(new Dependency(child.id(), input.amount(), input.kind(),
                                link.selected() && input.selected()));
                        }
                    }
                } else {
                    children.add(new Dependency(target.id(), link.amount(), link.kind(), link.selected()));
                }
            }
            children.sort(Comparator.comparing((Dependency value) -> label(source.nodes().get(value.childId())))
                .thenComparingInt(Dependency::childId).thenComparingLong(Dependency::amount)
                .thenComparing(value -> value.kind().name()));
            LinkedHashSet<String> dedupe = new LinkedHashSet<>();
            children.removeIf(value -> !dedupe.add(value.childId() + ":" + value.amount() + ":" + value.kind()));
            result.put(node.id(), List.copyOf(children));
        }
        return result;
    }

    private static String label(ClientCraftingGraph.Node node) {
        return node == null ? "" : node.label();
    }

    private static String path(String parent, int sourceId, int ordinal) {
        return parent + "/" + sourceId + ":" + ordinal;
    }

    private record Dependency(int childId, long amount, CraftingGraphSnapshot.EdgeKind kind, boolean selected) {}
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
