package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOExactBatchPlanner;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalBigIntegerTarget;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOUselessExactCraftingDispatchTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    @Test void exactCommitUsesReleasedNativeEntryPointBeyondWindowAndRollsBackRejection() {
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        var target = new OmniversalBigIntegerTarget(core, "test");
        var pattern = mock(IMolecularAssemblerSupportedPattern.class);
        var input = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.IRON_BLOCK);
        var copies = BigInteger.TEN.pow(28).add(BigInteger.valueOf(17));
        var context = new ECOBatchDispatchContext(pattern,
            List.of(List.of(new GenericStack(input, 9))),
            List.of(new GenericStack(output, 1)), List.of(), null, null);
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.restore(Map.of(input, copies.multiply(BigInteger.valueOf(9))));
        var provider = mock(ECOFastPathDispatchProvider.class);
        when(provider.eco$prepareExactFastPath(any(), any())).thenAnswer(call ->
            ECOUselessExactCraftingDispatch.prepare(target, call.getArgument(0), call.getArgument(1)));
        var energy = mock(ECOFastPathFacade.Reservation.class);

        var rejected = ECOExactBatchPlanner.prepare(provider, context, inventory, copies, Map.of());
        assertNotNull(rejected);
        assertEquals(copies, rejected.craftCount());
        assertFalse(rejected.submit(energy));
        assertEquals(copies.multiply(BigInteger.valueOf(9)), inventory.amount(input));
        verify(energy).refund();

        when(core.pushBigIntegerBatch(eq(pattern), eq(copies), any(), isNull())).thenAnswer(call -> {
            KeyCounter[] receipt = call.getArgument(2);
            assertEquals(9, receipt[0].get(input));
            assertEquals(BigInteger.ZERO, inventory.amount(input));
            receipt[0].clear();
            return true;
        });
        var accepted = ECOExactBatchPlanner.prepare(provider, context, inventory, copies, Map.of());
        assertTrue(accepted.submit(energy));
        assertEquals(copies, accepted.outputs().get(output));
        assertEquals(BigInteger.ZERO, inventory.amount(input));
        verify(energy).commit();
        assertThrows(IllegalStateException.class, () -> accepted.submit(energy));
    }

    @Test void releasedManagerAssemblesOnceAndQueuesExactOutputsBeyondLong() throws Exception {
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        when(core.getLevel()).thenReturn(level);
        when(core.isTaskExecutionEnabled()).thenReturn(true);
        var manager = new com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager(core);
        when(core.pushBigIntegerBatch(any(), any(), any(), isNull())).thenAnswer(call ->
            manager.pushBigIntegerBatch(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        var pattern = mock(IMolecularAssemblerSupportedPattern.class);
        when(pattern.assemble(any(), eq(level))).thenReturn(new net.minecraft.world.item.ItemStack(Items.IRON_BLOCK, 2));
        when(pattern.getRemainingItems(any())).thenReturn(net.minecraft.core.NonNullList.create());
        var input = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.IRON_BLOCK);
        var copies = BigInteger.TEN.pow(28).add(BigInteger.valueOf(17));
        var context = new ECOBatchDispatchContext(pattern,
            List.of(List.of(new GenericStack(input, 9))),
            List.of(new GenericStack(output, 2)), List.of(), level, null);
        var target = new OmniversalBigIntegerTarget(core, "test");
        // Verify the actual released admission is limited, while the native commit can retain the full count.
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 2)));
        when(core.getMaxAETaskCount()).thenReturn(2);
        when(core.outputSegmentBudget()).thenReturn(2L);
        try (var budget = mockStatic(com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceTickBudget.class);
             var diagnostics = mockStatic(com.sorrowmist.useless.integration.dataenergistics.TrinityDispatchDiagnostics.class)) {
            budget.when(() -> com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceTickBudget.applyScale(any(BigInteger.class)))
                .thenAnswer(call -> call.getArgument(0));
            assertTrue(target.capacity(pattern, context.inputCounters(), copies).accepted().compareTo(copies) < 0);
            var adapter = new ECOUselessBatchProviderBridge.BigIntegerAdapter(
                ECOUselessBatchProviderBridge.BigIntegerReflectionApi.load(), target);
            var ordinary = adapter.eco$prepareFastPath(context);
            assertNotNull(ordinary);
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)
                .divide(BigInteger.valueOf(9)).longValueExact(), ordinary.capacity());
            var prepared = adapter.eco$prepareExactFastPath(context, copies);
            assertNotNull(prepared);
            try (var events = mockStatic(appeng.crafting.CraftingEvent.class)) {
                assertTrue(prepared.dispatch().getAsBoolean());
            }
            verify(pattern, times(1)).assemble(any(), eq(level));
            var pending = manager.getClass().getDeclaredField("pendingOutputAmount");
            pending.setAccessible(true);
            assertEquals(copies.multiply(BigInteger.TWO), pending.get(manager));
            var queueField = manager.getClass().getDeclaredField("queuedCraftingOutputs");
            queueField.setAccessible(true);
            var queue = (List<?>) queueField.get(manager);
            assertEquals(1, queue.size());
            var ledgerField = queue.getFirst().getClass().getDeclaredField("ledger");
            ledgerField.setAccessible(true);
            var ledger = ledgerField.get(queue.getFirst());
            var amount = ledger.getClass().getDeclaredMethod("amount", appeng.api.stacks.AEKey.class);
            amount.setAccessible(true);
            assertEquals(copies.multiply(BigInteger.TWO), amount.invoke(ledger, output));
        }
    }
    @Test void recipePatternsAndUnknownTargetsRetainNativeAdmission() {
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        var target = new OmniversalBigIntegerTarget(core, "test");
        var context = new ECOBatchDispatchContext(mock(IPatternDetails.class),
            List.of(), List.of(), List.of(), null, null);
        assertNull(ECOUselessExactCraftingDispatch.prepare(target, context, BigInteger.TEN));
        assertNull(ECOUselessExactCraftingDispatch.prepare(new Object(), context, BigInteger.TEN));
        verifyNoInteractions(core);
    }

    @Test void uncertainCommitNeverRefundsOrReplaysInputs() {
        var core = mock(MultiblockAlloyFurnaceCoreBlockEntity.class);
        var target = new OmniversalBigIntegerTarget(core, "test");
        var context = new ECOBatchDispatchContext(mock(IMolecularAssemblerSupportedPattern.class),
            List.of(), List.of(), List.of(), null, null);
        when(core.pushBigIntegerBatch(any(), any(), any(), isNull()))
            .thenThrow(new IllegalStateException("after ownership transfer"));
        var prepared = ECOUselessExactCraftingDispatch.prepare(target, context, BigInteger.TEN.pow(28));
        assertNotNull(prepared);
        assertThrows(ECOIndeterminateBatchException.class, () -> prepared.dispatch().getAsBoolean());
    }
}
