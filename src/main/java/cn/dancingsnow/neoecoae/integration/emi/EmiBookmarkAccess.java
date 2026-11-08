package cn.dancingsnow.neoecoae.integration.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;

import java.util.List;

/** Adds missing crafting materials to EMI's favorites list when JEI bookmarks are unavailable. */
public final class EmiBookmarkAccess {
    private EmiBookmarkAccess() {}

    public static void addMissingToBookmarks(List<? extends AEKey> keys) {
        int added = 0;
        for (AEKey key : keys) {
            EmiStack stack = stack(key);
            if (stack != null && !stack.isEmpty()) {
                EmiFavorites.addFavorite(stack);
                added++;
            }
        }
        if (added > 0) {
            EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
        }
    }

    private static EmiStack stack(AEKey key) {
        if (key instanceof AEItemKey item) {
            return EmiStack.of(item.toStack());
        }
        if (key instanceof AEFluidKey fluid) {
            return NeoForgeEmiStack.of(fluid.toStack(1));
        }
        return null;
    }
}
