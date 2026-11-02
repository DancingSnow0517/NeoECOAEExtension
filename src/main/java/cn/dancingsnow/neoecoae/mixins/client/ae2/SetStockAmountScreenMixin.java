package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.implementations.SetStockAmountMenu;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchLongAmountWidget;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmountMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(appeng.client.gui.me.crafting.SetStockAmountScreen.class)
public abstract class SetStockAmountScreenMixin extends AEBaseScreen<SetStockAmountMenu> {
    @Shadow @Final private NumberEntryWidget amount;
    @Shadow private boolean amountInitialized;

    protected SetStockAmountScreenMixin(SetStockAmountMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neoecoae$enableLongAmount(SetStockAmountMenu menu, Inventory inventory, Component title,
        ScreenStyle style, CallbackInfo ci) {
        if (menu.getHost() instanceof ECOFluidInputHatchBlockEntity) {
            ((FluidHatchLongAmountWidget) amount).neoecoae$enableLongFluidAmount();
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"), cancellable = true)
    private void neoecoae$initializeLongAmount(CallbackInfo ci) {
        if (!(menu.getHost() instanceof ECOFluidInputHatchBlockEntity)) return;
        super.updateBeforeRender();
        var data = (FluidHatchStockAmountMenu) menu;
        if (!amountInitialized && menu.getWhatToStock() != null
            && data.neoecoae$getInitialStockAmount() >= 0 && data.neoecoae$getMaxStockAmount() >= 0) {
            amount.setType(NumberEntryType.of(menu.getWhatToStock()));
            amount.setMaxValue(data.neoecoae$getMaxStockAmount());
            amount.setLongValue(data.neoecoae$getInitialStockAmount());
            amountInitialized = true;
        }
        ci.cancel();
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void neoecoae$confirmLongAmount(CallbackInfo ci) {
        if (menu.getHost() instanceof ECOFluidInputHatchBlockEntity) {
            if (amountInitialized) amount.getLongValue().ifPresent(((FluidHatchStockAmountMenu) menu)::neoecoae$confirmStockAmount);
            ci.cancel();
        }
    }
}
