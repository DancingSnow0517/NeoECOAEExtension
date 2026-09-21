package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ECOExactInventoryLifecycleTest {
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
