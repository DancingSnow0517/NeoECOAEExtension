package cn.dancingsnow.neoecoae.integration.xei;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.integration.emi.EmiBookmarkAccess;
import cn.dancingsnow.neoecoae.integration.jei.JeiBookmarkAccess;
import java.util.List;
import net.minecraftforge.fml.ModList;

/** Optional viewer entry point; EMI owns the visible bookmarks when both viewers are installed. */
public final class RecipeViewerBookmarkAccess {
    private RecipeViewerBookmarkAccess() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("emi") || JeiBookmarkAccess.isAvailable();
    }

    public static List<AEKey> bookmarkedKeys() {
        if (ModList.get().isLoaded("emi")) return EmiBookmarkAccess.bookmarkedKeys();
        return JeiBookmarkAccess.bookmarkedKeys();
    }

    public static void addMissingToBookmarks(List<? extends AEKey> keys) {
        if (ModList.get().isLoaded("emi")) {
            EmiBookmarkAccess.addMissingToBookmarks(keys);
        } else {
            JeiBookmarkAccess.addMissingToBookmarks(keys);
        }
    }
}
