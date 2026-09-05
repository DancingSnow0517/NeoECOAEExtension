package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ECOCraftingAccountingTest {
    private final ECOCraftingTestKey first = new ECOCraftingTestKey("first");
    private final ECOCraftingTestKey variant = new ECOCraftingTestKey("variant");

    @Test
    void snapshotCombinesSlotsWithoutMergingVariantsOrFollowingContainerMutation() {
        var firstSlot = counter(2L);
        var secondSlot = counter(3L);
        secondSlot.add(variant, 7L);
        var snapshot = ECOCraftingAccounting.mergeConsumedInputs(
            new KeyCounter[] { firstSlot, null, secondSlot },
            Arrays.asList(null, new GenericStack(first, 11L), new GenericStack(variant, 13L)));

        firstSlot.remove(first, 2L);
        secondSlot.add(first, 100L);

        assertEquals(Map.of(first, 16L, variant, 20L), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(first, 0L));
    }

    @Test
    void partialBatchAccountingUsesTheSnapshotTakenBeforeProviderMutation() {
        var container = new KeyCounter[] { counter(2L) };
        var firstConsumed = ECOCraftingAccounting.consumedInputs(container);
        container[0].remove(first, 2L);

        var accepted = ECOCraftingAccounting.mergeConsumedInputs(firstConsumed, List.of(new GenericStack(first, 4L)));

        assertEquals(Map.of(first, 6L), accepted);
        assertEquals(Map.of(first, 2L), firstConsumed);
    }

    @Test
    void absentAndNonpositiveInputsDoNotManufactureConsumption() {
        assertEquals(Map.of(), ECOCraftingAccounting.consumedInputs((KeyCounter[]) null));
        assertEquals(Map.of(), ECOCraftingAccounting.consumedInputs((List<GenericStack>) null));
        assertEquals(Map.of(first, 5L), ECOCraftingAccounting.consumedInputs(Arrays.asList(
            null, new GenericStack(first, 0L), new GenericStack(first, -3L), new GenericStack(first, 5L))));
    }

    @Test
    void consumptionOverflowFailsBeforeTheProviderCanOwnInputs() {
        var container = new KeyCounter[] { counter(Long.MAX_VALUE), counter(1L) };
        assertThrows(ArithmeticException.class, () -> ECOCraftingAccounting.consumedInputs(container));
        assertThrows(ArithmeticException.class, () -> ECOCraftingAccounting.mergeConsumedInputs(
            Map.of(first, Long.MAX_VALUE), List.of(new GenericStack(first, 1L))));
        assertEquals(Long.MAX_VALUE, container[0].get(first));
        assertEquals(1L, container[1].get(first));
    }

    @ParameterizedTest
    @CsvSource({ "true,true,5", "true,false,2", "false,true,3", "false,false,0" })
    void rollbackReturnsOnlyInputsOwnedByThisBatch(boolean ownsFirst, boolean extractedExtra, long returned) {
        var inventory = new ListCraftingInventory(key -> {});
        inventory.list.add(first, 100L);

        ECOCraftingBatchDispatcher.rollbackBatchInputs(inventory,
            new KeyCounter[] { counter(2L) }, List.of(new GenericStack(first, 3L)), ownsFirst, extractedExtra);

        assertEquals(100L + returned, inventory.list.get(first));
    }

    private KeyCounter counter(long amount) {
        var counter = new KeyCounter();
        counter.add(first, amount);
        return counter;
    }
}
