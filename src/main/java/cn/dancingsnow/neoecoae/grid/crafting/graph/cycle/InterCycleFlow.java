package cn.dancingsnow.neoecoae.grid.crafting.graph.cycle;

/** One material-preserving edge in the condensation DAG between two distinct planner SCCs. */
public record InterCycleFlow(int fromComponentId, int toComponentId, int materialNodeId,
        int producerPatternId, int consumerPatternId, long outputAmount, long inputAmount) {
}
