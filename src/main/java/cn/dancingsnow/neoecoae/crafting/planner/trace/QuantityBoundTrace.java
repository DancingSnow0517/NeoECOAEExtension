package cn.dancingsnow.neoecoae.crafting.planner.trace;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Export-only derivation of a global optimistic bound, never a procurement list or execution contract. */
public record QuantityBoundTrace(long requested, Node goal) {
    // An input refers to the revision actually used, not to the latest bound for its key. Even for cyclic
    // recipes this is a DAG: every edge points to an earlier revision of the propagation.
    public record Node(int revision, AEKey key, PlannerAmount stock, PlannerAmount upper, List<Route> routes) {
        public Node { routes = List.copyOf(routes); }
    }
    public record Route(IPatternDetails pattern, PlannerAmount outputPerFiring, PlannerAmount firingsUpper,
                        int slot, PlannerAmount multiplier, List<Input> alternatives) {
        public Route { alternatives = List.copyOf(alternatives); }
    }
    public record Input(Node supply, PlannerAmount units) {}

    /** Walk only the goal's evidence. Shared subproofs appear once, independent of depth. */
    public List<Node> nodes() {
        var nodes = new ArrayList<Node>();
        var pending = new ArrayDeque<Node>();
        var seen = new HashSet<Integer>();
        pending.add(goal);
        seen.add(goal.revision());
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            nodes.add(node);
            for (Route route : node.routes()) for (Input input : route.alternatives()) {
                if (seen.add(input.supply().revision())) pending.addLast(input.supply());
            }
        }
        return nodes;
    }
}
