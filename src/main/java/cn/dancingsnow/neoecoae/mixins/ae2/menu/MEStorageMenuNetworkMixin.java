package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.networking.IGridNode;
import appeng.api.storage.ITerminalHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECONetworkIdHost;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Synchronizes the terminal's runtime ME network id so client crafting pins stay isolated per network. */
@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuNetworkMixin extends AEBaseMenu implements ECONetworkIdHost {
    @Unique
    @GuiSync(12020)
    public long neoecoae$networkId;

    protected MEStorageMenuNetworkMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/api/storage/ITerminalHost;Z)V",
        at = @At("TAIL")
    )
    private void neoecoae$initNetworkId(
            MenuType<?> menuType, int id, Inventory ip, ITerminalHost host, boolean bindInventory, CallbackInfo ci) {
        neoecoae$updateNetworkId();
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void neoecoae$syncNetworkId(CallbackInfo ci) {
        neoecoae$updateNetworkId();
    }

    @Unique
    private void neoecoae$updateNetworkId() {
        if (!isServerSide()) {
            return;
        }
        IGridNode node = ((MEStorageMenu) (Object) this).getGridNode();
        neoecoae$networkId = node == null ? 0L : ECOCraftingNetworkSettings.networkId(node.getGrid());
    }

    @Override
    public long neoecoae$getNetworkId() {
        return neoecoae$networkId;
    }
}
