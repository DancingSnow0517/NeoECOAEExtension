package cn.dancingsnow.neoecoae.crafting.execution;

/** AE2 dispatch boundary for the loader-independent per-pass ledger. */
final class ECOCraftingDispatchBudget extends cn.dancingsnow.neoecoae.core.execution.DispatchBudget {
    ECOCraftingDispatchBudget(int ordinaryLimit, int probeLimit) {
        super(ordinaryLimit, probeLimit);
    }
}
