package cn.dancingsnow.neoecoae.core.planner;

/** Precompiled recipe data. Solvers never inspect platform pattern objects or allocate per-key wrappers. */
public interface PatternView<K> {
    int id();
    Object identity();
    K producedKey();
    int inputCount();
    K inputKey(int index);
    /** Exact amount consumed for one firing; the core never reconstructs this from platform stacks. */
    cn.dancingsnow.neoecoae.core.amount.PlannerAmount inputAmount(int index);
    boolean inputFastSupported(int index);
    boolean inputIgnoresComponents(int index);
    int grossOutputCount();
    K grossOutputKey(int index);
    long grossOutputAmount(int index);
    default int outputCount() { return grossOutputCount(); }
    default K outputKey(int index) { return grossOutputKey(index); }
    default long outputAmount(int index) { return grossOutputAmount(index); }
    boolean fastSupported();
    boolean completeForStaticPlanning();
    boolean special();
}
