package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ECOCpuInventoryReturnTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void largeReturnsAreBoundedAcrossRepeatedCallsInTheSameTickAndEventuallyComplete() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var level = mock(Level.class);
        var tick = new AtomicLong();
        when(level.getGameTime()).thenAnswer(call -> tick.get());
        when(cpu.getLevel()).thenReturn(level);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        var logic = new ECOCraftingCPULogic(cpu);
        List<AEItemKey> keys = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .limit(700).map(AEItemKey::of).toList();
        assertEquals(700, keys.size());
        for (var key : keys) logic.getInventory().insert(key, 64, Actionable.MODULATE);
        var accepted = new AtomicLong();
        var calls = new AtomicInteger();
        when(grid.getStorageService().getInventory().insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
            calls.incrementAndGet();
            accepted.addAndGet(call.getArgument(1));
            return call.getArgument(1);
        });
        logic.storeItems();
        assertTrue(logic.hasOwnedItems());
        for (int i = 0; i < 20; i++) logic.storeItems();
        assertTrue(calls.get() <= ECOCpuInventoryReturn.KEYS_PER_TICK);
        for (int i = 0; i < 100 && logic.hasOwnedItems(); i++) { tick.incrementAndGet(); logic.storeItems(); }
        assertFalse(logic.hasOwnedItems());
        assertEquals(700 * 64, accepted.get());
        assertEquals(700, calls.get());
    }

    @Test void rejectedResourcesRemainOwnedWhileOtherKeysAndLaterReturnsProgress() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        var logic = new ECOCraftingCPULogic(cpu);
        AEKey blocked = AEItemKey.of(Items.STONE);
        AEKey accepted = AEItemKey.of(Items.DIRT);
        logic.getInventory().insert(blocked, 80, Actionable.MODULATE);
        logic.getInventory().insert(accepted, 30, Actionable.MODULATE);
        MEStorage storage = grid.getStorageService().getInventory();
        when(storage.insert(eq(accepted), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));
        logic.storeItems();
        assertEquals(80, logic.getInventory().list.get(blocked));
        assertEquals(0, logic.getInventory().list.get(accepted));
        when(storage.insert(eq(blocked), eq(80L), any(), any())).thenReturn(50L);
        logic.storeItems();
        assertEquals(30, logic.getInventory().list.get(blocked));
        when(storage.insert(eq(blocked), eq(30L), any(), any())).thenReturn(30L);
        logic.storeItems();
        assertFalse(logic.hasOwnedItems());
    }

    @Test void permanentRemovalAttemptsOneCompletePassAfterTheNormalBudgetWasUsed() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        var logic = new ECOCraftingCPULogic(cpu);
        var keys = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(700).map(AEItemKey::of).toList();
        for (var key : keys) logic.getInventory().insert(key, 1_000_000_000L, Actionable.MODULATE);
        var accepted = new AtomicLong();
        when(grid.getStorageService().getInventory().insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
            accepted.addAndGet(call.getArgument(1));
            return call.getArgument(1);
        });
        logic.storeItems();
        assertTrue(logic.hasOwnedItems());
        logic.storeItemsForRemoval();
        assertFalse(logic.hasOwnedItems());
        assertEquals(700_000_000_000L, accepted.get());
    }

    @Test void failedInitialExtractionKeepsThePartThatRollbackStorageRejects() {
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        var logic = new ECOCraftingCPULogic(cpu);
        var key = AEItemKey.of(Items.IRON_INGOT);
        var plan = mock(ICraftingPlan.class);
        var used = new KeyCounter();
        used.set(key, 10);
        when(plan.usedItems()).thenReturn(used);
        var storage = grid.getStorageService().getInventory();
        when(storage.extract(eq(key), eq(10L), any(), any())).thenReturn(7L);
        when(storage.insert(eq(key), eq(7L), any(), any())).thenReturn(3L);
        var missing = logic.extractInitialItems(plan, grid, IActionSource.empty());
        assertEquals(3, missing.amount());
        assertEquals(4, logic.getInventory().list.get(key));
        assertSame(appeng.crafting.execution.CraftingSubmitResult.CPU_BUSY,
                logic.trySubmitJob(grid, plan, IActionSource.empty(), null));
    }
}
