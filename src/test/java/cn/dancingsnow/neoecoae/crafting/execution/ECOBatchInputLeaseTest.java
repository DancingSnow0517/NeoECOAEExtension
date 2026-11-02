package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchInputLease;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOBatchInputLeaseTest {
    @Test
    void explicitRejectionRestoresExactlyWhatWasExtracted() {
        AEKey key = mock(AEKey.class);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 20L, Actionable.MODULATE);

        var transaction = ECOBatchInputLease.acquire(inventory, List.of(new GenericStack(key, 12L)));
        assertNotNull(transaction);
        assertEquals(8L, inventory.list.get(key));
        transaction.rollback();
        transaction.rollback();
        assertEquals(20L, inventory.list.get(key));
    }

    @Test
    void acceptedOrAmbiguousOwnershipNeverReplaysInputs() {
        AEKey key = mock(AEKey.class);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 20L, Actionable.MODULATE);

        var transaction = ECOBatchInputLease.acquire(inventory, List.of(new GenericStack(key, 12L)));
        assertNotNull(transaction);
        transaction.transferOwnership();
        transaction.rollback();
        assertEquals(8L, inventory.list.get(key));
    }
}
