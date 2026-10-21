package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ForgeStorageFilterTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }

    @Test void realItemAndFluidKeysKeepExactTagsAcrossAllFilterSlots() {
        var item = new ItemStack(Items.PAPER);
        item.getOrCreateTag().putInt("variant", 7);
        var itemKey = AEItemKey.of(item);
        var fluidKey = AEFluidKey.of(Fluids.WATER);
        var original = new CompoundTag();
        var saved = StorageInterfaceFilter.with(original, 0, itemKey);
        saved = StorageInterfaceFilter.with(saved, 26, fluidKey);
        assertTrue(original.isEmpty());
        var reopened = new StorageInterfaceFilter(saved);
        assertTrue(reopened.test(itemKey));
        assertTrue(reopened.test(fluidKey));
        assertFalse(reopened.test(AEItemKey.of(Items.PAPER)));
        assertEquals(itemKey, StorageInterfaceFilter.get(saved, 0));
        assertEquals(fluidKey, StorageInterfaceFilter.get(saved, 26));
    }

    @Test void missingChannelOrItemNeverTurnsAnExistingFilterIntoAllowAll() {
        var tag = new CompoundTag();
        var unknown = new CompoundTag();
        unknown.putString("#c", "missing_mod:channel");
        tag.put("26", unknown);
        assertFalse(new StorageInterfaceFilter(tag).test(AEItemKey.of(Items.STONE)));
        unknown = StorageInterfaceFilter.encode(AEItemKey.of(Items.STONE));
        unknown.putString("id", "missing_mod:item");
        tag.put("26", unknown);
        assertNull(StorageInterfaceFilter.get(tag, 26));
        assertFalse(new StorageInterfaceFilter(tag).test(AEItemKey.of(Items.STONE)));
        assertEquals(unknown, tag.getCompound("26"));
    }

    @Test void clearingLastSlotAndSlotBoundsRetainTheExistingContract() {
        var key = AEItemKey.of(Items.STONE);
        var saved = StorageInterfaceFilter.with(new CompoundTag(), 26, key);
        var cleared = StorageInterfaceFilter.with(saved, 26, null);
        assertTrue(new StorageInterfaceFilter(cleared).test(AEItemKey.of(Items.DIRT)));
        assertTrue(saved.contains("26"));
        assertThrows(IllegalArgumentException.class, () -> StorageInterfaceFilter.with(saved, -1, key));
        assertThrows(IllegalArgumentException.class, () -> StorageInterfaceFilter.with(saved, 27, key));
    }
}
