package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.util.NETextFormat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CPUSelectionList.class, remap = false)
public abstract class CPUSelectionListMixin {
    @Shadow
    @Final
    private Blitter buttonBg;

    // Small corner badge dimensions
    private static final int OVERLAY_W = 10;
    private static final int OVERLAY_H = 10;
    private static final int OVERLAY_RIGHT_MARGIN = 2;
    private static final int OVERLAY_TOP_MARGIN = 2;
    /** AE2 15.4.10 repeats its last byte divisor and overflows at this boundary. */
    private static final long AE2_BYTE_TOOLTIP_LIMIT = 1000L * 1024L * 1024L * 1024L;

    @WrapOperation(
            method = "drawBackgroundLayer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/client/gui/widgets/InfoBar;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void neoecoae$drawCpuTierOverlay(
            InfoBar infoBar,
            GuiGraphics guiGraphics,
            int infoBarX,
            int infoBarY,
            Operation<Void> original,
            @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        original.call(infoBar, guiGraphics, infoBarX, infoBarY);

        var overlay = IOverlayTextureHolder.of(cpu).neoecoae$getOverlay();
        if (overlay != null) {
            // InfoBar starts two pixels into its row and twelve pixels above its bottom edge.
            int rowX = infoBarX - 2;
            int rowY = infoBarY - buttonBg.getSrcHeight() + 12;
            Blitter.texture(overlay)
                    .dest(
                            rowX + buttonBg.getSrcWidth() - OVERLAY_W - OVERLAY_RIGHT_MARGIN,
                            rowY + OVERLAY_TOP_MARGIN,
                            OVERLAY_W,
                            OVERLAY_H)
                    .blending(true)
                    .blit(guiGraphics);
        }
    }

    @Redirect(
            method = "drawBackgroundLayer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"),
            require = 0)
    private String neoecoae$formatCraftAmount(AEKey key, long amount, AmountFormat format) {
        return NETextFormat.formatItemAmount(amount);
    }

    /**
     * Override AE2's default storage formatting for ECO CPU entries.
     * AE2's default {@code formatStorage} divides bytes by 1024 and appends "k",
     * producing unreadable output like "6291456k" for 6 GB.
     * We intercept only ECO CPU entries (identified by their tier overlay texture)
     * and replace the storage text with a compact human-readable form.
     */
    @Inject(method = "formatStorage", at = @At("RETURN"), cancellable = true, require = 0)
    private void neoecoae$formatStorage(
            CraftingStatusMenu.CraftingCpuListEntry cpu, CallbackInfoReturnable<String> cir) {
        var overlay = IOverlayTextureHolder.of(cpu).neoecoae$getOverlay();
        if (overlay != null) {
            // AE2 stores bytes; its default format divides by 1024 → KB → appends "k".
            // We convert bytes → KB, then pass to formatKiloUnit for compact display.
            long storageKB = cpu.storage() / 1024;
            cir.setReturnValue(NETextFormat.formatKiloUnit(storageKB));
        }
    }

    /** Keeps AE2's CPU tooltip from indexing past its four-entry BYTE_NUMS array. */
    @Redirect(
            method = "getTooltip",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/core/localization/Tooltips;ofBytes(J)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0)
    private MutableComponent neoecoae$formatCpuTooltipStorage(long bytes) {
        if (bytes < AE2_BYTE_TOOLTIP_LIMIT) {
            return Tooltips.ofBytes(bytes);
        }
        return Component.literal(NETextFormat.formatBytes(bytes)).withStyle(Tooltips.NUMBER_TEXT);
    }
}
