package cn.dancingsnow.neoecoae.multiblock.placement;

import java.util.List;
import lombok.Getter;

public class MultiBlockBuildSession {
    private final List<WorldPlannedBlock> pendingBlocks;

    @Getter
    private final int totalBlocks;

    private int nextBlockIndex;
    private int waitTicks;
    private int skippedBlockCount;

    public MultiBlockBuildSession(List<WorldPlannedBlock> pendingBlocks, int initialDelay) {
        this(pendingBlocks, initialDelay, 0);
    }

    public MultiBlockBuildSession(List<WorldPlannedBlock> pendingBlocks, int initialDelay, int initialSkippedBlocks) {
        this.pendingBlocks = List.copyOf(pendingBlocks);
        this.totalBlocks = pendingBlocks.size();
        this.waitTicks = Math.max(0, initialDelay);
        this.skippedBlockCount = Math.max(0, initialSkippedBlocks);
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

    public void skip(int nextDelay) {
        skippedBlockCount++;
        advance(nextDelay);
    }

    public int getPlacedBlockCount() {
        return nextBlockIndex;
    }

    public int getRemainingBlockCount() {
        return totalBlocks - nextBlockIndex;
    }

    public int getSkippedBlockCount() {
        return skippedBlockCount;
    }
}
