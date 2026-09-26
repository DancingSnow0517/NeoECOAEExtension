package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;

/** Internal ledger bridge shared by native AE2/OmniCell and AdvancedAE CPUs. */
public interface ECOExternalCpuJob {
    Map<IPatternDetails, ?> neoecoae$tasks();
    ListCraftingInventory neoecoae$waitingFor();
    CraftingLink neoecoae$link();
    GenericStack neoecoae$finalOutput();
    boolean neoecoae$suspended();
    void neoecoae$suspended(boolean value);
    void neoecoae$addRemainderItems(long amount, AEKeyType type);
    interface Task {
        long neoecoae$value();
        void neoecoae$value(long value);
    }
}
