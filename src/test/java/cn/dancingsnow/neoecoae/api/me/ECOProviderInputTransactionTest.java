package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOProviderInputTransactionTest {
    @Test
    void explicitRejectionRestoresExactlyWhatWasExtracted() {
        AEKey key = mock(AEKey.class);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, 20L, Actionable.MODULATE);

        var transaction = ECOProviderInputTransaction.begin(inventory, List.of(new GenericStack(key, 12L)));
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

        var transaction = ECOProviderInputTransaction.begin(inventory, List.of(new GenericStack(key, 12L)));
        assertNotNull(transaction);
        transaction.transferOwnership();
        transaction.rollback();
        assertEquals(8L, inventory.list.get(key));
    }
}
