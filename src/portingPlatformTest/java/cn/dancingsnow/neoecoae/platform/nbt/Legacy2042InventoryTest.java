package cn.dancingsnow.neoecoae.platform.nbt;

import static org.junit.jupiter.api.Assertions.*;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Legacy2042InventoryTest {
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }
    private static CompoundTag patterns() {
        ItemStack pattern = new ItemStack(Items.PAPER); pattern.getOrCreateTag().putString("encodedPattern", "retained exactly");
        CompoundTag entry = pattern.save(new CompoundTag()); entry.putInt("Slot", 449);
        ListTag list = new ListTag(); list.add(entry); CompoundTag tag = new CompoundTag(); tag.put("patternInventory", list);
        tag.putInt("patternInventoryPages", 8); return tag;
    }
    @Test void restoresSparseHighPageAndExactItemNbt() {
        var source = patterns(); var entries = Legacy2042Inventory.read(source, "patternInventory", 504, 64);
        assertEquals(8, Legacy2042Inventory.pages(source, entries, 63, 8));
        assertEquals("retained exactly", entries.get(449).getTag().getString("encodedPattern")); assertTrue(entries.get(0).isEmpty());
    }
    @Test void occupiedPageWinsOverStalePageCount() {
        var source = patterns(); source.putInt("patternInventoryPages", 1);
        assertEquals(8, Legacy2042Inventory.pages(source, Legacy2042Inventory.read(source, "patternInventory", 504, 64), 63, 8));
    }
    @Test void unknownItemIsNotSilentlyErased() {
        var source = patterns(); var entry = source.getList("patternInventory", 10).getCompound(0); entry.putString("id", "absent:pattern");
        var original = source.copy(); assertThrows(IllegalArgumentException.class, () -> Legacy2042Inventory.read(source, "patternInventory", 504, 64));
        assertEquals(original, source);
    }
    @Test void duplicateAndOutOfRangeSlotsAreRejectedAtomically() {
        var source = patterns(); var list = source.getList("patternInventory", 10); list.add(list.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> Legacy2042Inventory.read(source, "patternInventory", 504, 64));
        list.remove(1); list.getCompound(0).putInt("Slot", 504);
        assertThrows(IllegalArgumentException.class, () -> Legacy2042Inventory.read(source, "patternInventory", 504, 64));
    }
    @Test void invalidPageMetadataIsRejected() {
        var source = patterns(); source.putInt("patternInventoryPages", 9);
        var values = Legacy2042Inventory.read(source, "patternInventory", 504, 64);
        assertThrows(IllegalArgumentException.class, () -> Legacy2042Inventory.pages(source, values, 63, 8));
    }
    @Test void restoresRealForgeControllerComponentInventory() {
        var original = new net.minecraftforge.items.ItemStackHandler(1); original.setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        List<ItemStack> restored = Legacy2042Inventory.read(original.serializeNBT(), "Items", 1, 64);
        assertEquals(64, restored.get(0).getCount()); assertTrue(restored.get(0).is(Items.DIAMOND));
    }
}
