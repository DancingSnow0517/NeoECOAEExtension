package cn.dancingsnow.neoecoae.mixins;

import appeng.client.Point;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(CPUSelectionList.class)
public class CPUSelectionListMixin {

    @Shadow
    @Final
    private Blitter buttonBg;

    @Inject(
        method = "drawBackgroundLayer",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"
        )
    )
    private void onDrawBackgroundLayer(
        GuiGraphics guiGraphics,
        Rect2i bounds,
        Point mouse,
        CallbackInfo ci,
        @Local CraftingStatusMenu.CraftingCpuListEntry cpu
    ) {
        ResourceLocation texture = IOverlayTextureHolder.of(cpu).neoecoae$getOverlay();
        if (texture != null) {
            guiGraphics.blit(texture, buttonBg.getSrcWidth() - 12, 0, 0, 0, 7, 7, 7, 7);
        }
    }
}
