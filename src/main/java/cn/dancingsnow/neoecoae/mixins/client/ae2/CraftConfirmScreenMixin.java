package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.client.ECOCraftConfirmScreenIntegration;
import cn.dancingsnow.neoecoae.util.NEByteFormatter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;

/** Formats AE2's native CPU status without changing the native/Data confirmation-screen flow. */
@Mixin(value = CraftConfirmScreen.class, priority = 1100)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {
    @Unique
    private @Nullable IconButton neoecoae$ecoPlannerButton;

    protected CraftConfirmScreenMixin(CraftConfirmMenu menu,
                                      Inventory playerInventory,
                                      Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void neoecoae$addEcoPlannerButton(CraftConfirmMenu menu,
                                               Inventory playerInventory,
                                               Component title,
                                               ScreenStyle style,
                                               org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.neoecoae$ecoPlannerButton = addToLeftToolbar(
                ECOCraftConfirmScreenIntegration.createPlannerButton(menu));
    }

    @Inject(method = "updateBeforeRender", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void neoecoae$updateEcoPlannerButton(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (this.neoecoae$ecoPlannerButton != null) {
            ECOCraftConfirmScreenIntegration.updatePlannerButton(this.neoecoae$ecoPlannerButton, this.menu);
        }
    }

    @WrapOperation(
        method = "updateBeforeRender",
        at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE",
            target = "Lappeng/core/localization/GuiText;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
            ordinal = 1
        )
    )
    private MutableComponent neoecoae$formatCpuStatus(
            GuiText text, Object[] args, Operation<MutableComponent> original) {
        if (text != GuiText.ConfirmCraftCpuStatus || args.length < 2
                || !(args[0] instanceof Number storage)
                || !(args[1] instanceof Number coProcessors)) {
            return original.call(text, args);
        }

        return original.call(text, new Object[] {
            Component.literal(NEByteFormatter.formatCpuStorage(storage.longValue())),
            Component.literal(NEByteFormatter.formatCpuCoProcessors(coProcessors.longValue()))
        });
    }
}
