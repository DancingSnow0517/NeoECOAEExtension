package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.IGridNode;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternEncodingTermMenu.class)
public class PatternEncodingTermMenuMixin extends MEStorageMenu implements PatternEncodingTermMenuExtension {
    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;
    @Shadow
    private EncodingMode currentMode;
    @Unique
    private final String ACTION_UPLOAD_PATTERN = "neoecoae:uploadPattern";

    public PatternEncodingTermMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/me/items/PatternEncodingTermMenu;registerClientAction(Ljava/lang/String;Ljava/lang/Runnable;)V",
            ordinal = 0
        )
    )
    void onRegisterClientActions(
        MenuType<?> menuType,
        int id,
        Inventory ip,
        IPatternTerminalMenuHost host,
        boolean bindInventory,
        CallbackInfo ci
    ) {
        registerClientAction(ACTION_UPLOAD_PATTERN, this::neoecoae$uploadPattern);
    }

    @Override
    public void neoecoae$uploadPattern() {
        if (isClientSide()) {
            sendClientAction(ACTION_UPLOAD_PATTERN);
            return;
        }
        IGridNode node = getGridNode();
        if (node == null) return;
        if (!getLinkStatus().connected()) return;
        if (currentMode == EncodingMode.PROCESSING) return;
        ItemStack itemStack = encodedPatternSlot.getItem();
        IECOPatternStorageService service = node.getGrid().getService(IECOPatternStorageService.class);
        if (service != null) {
            if (service.getPatternStorage().insertPattern(itemStack.copy())) {
                encodedPatternSlot.clearStack();
            }
        }
    }
}
