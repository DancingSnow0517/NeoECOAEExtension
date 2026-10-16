package cn.dancingsnow.neoecoae.core.execution;

import java.util.BitSet;

/** DAG dependencies stream; only explicitly barriered producers must finish before consumers dispatch. */
public final class PhaseBarriers {
    private final int[][] dependencies;
    private final boolean[] barriered;

    public PhaseBarriers(int[][] dependencies, boolean[] barriered) {
        if (dependencies.length != barriered.length) throw new IllegalArgumentException("Phase layout shape changed");
        this.dependencies = new int[dependencies.length][];
        this.barriered = barriered.clone();
        for (int i = 0; i < dependencies.length; i++) this.dependencies[i] = dependencies[i].clone();
    }

    public boolean ready(int phase, BitSet completed) {
        for (int dependency : dependencies[phase]) {
            if (barriered[dependency] && !completed.get(dependency)) return false;
        }
        return true;
    }
}
