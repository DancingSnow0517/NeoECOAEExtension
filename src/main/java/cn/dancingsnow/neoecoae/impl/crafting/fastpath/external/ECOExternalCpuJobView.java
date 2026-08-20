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

    interface Task {
        IPatternDetails details();

        long remaining();

        void remaining(long value);
    }
}
