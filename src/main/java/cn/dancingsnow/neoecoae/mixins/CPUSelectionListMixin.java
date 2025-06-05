package cn.dancingsnow.neoecoae.mixins;

import appeng.client.Point;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.util.NumberFormatter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(
        method = "formatStorage",
        at = @At("RETURN"),
        cancellable = true
    )
    private void onFormatStorage(CraftingStatusMenu.CraftingCpuListEntry cpu, CallbackInfoReturnable<String> cir) {
        long storage = cpu.storage();
        if (storage >= 1024 * 1024 * 1024) {
            Tooltips.Amount amount = Tooltips.getByteAmount(storage);
            cir.setReturnValue(amount.digit() + amount.unit());
        }
    }

    @WrapOperation(
        method = "drawBackgroundLayer",
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V", ordinal = 2)
    )
    private void wrapAdd(
        InfoBar instance,
        String text,
        int color,
        float scale,
        int xPos,
        int yPos,
        Operation<Void> original,
        @Local CraftingStatusMenu.CraftingCpuListEntry cpu
    ) {
        if (cpu.coProcessors() >= 1000) {
            Tooltips.Amount amount = NumberFormatter.getAmount(cpu.coProcessors());
            int index = amount.digit().indexOf('.');
            String digit = index > 0 ? amount.digit().substring(0, index) : amount.digit();
            original.call(
                instance,
                digit + amount.unit(),
                color,
                scale,
                xPos,
                yPos
            );
        } else {
            original.call(instance, text, color, scale, xPos, yPos);
        }
    }
}
