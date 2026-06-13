package cn.dancingsnow.neoecoae.api;

import appeng.menu.me.crafting.CraftingStatusMenu;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface IOverlayTextureHolder {
    @Nullable
    Identifier neoecoae$getOverlay();

    void neoecoae$setOverlay(@Nullable Identifier overlay);

    static IOverlayTextureHolder of(CraftingStatusMenu.CraftingCpuListEntry entry) {
        return (IOverlayTextureHolder) (Object) entry;
    }
}
