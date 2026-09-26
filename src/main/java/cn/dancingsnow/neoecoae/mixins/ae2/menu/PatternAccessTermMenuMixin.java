package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.helpers.InventoryAction;
import appeng.menu.implementations.PatternAccessTermMenu;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternAccessContainerTrackerAccessor;
import cn.dancingsnow.neoecoae.util.PatternInventoryTransfer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternAccessTermMenu.class)
public abstract class PatternAccessTermMenuMixin {
    @Shadow @Final
    private Long2ObjectOpenHashMap<?> byId;

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void neoecoae$movePatternRegion(ServerPlayer player, InventoryAction action, int slot, long id,
                                            CallbackInfo ci) {
        if (action != InventoryAction.MOVE_REGION) return;
        Object tracker = byId.get(id);
        if (!(tracker instanceof PatternAccessContainerTrackerAccessor access)
            || !(access.neoecoae$getContainer() instanceof ECOCraftingPatternBusBlockEntity bus)) return;
        ci.cancel();
        var inventory = access.neoecoae$getServerInventory();
        if (slot < 0 || slot >= inventory.size()) return;
        bus.beginPatternBatch();
        try {
            PatternInventoryTransfer.moveRegion(inventory, player.getInventory()::add);
        } finally {
            bus.endPatternBatch();
        }
    }
}
