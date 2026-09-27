package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/** Safe entry point for screens that also run without JEI installed. */
public final class JeiBookmarkAccess {
    private JeiBookmarkAccess() {}

    public static boolean isAvailable() {
        // Keep JEI API references in a separate class, loaded only after this check.
        return ModList.get().isLoaded("jei") && JeiBookmarkAccessImpl.isAvailable();
    }

    public static List<ItemStack> itemBookmarks() {
        return isAvailable() ? JeiBookmarkAccessImpl.itemBookmarks() : List.of();
    }

    public static void addMissingToBookmarks(List<? extends AEKey> keys) {
        if (isAvailable()) {
            JeiBookmarkAccessImpl.addMissingToBookmarks(keys);
        }
    }
}
