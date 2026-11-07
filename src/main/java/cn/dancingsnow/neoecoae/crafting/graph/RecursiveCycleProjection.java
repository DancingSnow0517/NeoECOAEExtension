package cn.dancingsnow.neoecoae.crafting.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.KeyAmount;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.RecursionLayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Acyclic occurrences of one recursive module: initial stock, execution checkpoints and final material results. */
final class RecursiveCycleProjection {
    private RecursiveCycleProjection() {}

    static ClientCraftingGraph project(CraftingGraphSnapshot snapshot, int componentId, @Nullable AEKey focusedKey) {
        var cycle = snapshot.cycleGroups().stream().filter(value -> value.componentId() == componentId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown cycle component " + componentId));
        var builder = new Builder(snapshot, cycle);
        var preview = cycle.recursion();
        List<Integer> state = new ArrayList<>();
        List<KeyAmount> initial = preview.complete() ? preview.initialInventory() : cycle.requiredSeed();
        var seeds = RecursiveCycleLedger.amounts(cycle.requiredSeed());
        for (var seed : seeds.entrySet()) {
            state.add(builder.material(new KeyAmount(seed.getKey(), seed.getValue()), Role.SEED, 0));
        }
        for (var value : initial) {
            var extra = value.plannerAmount().subtract(seeds.getOrDefault(value.key(), PlannerAmount.ZERO));
            if (extra.signum() > 0) state.add(builder.material(new KeyAmount(value.key(), extra), Role.STOCK, 0));
        }
        if (state.isEmpty() && preview.complete()) state.add(builder.control(Role.SEED, 0, null));
        int level = 1;
        var previousInventory = RecursiveCycleLedger.amounts(initial);
        if (preview.complete()) {
            for (RecursionLayer layer : preview.layers()) {
                int step = builder.control(Role.STEP, level, layer);
                state.forEach(id -> builder.connect(id, step));
                for (KeyAmount value : layer.addedInputs()) {
                    int input = builder.material(value, Role.INPUT, level - 1, layer);
                    builder.inputs.add(input);
                    builder.connect(input, step);
                }
                state = new ArrayList<>();
                for (KeyAmount value : layer.inventory()) {
                    int stock = builder.material(value, Role.STOCK, level + 1, layer,
                        value.plannerAmount().subtract(previousInventory.getOrDefault(value.key(), PlannerAmount.ZERO)));
                    state.add(stock);
                    builder.connect(step, stock);
                }
                if (state.isEmpty()) state.add(step);
                previousInventory = RecursiveCycleLedger.amounts(layer.inventory());
                level += 2;
            }
        } else {
            for (KeyAmount value : cycle.externalInputs()) {
                int input = builder.material(value, Role.INPUT, 0);
                builder.inputs.add(input);
                state.add(input);
            }
        }
        int unresolved = -1;
        if (!preview.complete()) {
            unresolved = builder.control(Role.UNKNOWN, level, null);
            for (int id : state) builder.connect(id, unresolved);
            state = List.of(unresolved);
        }
        Map<AEKey, List<Integer>> endingStock = new LinkedHashMap<>();
        Map<Integer, PlannerAmount> remainingStock = new LinkedHashMap<>();
        for (int id : state) {
            var node = builder.nodes.get(id);
            if (node.key() != null) {
                endingStock.computeIfAbsent(node.key(), ignored -> new ArrayList<>()).add(id);
                remainingStock.put(id, node.recursion().amount());
            }
        }
        int fallbackRoot = state.get(0);
        int root = fallbackRoot;
        Integer focus = null;
        for (var value : cycle.requiredOutputs()) {
            int output = builder.material(value, Role.OUTPUT, level + 1);
            builder.outputs.add(output);
            if (unresolved >= 0) builder.connect(unresolved, output);
            else builder.connectResult(endingStock.getOrDefault(value.key(), List.of()), remainingStock, output);
            if (root == fallbackRoot || snapshot.rootNodeId() == builder.nodes.get(output).material().nodeId()) root = output;
            if (value.key().equals(focusedKey)) focus = output;
        }
        if (preview.complete()) {
            for (var balance : builder.balances.values()) {
                int retained = builder.material(new KeyAmount(balance.key(), balance.remaining()), Role.RETAINED,
                    level + 1, null, balance.netChange());
                builder.connectResult(endingStock.getOrDefault(balance.key(), List.of()), remainingStock, retained);
            }
        }
        if (focus == null && focusedKey != null) {
            focus = builder.nodes.values().stream().filter(node -> focusedKey.equals(node.key()))
                .map(ClientCraftingGraph.Node::id).findFirst().orElse(null);
        }
        return ClientCraftingGraph.recursive(snapshot, componentId, root, focus, builder.nodes, builder.links,
            builder.inputs, builder.outputs);
    }

    private static final class Builder {
        private final CraftingGraphSnapshot.CycleGroup cycle;
        private final Map<AEKey, CraftingGraphSnapshot.MaterialNode> materials = new LinkedHashMap<>();
        private final Map<AEKey, RecursiveCycleLedger.Balance> balances = new LinkedHashMap<>();
        private final Map<Integer, ClientCraftingGraph.Node> nodes = new LinkedHashMap<>();
        private final List<ClientCraftingGraph.Link> links = new ArrayList<>();
        private final Set<Integer> inputs = new LinkedHashSet<>(), outputs = new LinkedHashSet<>();

        Builder(CraftingGraphSnapshot snapshot, CraftingGraphSnapshot.CycleGroup cycle) {
            this.cycle = cycle;
            snapshot.nodes().forEach(node -> materials.put(node.key(), node));
            RecursiveCycleLedger.summarize(cycle).forEach(value -> balances.put(value.key(), value));
        }

        int material(KeyAmount value, Role role, int level) {
            return material(value, role, level, null);
        }

        int material(KeyAmount value, Role role, int level, @Nullable RecursionLayer layer) {
            return material(value, role, level, layer, null);
        }

        int material(KeyAmount value, Role role, int level, @Nullable RecursionLayer layer,
                @Nullable PlannerAmount delta) {
            int id = nodes.size();
            var source = materials.get(value.key());
            if (source == null) {
                source = new CraftingGraphSnapshot.MaterialNode(-1, value.key(), 0, 0, 0, 0,
                    CraftingGraphSnapshot.MaterialStatus.CYCLE);
            }
            nodes.put(id, new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.MATERIAL,
                value.key().getDisplayName().getString(), value.key(), source, null, cycle, null,
                new RecursiveCycleNode(role, level, value.plannerAmount(), layer, delta, balances.get(value.key()))));
            return id;
        }

        int control(Role role, int level, @Nullable RecursionLayer layer) {
            int id = nodes.size();
            PlannerAmount firings = PlannerAmount.ZERO;
            if (layer != null) for (var pattern : layer.patternTimes()) firings = firings.add(pattern.plannerAmount());
            nodes.put(id, new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.RECURSION, role.name(),
                null, null, null, cycle, null, new RecursiveCycleNode(role, level, firings, layer)));
            return id;
        }

        void connectResult(List<Integer> sources, Map<Integer, PlannerAmount> remainingStock, int result) {
            PlannerAmount demand = nodes.get(result).recursion().amount();
            for (int source : sources) {
                if (demand.signum() <= 0) break;
                PlannerAmount available = remainingStock.get(source);
                PlannerAmount transferred = available.min(demand);
                if (transferred.signum() <= 0) continue;
                connect(source, result, transferred);
                remainingStock.put(source, available.subtract(transferred));
                demand = demand.subtract(transferred);
            }
        }

        void connect(int fromId, int toId) { connect(fromId, toId, null); }

        private void connect(int fromId, int toId, @Nullable PlannerAmount amount) {
            var from = nodes.get(fromId);
            var to = nodes.get(toId);
            var material = from.key() != null ? from : to.key() != null ? to : null;
            links.add(new ClientCraftingGraph.Link(fromId, toId,
                amount != null ? amount : material == null ? PlannerAmount.ZERO : material.recursion().amount(),
                from.key() != null && to.key() == null ? CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT
                    : CraftingGraphSnapshot.EdgeKind.PATTERN_OUTPUT,
                cycle.recursion().complete(), material == null ? -1 : material.id()));
        }
    }
}
