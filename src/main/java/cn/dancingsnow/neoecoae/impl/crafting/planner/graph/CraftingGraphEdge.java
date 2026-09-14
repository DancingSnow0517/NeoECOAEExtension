package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;

/** A candidate pattern's producer -> logical required-input relationship. */
public record CraftingGraphEdge(
    AEKey producer,
    AEKey requiredInput,
    CompiledPattern pattern,
    CompiledInput input
) {
}
