package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ECOBatchCraftingWorkTest {
    @Test
    void batchWorkCannotUseZeroOrMoreSlotsThanCrafts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ECOBatchCraftingWork(8, List.of(), List.of(), List.of(), null, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ECOBatchCraftingWork(8, List.of(), List.of(), List.of(), null, 0, 9));
    }
}
