package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Iterator;
import java.util.UUID;

public interface ECOExternalCpuJobView {
    Iterator<Task> tasks();

    ListCraftingInventory inventory();

    ListCraftingInventory waitingFor();

    GenericStack finalOutput();

    long remainingOutputAmount();

    UUID craftingId();

    void addContainerMaxItems(long amount, AEKeyType keyType);

    void markDirty();

    /**
     * Returns the amount of final output items that have been completed by workers
     * and accepted into a delivery buffer, but not yet delivered to the requester.
     * This is used together with waitingFor() to calculate the total in-flight amount
     * for limiting batch dispatch.
     */
    long bufferedFinalOutputAmount();

    interface Task {
        IPatternDetails details();

        long remaining();

        void remaining(long value);
    }
}
