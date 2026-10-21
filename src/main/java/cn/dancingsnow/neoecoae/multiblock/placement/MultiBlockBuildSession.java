package cn.dancingsnow.neoecoae.multiblock.placement;

import java.util.List;

public class MultiBlockBuildSession {
    private final List<WorldPlannedBlock> pendingBlocks;
    private final int totalBlocks;
    private int nextBlockIndex;
    private int waitTicks;
    private int skippedBlocks;
    public int getRemainingBlockCount(){return pendingBlocks.size()-nextBlockIndex;}
    public int getPlacedBlockCount(){return nextBlockIndex-skippedBlocks;}
    public int getSkippedBlockCount(){return skippedBlocks;}
    public int getTotalBlocks(){return totalBlocks;}
    public void skip(int delay){skippedBlocks++;advance(delay);}

    public MultiBlockBuildSession(List<WorldPlannedBlock> pendingBlocks, int initialDelay) {
        this.pendingBlocks = pendingBlocks;
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

}
