package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.client.Point;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.util.NETextFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CPUSelectionList.class, remap = false)
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

    // Small corner badge dimensions
    private static final int OVERLAY_W = 10;
    private static final int OVERLAY_H = 10;
    private static final int OVERLAY_RIGHT_MARGIN = 2;
    private static final int OVERLAY_TOP_MARGIN = 2;

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
                // Small badge at top-right corner of the CPU entry row
                int overlayX = x + buttonBg.getSrcWidth() - OVERLAY_W - OVERLAY_RIGHT_MARGIN;
                int overlayY = y + OVERLAY_TOP_MARGIN;
                Blitter.texture(overlay)
                    .dest(overlayX, overlayY, OVERLAY_W, OVERLAY_H)
                    .blending(true)
                    .blit(guiGraphics);
            }
            y += buttonBg.getSrcHeight() + 1;
        }
    }

    /**
     * Override AE2's default storage formatting for ECO CPU entries.
     * AE2's default {@code formatStorage} divides bytes by 1024 and appends "k",
     * producing unreadable output like "6291456k" for 6 GB.
     * We intercept only ECO CPU entries (identified by their tier overlay texture)
     * and replace the storage text with a compact human-readable form.
     */
    @Inject(method = "formatStorage", at = @At("RETURN"), cancellable = true)
    private void neoecoae$formatStorage(CraftingStatusMenu.CraftingCpuListEntry cpu, CallbackInfoReturnable<String> cir) {
        var overlay = IOverlayTextureHolder.of(cpu).neoecoae$getOverlay();
        if (overlay != null) {
            // AE2 stores bytes; its default format divides by 1024 → KB → appends "k".
            // We convert bytes → KB, then pass to formatKiloUnit for compact display.
            long storageKB = cpu.storage() / 1024;
            cir.setReturnValue(NETextFormat.formatKiloUnit(storageKB));
        }
    }
}
