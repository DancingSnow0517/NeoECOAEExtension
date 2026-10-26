package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.SetStockAmountMenu;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmount;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmountMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SetStockAmountMenu.class)
public abstract class SetStockAmountMenuMixin extends AEBaseMenu implements FluidHatchStockAmountMenu {
    @Shadow @Final private InterfaceLogicHost host;
    @Shadow private AEKey whatToStock;
    @Shadow private int slot;

    @Unique private static final String NEOECOAE_SET_STOCK = "neoecoae:setFluidStockAmount";
    @Unique @GuiSync(10) private long neoecoae$initialStockAmount = -1;
    @Unique @GuiSync(11) private long neoecoae$maxStockAmount = -1;

    protected SetStockAmountMenuMixin(MenuType<?> type, int id, Inventory inventory, Object host) {
        super(type, id, inventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neoecoae$registerLongAmount(int id, Inventory inventory, InterfaceLogicHost host, CallbackInfo ci) {
        if (host instanceof ECOFluidInputHatchBlockEntity) {
            registerClientAction(NEOECOAE_SET_STOCK, Long.class, this::neoecoae$confirmStockAmount);
        }
    }

    @Inject(method = "setWhatToStock", at = @At("TAIL"))
    private void neoecoae$initializeLongAmount(int slot, AEKey key, int initialAmount, CallbackInfo ci) {
        if (host instanceof ECOFluidInputHatchBlockEntity hatch && slot == 0 && key instanceof AEFluidKey) {
            neoecoae$initialStockAmount = hatch.getTargetAmount();
            neoecoae$maxStockAmount = hatch.getConfig().getMaxAmount(key);
        }
    }

    @Override public long neoecoae$getInitialStockAmount() { return neoecoae$initialStockAmount; }
    @Override public long neoecoae$getMaxStockAmount() { return neoecoae$maxStockAmount; }

    @Override public void neoecoae$confirmStockAmount(long amount) {
        if (!(host instanceof ECOFluidInputHatchBlockEntity hatch)) return;
        if (isClientSide()) {
            if (amount >= 0) sendClientAction(NEOECOAE_SET_STOCK, amount);
            return;
        }
        if (getPlayer().containerMenu != this || !hatch.canPlayerUse(getPlayer()) || !stillValid(getPlayer())) return;
        if (slot == 0) FluidHatchStockAmount.apply(hatch, getPlayer(), this, whatToStock, amount);
        hatch.returnToMainMenu(getPlayer(), (SetStockAmountMenu) (Object) this);
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void neoecoae$guardNativeIntAction(int amount, CallbackInfo ci) {
        if (host instanceof ECOFluidInputHatchBlockEntity) {
            neoecoae$confirmStockAmount(amount);
            ci.cancel();
        }
    }

    @Override public boolean stillValid(Player player) {
        return super.stillValid(player)
            && (!(host instanceof ECOFluidInputHatchBlockEntity hatch) || hatch.canPlayerUse(player));
    }
}
