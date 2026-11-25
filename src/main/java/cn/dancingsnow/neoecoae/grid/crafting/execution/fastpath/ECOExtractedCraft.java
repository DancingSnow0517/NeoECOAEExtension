package cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath;

import appeng.api.stacks.KeyCounter;

/** Lightweight result of extracting one craft from the CPU inventory. */
public record ECOExtractedCraft(
        KeyCounter[] craftingContainer,
        KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems,
        double patternPower) {
}
