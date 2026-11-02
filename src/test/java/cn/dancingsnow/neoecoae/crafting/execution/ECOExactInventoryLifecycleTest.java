package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import java.math.BigInteger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ECOExactInventoryLifecycleTest {
    @Test void internalExactFxReceiptAndCpuReturnKeepTheCompleteAmount() throws Exception {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(grid.getStorageService().getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var input = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT);
        var output = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND);
        var amount = BigInteger.TEN.pow(28).add(BigInteger.valueOf(17));
        var id = java.util.UUID.randomUUID();
        var link = mock(appeng.crafting.CraftingLink.class);
        when(link.getCraftingID()).thenReturn(id);
        var plan = mock(appeng.api.networking.crafting.ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new appeng.api.stacks.GenericStack(output, 1));
        when(plan.emittedItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        when(plan.patternTimes()).thenReturn(java.util.Map.of());
        ExecutingCraftingJob job;
        try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
        }
        job.exactOrder = true;
        var waiting = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ignored -> {});
        waiting.setEnabled(true);
        waiting.restore(java.util.Map.of(input, amount));
        setField(job, "waitingFor", waiting);
        logic.setJobFromPersistence(job);
        java.util.function.Consumer<AEKey> listener = key -> assertEquals(BigInteger.ZERO,
                logic.insertExactForJob(id, input, amount, Actionable.MODULATE));
        logic.addListener(listener);
        assertEquals(BigInteger.ZERO, logic.insertExactForJob(java.util.UUID.randomUUID(), input, amount, Actionable.MODULATE));
        assertEquals(amount, logic.insertExactForJob(id, input, amount, Actionable.SIMULATE));
        assertEquals(amount, waiting.amount(input));
        assertEquals(amount, logic.insertExactForJob(id, input, amount, Actionable.MODULATE));
        logic.removeListener(listener);
        assertEquals(BigInteger.ZERO, waiting.amount(input));
        assertEquals(amount, logic.exactInventory().amount(input));
        when(((ECOBigIntegerStorage) storage).insertBigInteger(eq(input), eq(amount), any(), any())).thenReturn(amount);
        logic.finishJob(false);
        assertFalse(logic.hasOwnedItems());
        verify((ECOBigIntegerStorage) storage).insertBigInteger(eq(input), eq(amount), eq(Actionable.MODULATE), any());
        verify(storage, never()).insert(any(), anyLong(), any(), any());
    }

    @Test void ecoStorageReceivesTheExactOverflowOnceAndSmallRemainderUsesLong() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(grid.getStorageService().getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var total = BigInteger.TEN.pow(28).add(BigInteger.valueOf(17));
        logic.exactInventory().setEnabled(true);
        logic.exactInventory().restore(java.util.Map.of(key, total));
        var exact = (ECOBigIntegerStorage) storage;
        when(exact.insertBigInteger(eq(key), eq(total), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            logic.storeItems(); // A storage callback must not replay the still-in-flight transfer.
            return total.subtract(BigInteger.valueOf(7));
        });
        logic.storeItems();
        assertEquals(BigInteger.valueOf(7), logic.exactInventory().amount(key));
        verify(exact, times(1)).insertBigInteger(eq(key), eq(total), eq(Actionable.MODULATE), any());
        verify(storage, never()).insert(any(), anyLong(), any(), any());
        when(storage.insert(eq(key), eq(7L), eq(Actionable.MODULATE), any())).thenReturn(7L);
        logic.storeItems();
        assertEquals(BigInteger.ZERO, logic.exactInventory().amount(key));
        verify(storage).insert(eq(key), eq(7L), eq(Actionable.MODULATE), any());
        assertFalse(logic.hasOwnedItems());
    }

    @Test void boundedReturnsPreferLongEvenWhenEcoStorageSupportsBigInteger() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(grid.getStorageService().getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        logic.exactInventory().setEnabled(true);
        logic.exactInventory().restore(java.util.Map.of(key, BigInteger.valueOf(64)));
        when(storage.insert(eq(key), eq(64L), eq(Actionable.MODULATE), any())).thenReturn(64L);
        logic.storeItems();
        assertFalse(logic.hasOwnedItems());
        verify(storage).insert(eq(key), eq(64L), eq(Actionable.MODULATE), any());
        verify((ECOBigIntegerStorage) storage, never()).insertBigInteger(any(), any(), any(), any());
    }

    @Test void failedExactReturnRetainsItsInventoryAndCanRetry() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(grid.getStorageService().getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var total = BigInteger.TEN.pow(28);
        logic.exactInventory().setEnabled(true);
        logic.exactInventory().restore(java.util.Map.of(key, total));
        when(((ECOBigIntegerStorage) storage).insertBigInteger(eq(key), eq(total), any(), any()))
                .thenThrow(new IllegalStateException("rejected")).thenReturn(total);
        assertThrows(IllegalStateException.class, logic::storeItems);
        assertEquals(total, logic.exactInventory().amount(key));
        logic.storeItems();
        assertFalse(logic.hasOwnedItems());
    }

    @Test void onlyTheExplicitExactOrderFlagEnablesBigIntegerInventory() {
        var logic = new ECOCraftingCPULogic(mock(ECOCraftingCPU.class));
        var normal = mock(ExecutingCraftingJob.class);
        normal.exactOrder = false;
        logic.setJobFromLifecycle(normal);
        assertFalse(logic.exactInventory().isEnabled());
        var exact = mock(ExecutingCraftingJob.class);
        exact.exactOrder = true;
        logic.setJobFromLifecycle(exact);
        assertTrue(logic.exactInventory().isEnabled());
        logic.setJobFromLifecycle(null);
        assertFalse(logic.exactInventory().isEnabled());
        logic.setJobFromPersistence(exact);
        assertTrue(logic.exactInventory().isEnabled());
    }

    @Test void cancelledBigInventorySurvivesCpuSaveAndReturnsOneLongWindowPerPass() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var registry = mock(HolderLookup.Provider.class);
        when(key.toTagGeneric(registry)).thenAnswer(call -> new CompoundTag());
        logic.exactInventory().setEnabled(true);
        for (int i = 0; i < 9; i++) logic.getInventory().insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        var saved = new CompoundTag();
        logic.writeToNBT(saved, registry);
        var restored = new ECOCraftingCPULogic(cpu);
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registry), any())).thenReturn(key);
            restored.readFromNBT(saved, registry);
        }
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(9));
        assertEquals(total, restored.exactInventory().amount(key));
        var storage = grid.getStorageService().getInventory();
        when(storage.insert(eq(key), anyLong(), eq(Actionable.MODULATE), any()))
            .thenAnswer(call -> call.getArgument(1));
        for (int i = 1; i <= 9; i++) {
            restored.storeItems();
            assertEquals(total.subtract(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(i))),
                restored.exactInventory().amount(key));
        }
        verify(storage, times(9)).insert(eq(key), eq(Long.MAX_VALUE), eq(Actionable.MODULATE), any());
        assertFalse(restored.hasOwnedItems());
    }
    @Test void dismantledExactInventoryTravelsOnCoreItemWithoutChangingOrdinarySlots() throws Exception {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var registry = mock(net.minecraft.core.RegistryAccess.class);
        when(key.toTagGeneric(registry)).thenAnswer(call -> new CompoundTag());
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        when(cpu.getLogic()).thenReturn(logic);
        logic.exactInventory().setEnabled(true);
        for (int i = 0; i < 9; i++) logic.getInventory().insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        doAnswer(call -> { logic.writeToNBT(call.getArgument(0), registry); return null; })
            .when(cpu).writeToNBT(any(), eq(registry));
        var ordinaryCpu = mock(ECOCraftingCPU.class);
        var ordinaryLogic = new ECOCraftingCPULogic(ordinaryCpu);
        when(ordinaryCpu.getLogic()).thenReturn(ordinaryLogic);
        ordinaryLogic.getInventory().insert(key, 64, Actionable.MODULATE);
        var be = mock(cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity.class);
        var level = mock(net.minecraft.world.level.Level.class);
        when(level.registryAccess()).thenReturn(registry);
        setField(be, "level", level);
        setField(be, "cpus", new ECOCraftingCPU[]{cpu, ordinaryCpu});
        setField(be, "deferredInit", new CompoundTag[2]);
        var packed = new java.util.BitSet();
        setField(be, "packedExactInventories", packed);
        doCallRealMethod().when(be).packExactInventories(any());
        var item = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
        be.packExactInventories(item);
        assertTrue(packed.get(0));
        assertFalse(packed.get(1));
        verify(ordinaryCpu, never()).writeToNBT(any(), any());
        var placed = mock(cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity.class);
        setField(placed, "cpus", new ECOCraftingCPU[2]);
        var restoredSlots = new CompoundTag[2];
        setField(placed, "deferredInit", restoredSlots);
        doCallRealMethod().when(placed).restoreExactInventories(any());
        placed.restoreExactInventories(item);
        assertNotNull(restoredSlots[0]);
        assertNull(restoredSlots[1]);
        var restored = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ignored -> {});
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registry), any())).thenReturn(key);
            restored.readFromNBT(restoredSlots[0].getList("inventory", 10), registry);
        }
        assertEquals(logic.exactInventory().amount(key), restored.amount(key));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
