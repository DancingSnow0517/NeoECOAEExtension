package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.stacks.*;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.*;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOExactVirtualLedgerTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    @Test void exactCustodySurvivesPartialDeliveryAndReload() {
        var key = mock(AEKey.class);
        var registries = mock(HolderLookup.Provider.class);
        when(key.toTagGeneric(registries)).thenAnswer(call -> new CompoundTag());
        var count = BigInteger.TEN.pow(28).add(BigInteger.valueOf(17));
        var ledger = new ECOExactVirtualLedger(count, List.of(new GenericStack(key, 9)),
            List.of(new GenericStack(key, 2)), List.of(new GenericStack(key, 1)));
        assertEquals(count.multiply(BigInteger.valueOf(9)), ledger.snapshot(false).get(key));
        assertFalse(ledger.drain(true, (k, offered) -> {
            assertEquals(Long.MAX_VALUE, offered.longValue());
            return 17L;
        }, () -> {}));
        var remaining = count.multiply(BigInteger.valueOf(3)).subtract(BigInteger.valueOf(17));
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registries), any())).thenReturn(key);
            var restored = ECOExactVirtualLedger.read(ledger.write(registries), registries);
            assertEquals(count, restored.crafts());
            assertEquals(remaining, restored.snapshot(true).get(key));
            assertThrows(IllegalStateException.class,
                () -> restored.drain(true, (k, n) -> { throw new IllegalStateException(); }, () -> {}));
            assertEquals(remaining, restored.snapshot(true).get(key));
            assertFalse(restored.drain(false, (k, n) -> n, () -> {}));
            assertEquals(count.multiply(BigInteger.valueOf(9)).subtract(BigInteger.valueOf(Long.MAX_VALUE)),
                restored.snapshot(false).get(key));
        }
    }

    @Test void exactLaneUsesOneSlotAndRejectsBusyOrNonVirtualWorker() {
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        var controller = mock(ECOCraftingSystemBlockEntity.class);
        var cache = mock(ECOCraftingFastPathCache.class);
        var recipe = mock(ECOVerifiedFastPathRecipe.class);
        var key = AEItemKey.of(Items.IRON_INGOT);
        var inputs = List.of(new GenericStack(key, 9));
        var outputs = List.of(new GenericStack(key, 1));
        when(worker.getFastPathCache()).thenReturn(cache);
        when(worker.isControlledBy(controller)).thenReturn(true);
        when(recipe.isIssuedBy(cache)).thenReturn(true);
        when(recipe.isCurrent(anyLong())).thenReturn(true);
        when(recipe.inputsPerCraft()).thenReturn(inputs);
        when(recipe.outputsPerCraft()).thenReturn(outputs);
        when(recipe.remainingPerCraft()).thenReturn(List.of());
        var job = UUID.randomUUID();
        when(recipe.withVirtualBatch(1L, job)).thenReturn(
            new ECOVerifiedVirtualExecution(recipe, 1L, job, inputs, outputs, List.of()));
        var thread = new ECOCraftingThread(worker);
        var count = BigInteger.TEN.pow(28).add(BigInteger.ONE);
        assertFalse(thread.pushExactVirtualBatch(recipe, count, job, controller));
        when(controller.isFullVirtualCraftingMode()).thenReturn(true);
        assertTrue(thread.pushExactVirtualBatch(recipe, count, job, controller));
        assertEquals(count, thread.getExactCraftCount());
        assertEquals(1, thread.getFiniteBatchCraftCount());
        assertFalse(thread.pushExactVirtualBatch(recipe, count, job, controller));
        verify(worker, times(1)).onBatchStarted();
    }
}