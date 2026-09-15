package cn.dancingsnow.neoecoae.mixins;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ITerminalHost;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.api.PatternEncodingTermMenuExtension;
import java.util.Locale;
import net.minecraft.network.chat.Component;
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

@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuMixin extends MEStorageMenu implements PatternEncodingTermMenuExtension {
    @Unique private static final String NEOECOAE_ACTION_UPLOAD_PATTERN = "neoecoae:uploadPattern";

    @Unique private static final String NEOECOAE_ACTION_AUTO_UPLOAD = "neoecoae:autoUploadPattern";

    @Unique private boolean neoecoae$autoUpload;

    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;

    @Shadow
    public EncodingMode mode;

    public PatternEncodingTermMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(
            method =
                    "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V",
            at = @At("RETURN"))
    private void neoecoae$registerUploadAction(
            MenuType<?> menuType,
            int id,
            Inventory playerInventory,
            IPatternTerminalMenuHost host,
            boolean bindInventory,
            CallbackInfo ci) {
        registerClientAction(NEOECOAE_ACTION_UPLOAD_PATTERN, this::neoecoae$uploadPattern);
        registerClientAction(NEOECOAE_ACTION_AUTO_UPLOAD, this::neoecoae$toggleAutoUpload);
    }

    @Override
    public void neoecoae$toggleAutoUpload() {
        if (isClientSide()) {
            sendClientAction(NEOECOAE_ACTION_AUTO_UPLOAD);
            return;
        }
        if (!canInteractWithGrid()) return;
        neoecoae$autoUpload = !neoecoae$autoUpload;
        neoecoae$notifyUpload(neoecoae$autoUpload ? "auto_enabled" : "auto_disabled");
    }

    // Run only after a successful encode writes its output, never on an early return or clear.
    @Inject(method = "encode", at = @At(value = "RETURN", ordinal = 3))
    private void neoecoae$uploadAfterEncode(CallbackInfo ci) {
        if (!isClientSide()
                && neoecoae$autoUpload
                && PatternDetailsHelper.isEncodedPattern(encodedPatternSlot.getItem())) neoecoae$uploadPattern();
    }

    @Unique private void neoecoae$notifyUpload(String result) {
        getPlayer().displayClientMessage(Component.translatable("neoecoae.pattern_upload." + result), false);
    }

    @Override
    public void neoecoae$uploadPattern() {
        if (isClientSide()) {
            sendClientAction(NEOECOAE_ACTION_UPLOAD_PATTERN);
            return;
        }

        IGridNode node = getNetworkNode();
        if (node == null || !node.isActive() || !canInteractWithGrid()) {
            neoecoae$notifyUpload("unavailable");
            return;
        }

        if (this.mode == EncodingMode.PROCESSING) {
            neoecoae$notifyUpload("incompatible");
            return;
        }

        ItemStack pattern = this.encodedPatternSlot.getItem();
        if (pattern.isEmpty()) {
            neoecoae$notifyUpload("empty");
            return;
        }
        if (!(PatternDetailsHelper.decodePattern(pattern, getPlayer().level())
                instanceof IMolecularAssemblerSupportedPattern)) {
            neoecoae$notifyUpload("incompatible");
            return;
        }

        IECOPatternStorageService service = node.getGrid().getService(IECOPatternStorageService.class);
        ECOPatternInsertionResult result = service == null
                ? ECOPatternInsertionResult.NO_TARGET
                : service.getPatternStorage().insertPattern(pattern.copyWithCount(1));
        if (result == ECOPatternInsertionResult.INSERTED) {
            ItemStack remaining = pattern.copy();
            remaining.shrink(1);
            this.encodedPatternSlot.set(remaining);
            this.encodedPatternSlot.setChanged();
        }
        neoecoae$notifyUpload(result.name().toLowerCase(Locale.ROOT));
    }
}
