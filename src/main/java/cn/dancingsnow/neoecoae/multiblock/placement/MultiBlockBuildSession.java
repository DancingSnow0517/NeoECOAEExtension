package cn.dancingsnow.neoecoae.multiblock.placement;

import lombok.Getter;

import java.util.List;

public class MultiBlockBuildSession {
    private final List<WorldPlannedBlock> pendingBlocks;
    @Getter
    private final int totalBlocks;
    private int nextBlockIndex;
    private int waitTicks;

    public MultiBlockBuildSession(List<WorldPlannedBlock> pendingBlocks, int initialDelay) {
        this.pendingBlocks = List.copyOf(pendingBlocks);
        this.totalBlocks = pendingBlocks.size();
        this.waitTicks = Math.max(0, initialDelay);
    }

    public boolean isFinished() {
        return nextBlockIndex >= pendingBlocks.size();
    }

    public boolean tickDelay() {
        if (waitTicks > 0) {
            waitTicks--;
            return false;
        }
        return true;
    }

    public WorldPlannedBlock getCurrentBlock() {
        return pendingBlocks.get(nextBlockIndex);
    }

    public void advance(int nextDelay) {
        nextBlockIndex++;
        waitTicks = Math.max(0, nextDelay);
    }

    public int getPlacedBlockCount() {
        return nextBlockIndex;
    }

    public int getRemainingBlockCount() {
        return totalBlocks - nextBlockIndex;
    }

}