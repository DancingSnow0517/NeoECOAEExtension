package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.Map;

public final class SolveWorkspace {
    private final KeyCounter inventory;
    private final Map<AEKey, Integer> candidateChoice;

    public SolveWorkspace(KeyCounter inventory, Map<AEKey, Integer> candidateChoice) {
        this.inventory = copy(inventory);
        this.candidateChoice = candidateChoice;
    }
    KeyCounter inventory() { return inventory; }
    Map<AEKey, Integer> candidateChoice() { return candidateChoice; }

    private static KeyCounter copy(KeyCounter source) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) result.add(entry.getKey(), entry.getLongValue());
        return result;
    }
}
