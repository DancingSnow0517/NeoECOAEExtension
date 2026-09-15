package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AppEngInternalInventorySyncTest {
    @Test
    void inventoryMutationMarksRegisteredSyncListenerDirty() throws Exception {
        var inventory = new SnapshotReceiver();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        inventory.setOnContentsChanged(calls::incrementAndGet);
        var callback = AppEngInternalInventoryMixin.class.getDeclaredMethod(
                "neoecoae$markInventorySyncDirty", int.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        callback.setAccessible(true);
        callback.invoke(inventory, 0, null);
        assertEquals(1, calls.get());
    }

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    @Test
    void fullSnapshotClearsSlotsOmittedBySparseNbtWithoutDirtyingReceiver() throws Exception {
        var inventory = new SnapshotReceiver();
        var slots = NonNullList.withSize(3, ItemStack.EMPTY);
        slots.set(0, mock(ItemStack.class));
        slots.set(2, mock(ItemStack.class));
        var field = AppEngInternalInventoryMixin.class.getDeclaredField("stacks");
        field.setAccessible(true);
        field.set(inventory, slots);
        inventory.setOnContentsChanged(() -> fail("Receiving a snapshot must not mark it dirty"));

        inventory.deserializeNBT(null, new CompoundTag());

        assertEquals(3, slots.size());
        assertTrue(slots.stream().allMatch(ItemStack::isEmpty));
        assertTrue(inventory.read);
    }

    private static final class SnapshotReceiver extends AppEngInternalInventoryMixin {
        boolean read;
        @Override public int size() { return 3; }
        @Override public void writeToNBT(CompoundTag data, String name, HolderLookup.Provider registries) {}
        @Override public void readFromNBT(CompoundTag data, String name, HolderLookup.Provider registries) {
            read = true;
        }
    }
}
