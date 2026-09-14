package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.Map;

public final class SolveWorkspace {
    private final PlannerInventorySnapshot inventory;
    private final Map<AEKey, Integer> candidateChoice;

    public SolveWorkspace(KeyCounter inventory, Map<AEKey, Integer> candidateChoice) {
        this(PlannerInventorySnapshot.of(inventory), candidateChoice);
    }

    public SolveWorkspace(PlannerInventorySnapshot inventory, Map<AEKey, Integer> candidateChoice) {
        this.inventory = inventory;
        this.candidateChoice = candidateChoice;
    }
    PlannerInventorySnapshot inventory() { return inventory; }
    Map<AEKey, Integer> candidateChoice() { return candidateChoice; }

}
