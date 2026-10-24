package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.OmniLongCapacityProvider;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges ECO's long cell capacity into OmniCells' existing long-based inventory accounting.
 */
@Pseudo
@Mixin(value = AEUniversalCellInventory.class, remap = false)
public abstract class AEUniversalCellInventoryMixin {
    @Shadow @Final @Mutable private long totalBytesEff;
    @Shadow @Final private ItemStack itemStack;
    @Shadow @Final private IAEUniversalCell cellType;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$useLongCapacity(
        AEUniversalCellData cellData,
        ItemStack stack,
        IAEUniversalCell cellType,
        ISaveProvider saveContainer,
        CallbackInfo ci
    ) {
        if (cellType instanceof OmniLongCapacityProvider provider) {
            totalBytesEff = provider.getOmniTotalBytes();
            neoecoae$updateLongCapacityState();
        }
    }

    // LDLib1-era Mixin's annotation processor rejects injectors declared in an interface mixin.
    // The inventory is the producer of this same cached state, so bridge it here on Forge 1.20.
    @Inject(method = "updateItemTooltipState", at = @At("RETURN"))
    private void neoecoae$refreshLongCapacityState(CallbackInfo ci) {
        neoecoae$updateLongCapacityState();
    }

    private void neoecoae$updateLongCapacityState() {
        if (!(cellType instanceof OmniLongCapacityProvider provider)) return;
        long usedBytes = IAEUniversalCell.getUsedBytes(itemStack);
        int usedTypes = IAEUniversalCell.getUsedTypes(itemStack);
        CellState state;
        if (usedBytes <= 0L && usedTypes <= 0) state = CellState.EMPTY;
        else if (provider.getOmniTotalBytes() > 0L && usedBytes >= provider.getOmniTotalBytes()) state = CellState.FULL;
        else if (cellType.getTotalTypes() > 0 && usedTypes >= cellType.getTotalTypes()) state = CellState.TYPES_FULL;
        else state = CellState.NOT_EMPTY;
        itemStack.getOrCreateTag().putString(IAEUniversalCell.CELL_STATE_TAG_NAME, state.name());
    }
}
