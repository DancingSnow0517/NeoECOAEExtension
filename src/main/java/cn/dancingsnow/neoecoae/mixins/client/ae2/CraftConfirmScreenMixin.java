package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.client.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.util.NEByteFormatter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes only a confirmed ECO planning result to the ECO report; the native screen remains Data-compatible otherwise. */
@Mixin(value = CraftConfirmScreen.class, priority = 1100)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {

    @Unique
    private boolean neoecoae$fastPlannerReportRouted;

    protected CraftConfirmScreenMixin(CraftConfirmMenu menu,
                                      Inventory playerInventory,
                                      Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
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

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$routeFastPlannerReport(CallbackInfo ci) {
        if (this.neoecoae$fastPlannerReportRouted
                || Minecraft.getInstance().screen != (Object) this
                || !((Object) this.menu instanceof ECOCraftConfirmMenuMode mode)
                || !mode.neoecoae$shouldShowFastPlannerReport()) {
            return;
        }

        this.neoecoae$fastPlannerReportRouted = true;
        CraftConfirmScreen screen = (CraftConfirmScreen) (Object) this;
        switchToScreen(new ECOCraftConfirmScreen(
                this.menu,
                this.menu.getPlayerInventory(),
                screen.getTitle(),
                appeng.client.gui.style.StyleManager.loadStyleDoc("/screens/eco_craft_confirm.json")));
    }
}
