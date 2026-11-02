package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells;

import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.OmniLongCapacityProvider;
import com.wintercogs.ae2omnicells.common.init.OCDataComponents;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps OmniCells' cached item state consistent with the long-capacity inventory bridge. */
@Pseudo
@Mixin(value = IAEUniversalCell.class, remap = false)
public interface IAEUniversalCellMixin {
    @Inject(method = "setCellState", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$setLongCapacityState(
        ItemStack stack,
        IAEUniversalCell cellType,
        long usedBytes,
        int usedTypes,
        CallbackInfo ci
    ) {
        if (!(cellType instanceof OmniLongCapacityProvider provider)) {
            return;
        }

        long totalBytes = provider.getOmniTotalBytes();
        int totalTypes = cellType.getTotalTypes();
        CellState state;
        if (usedBytes <= 0L && usedTypes <= 0) {
            state = CellState.EMPTY;
        } else if (totalBytes > 0L && usedBytes >= totalBytes) {
            state = CellState.FULL;
        } else if (totalTypes > 0 && usedTypes >= totalTypes) {
            state = CellState.TYPES_FULL;
        } else {
            state = CellState.NOT_EMPTY;
        }
        stack.set(OCDataComponents.CELL_STATE.get(), state.name());
        ci.cancel();
    }
}
