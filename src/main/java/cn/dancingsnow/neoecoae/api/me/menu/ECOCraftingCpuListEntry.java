package cn.dancingsnow.neoecoae.api.me.menu;

import appeng.menu.me.crafting.CraftingStatusMenu;

/** CPU ownership synchronized independently of the optional tier overlay. */
public interface ECOCraftingCpuListEntry {
    boolean neoecoae$isEcoCpu();

    void neoecoae$setEcoCpu(boolean ecoCpu);

    static ECOCraftingCpuListEntry of(CraftingStatusMenu.CraftingCpuListEntry entry) {
        return (ECOCraftingCpuListEntry) (Object) entry;
    }

    static boolean isEcoCpu(Object entry) {
        return entry instanceof ECOCraftingCpuListEntry state && state.neoecoae$isEcoCpu();
    }
}
