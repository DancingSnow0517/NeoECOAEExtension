package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    /**
     * A snapshot that omits a slot is how the bus learns that a pattern disk left: every slot the
     * snapshot dropped has to reach the host, or the recipes of the removed disk stay advertised.
     */
    @Test
    void snapshotReportsEverySlotWhoseContentsChanged() throws Exception {
        var host = mock(InternalInventoryHost.class);
        var inventory = new AppEngInternalInventory(host, 3);

        notifyChangedSlots(inventory,
                List.of(mock(ItemStack.class), ItemStack.EMPTY, mock(ItemStack.class)),
                List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

        verify(host).onChangeInventory(inventory, 0);
        verify(host).onChangeInventory(inventory, 2);
        verify(host, never()).onChangeInventory(inventory, 1);
    }

    @Test
    void snapshotReportsAnAddedStackWithoutTouchingItsNeighbours() throws Exception {
        var host = mock(InternalInventoryHost.class);
        var inventory = new AppEngInternalInventory(host, 3);
        ItemStack untouched = mock(ItemStack.class);
        ItemStack added = mock(ItemStack.class);

        notifyChangedSlots(inventory,
                List.of(untouched, ItemStack.EMPTY, ItemStack.EMPTY),
                List.of(untouched, ItemStack.EMPTY, added));

        verify(host).onChangeInventory(inventory, 2);
        verify(host, never()).onChangeInventory(inventory, 0);
        verify(host, never()).onChangeInventory(inventory, 1);
    }

    /** Re-applying an identical snapshot must stay silent, or every sync would echo a fresh one. */
    @Test
    void identicalSnapshotReportsNothing() throws Exception {
        var host = mock(InternalInventoryHost.class);
        var inventory = new AppEngInternalInventory(host, 3);
        ItemStack first = mock(ItemStack.class);
        List<ItemStack> contents = List.of(first, ItemStack.EMPTY, ItemStack.EMPTY);

        notifyChangedSlots(inventory, contents, new ArrayList<>(contents));

        verify(host, never()).onChangeInventory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void snapshotWithoutAHostIsIgnored() throws Exception {
        // AE2 allows a null host; a snapshot then has nobody to inform and must not throw.
        var inventory = new AppEngInternalInventory(null, 2);

        assertDoesNotThrow(() -> notifyChangedSlots(inventory,
                List.of(mock(ItemStack.class), ItemStack.EMPTY),
                List.of(ItemStack.EMPTY, ItemStack.EMPTY)));
    }

    /**
     * The mixin shadows these members by name and adds the interfaces. If AE2 ever renames, removes or
     * ships them itself, this fails here instead of silently turning the mixin into an override.
     */
    @Test
    void ae2InventoryStillDeclaresEveryMemberThisMixinTouches() throws Exception {
        var type = AppEngInternalInventory.class;

        assertEquals(NonNullList.class, type.getDeclaredField("stacks").getType());
        assertNotNull(type.getDeclaredMethod("size"));
        assertNotNull(type.getDeclaredMethod("getHost"));
        assertNotNull(type.getDeclaredMethod("onContentsChanged", int.class));
        assertNotNull(type.getDeclaredMethod("readFromNBT",
                CompoundTag.class, String.class, HolderLookup.Provider.class));
        assertNotNull(type.getDeclaredMethod("writeToNBT",
                CompoundTag.class, String.class, HolderLookup.Provider.class));
        assertThrows(NoSuchMethodException.class,
                () -> type.getDeclaredMethod("deserializeNBT", HolderLookup.Provider.class, CompoundTag.class),
                "AE2 now supplies deserializeNBT itself; the mixin would be overriding it");
        assertFalse(INBTSerializable.class.isAssignableFrom(type),
                "AE2 now implements INBTSerializable itself; the mixin would be overriding it");
    }

    private static void notifyChangedSlots(AppEngInternalInventory inventory, List<ItemStack> before,
                                           List<ItemStack> after) throws Exception {
        var method = AppEngInternalInventoryMixin.class.getDeclaredMethod("notifyChangedSlots",
                AppEngInternalInventory.class, List.class, List.class);
        method.setAccessible(true);
        method.invoke(null, inventory, before, after);
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
