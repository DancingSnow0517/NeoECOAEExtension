package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.client.Point;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CPUSelectionList.class)
public abstract class CPUSelectionListMixin120 {
    @Shadow
    @Final
    private Blitter buttonBg;

    @Shadow
    @Final
    private CraftingStatusMenu menu;

    @Shadow
    @Final
    private Scrollbar scrollbar;

    @Shadow
    private Rect2i bounds;

    @Inject(method = "drawBackgroundLayer", at = @At("RETURN"))
    private void neoecoae$drawCpuTierOverlay(GuiGraphics guiGraphics, Rect2i screenBounds, Point mouse, CallbackInfo ci) {
        int x = screenBounds.getX() + this.bounds.getX() + 9;
        int y = screenBounds.getY() + this.bounds.getY() + 19;
        var cpus = menu.cpuList.cpus();
        var visibleCpus = cpus.subList(
            Mth.clamp(scrollbar.getCurrentScroll(), 0, cpus.size()),
            Mth.clamp(scrollbar.getCurrentScroll() + 6, 0, cpus.size())
        );
        for (var cpu : visibleCpus) {
            var overlay = IOverlayTextureHolder.of(cpu).neoecoae$getOverlay();
            if (overlay != null) {
                Blitter.texture(overlay)
                    .dest(x + buttonBg.getSrcWidth() - 20, y + 2, 18, 18)
                    .blending(true)
                    .blit(guiGraphics);
            }
            y += buttonBg.getSrcHeight() + 1;
        }
    }
}
