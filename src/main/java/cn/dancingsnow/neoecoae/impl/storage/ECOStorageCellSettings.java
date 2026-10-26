package cn.dancingsnow.neoecoae.impl.storage;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Cell-local priorities use AE2's signed integer ordering: higher values receive inserts first. */
public final class ECOStorageCellSettings {
    private static final String PRIORITY = "neoecoae_storage_priority";

    private ECOStorageCellSettings() {}

    public static boolean canConfigure(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof ECOInfiniteResourceCellItem)
            && !ECOInfiniteStorageMember.isMember(stack) && !ECOInfiniteStorageMember.isMigrating(stack);
    }

    public static int priority(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (stack.getItem() instanceof ECOBulkStorageCellItem) {
            var bulk = data.getCompound(data.contains("neoecoae_bulk", Tag.TAG_COMPOUND)
                ? "neoecoae_bulk" : "neoecoae_mega_long_bulk");
            return bulk.contains("priority", Tag.TAG_INT) ? bulk.getInt("priority") : 1;
        }
        return data.getInt(PRIORITY);
    }

    public static void setPriority(ItemStack stack, int priority) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(PRIORITY, priority);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
}
