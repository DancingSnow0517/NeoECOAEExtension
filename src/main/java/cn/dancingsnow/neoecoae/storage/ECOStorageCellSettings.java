package cn.dancingsnow.neoecoae.storage;

import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Cell-local priorities use AE2's signed integer ordering: higher values receive inserts first. */
public final class ECOStorageCellSettings {
    private static final String PRIORITY = "neoecoae_storage_priority";

    private ECOStorageCellSettings() {}

    public static boolean canConfigure(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof ECOInfiniteResourceCellItem)
            && !ECOInfiniteStorageMember.isMember(stack) && !ECOInfiniteStorageMember.isMigrating(stack);
    }

    public static int priority(ItemStack stack) {
        var data = NEItemData.read(stack);
        if (stack.getItem() instanceof ECOBulkStorageCellItem) {
            var bulk = data.getCompound(data.contains("neoecoae_bulk", Tag.TAG_COMPOUND)
                ? "neoecoae_bulk" : "neoecoae_mega_long_bulk");
            return bulk.contains("priority", Tag.TAG_INT) ? bulk.getInt("priority") : 1;
        }
        return data.getInt(PRIORITY);
    }

    public static void setPriority(ItemStack stack, int priority) {
        NEItemData.update(stack, data -> data.putInt(PRIORITY, priority));
    }
}
