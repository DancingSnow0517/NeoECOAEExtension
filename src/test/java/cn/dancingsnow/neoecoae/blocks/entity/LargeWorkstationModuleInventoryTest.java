package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationModuleInventoryTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void singleCardInsertionSimulationAndExtractionNotifyOnlyActualChanges() {
        AtomicInteger changes = new AtomicInteger();
        var inventory = new LargeWorkstationModuleInventory(changes::incrementAndGet);
        try (var modules = mockStatic(LargeWorkstationModule.class)) {
            modules.when(() -> LargeWorkstationModule.isUpgradeCard(any(ItemStack.class)))
                .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.PAPER));
            assertEquals(1, inventory.getSlots());
            assertEquals(1, inventory.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount());
            assertEquals(7, inventory.insertItem(0, new ItemStack(Items.PAPER, 8), true).getCount());
            assertTrue(inventory.getStackInSlot(0).isEmpty());
            assertEquals(0, changes.get());
            assertEquals(7, inventory.insertItem(0, new ItemStack(Items.PAPER, 8), false).getCount());
            assertEquals(1, inventory.getStackInSlot(0).getCount());
            assertEquals(1, changes.get());
            assertEquals(1, inventory.extractItem(0, 64, true).getCount());
            assertEquals(1, changes.get());
            assertEquals(1, inventory.extractItem(0, 64, false).getCount());
            assertTrue(inventory.getStackInSlot(0).isEmpty());
            assertEquals(2, changes.get());
            assertThrows(IllegalArgumentException.class, () -> inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND)));
            assertThrows(IllegalArgumentException.class, () -> inventory.setStackInSlot(0, new ItemStack(Items.PAPER, 2)));
        }
    }

    @Test void savedCardRoundTripsAndOlderSavesStartWithOneEmptySlot() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        AtomicInteger changes = new AtomicInteger();
        var inventory = new LargeWorkstationModuleInventory(changes::incrementAndGet);
        try (var modules = mockStatic(LargeWorkstationModule.class)) {
            modules.when(() -> LargeWorkstationModule.isUpgradeCard(any(ItemStack.class))).thenReturn(true);
            inventory.setStackInSlot(0, new ItemStack(Items.PAPER));
        }
        CompoundTag saved = inventory.serializeNBT();
        var restored = new LargeWorkstationModuleInventory(changes::incrementAndGet);
        restored.deserializeNBT(saved);
        assertTrue(restored.getStackInSlot(0).is(Items.PAPER));
        assertEquals(1, restored.getStackInSlot(0).getCount());
        assertEquals(saved, restored.serializeNBT());
        assertEquals(1, changes.get(), "Loading must not republish a partly restored machine");
        restored.deserializeNBT(new CompoundTag());
        assertEquals(1, restored.getSlots());
        assertTrue(restored.getStackInSlot(0).isEmpty());
    }
}
