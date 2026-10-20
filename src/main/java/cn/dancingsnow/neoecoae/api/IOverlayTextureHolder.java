package cn.dancingsnow.neoecoae.api;

import appeng.menu.me.crafting.CraftingStatusMenu;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface IOverlayTextureHolder {
    @Nullable
    ResourceLocation neoecoae$getOverlay();

    void neoecoae$setOverlay(@Nullable ResourceLocation overlay);

    static IOverlayTextureHolder of(CraftingStatusMenu.CraftingCpuListEntry entry) {
        return (IOverlayTextureHolder) (Object) entry;
    }
}
