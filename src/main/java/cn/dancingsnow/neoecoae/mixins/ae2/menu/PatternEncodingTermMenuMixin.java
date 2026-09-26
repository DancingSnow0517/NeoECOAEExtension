package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertion;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
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
        IGrid grid = node.getGrid();
        if (grid == null) return;
        ItemStack itemStack = encodedPatternSlot.getItem();
        IECOPatternStorageService service = grid.getService(IECOPatternStorageService.class);
        if (service == null || itemStack.isEmpty()) return;

        var details = PatternDetailsHelper.decodePattern(itemStack, getPlayer().level());
        if (details == null) return;

        ECOPatternInsertion insertion = service.insertPreparedPatternReporting(
                new ECOPreparedPattern(itemStack.copy(), details, AEItemKey.of(itemStack)));
        if (insertion.result() == ECOPatternInsertionResult.INSERTED) {
            if (!insertion.consumedSource()) {
                // A bus slot stores the encoded pattern item, so this is a move and owes no blank pattern.
                encodedPatternSlot.clearStack();
                return;
            }

            if (neoecoae$returnReplacement(grid, insertion.blankReplacement())) {
                encodedPatternSlot.clearStack();
            }
            return;
        }

        if (insertion.result() == ECOPatternInsertionResult.ALREADY_PRESENT
                && neoecoae$returnReplacement(grid, service.blankPatternReplacementFor(itemStack.copy()))) {
            // The item is being consumed even though the recipe was already reachable, so settle it with a blank.
            encodedPatternSlot.clearStack();
        }
    }

    @Unique
    private boolean neoecoae$returnReplacement(IGrid grid, ItemStack replacement) {
        if (grid == null || replacement.isEmpty()) return false;
        var inventory = grid.getStorageService().getInventory();
        AEItemKey key = AEItemKey.of(replacement);
        if (key == null) return false;
        IActionSource source = IActionSource.ofPlayer(getPlayer());
        long accepted = inventory.insert(key, replacement.getCount(), Actionable.SIMULATE, source);
        if (accepted < replacement.getCount()) return false;
        return inventory.insert(key, replacement.getCount(), Actionable.MODULATE, source)
                >= replacement.getCount();
    }
}
