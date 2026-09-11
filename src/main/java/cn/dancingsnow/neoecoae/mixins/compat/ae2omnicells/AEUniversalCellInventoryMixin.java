package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.OmniLongCapacityProvider;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges ECO's long cell capacity into OmniCells' existing long-based inventory accounting.
 */
@Mixin(value = AEUniversalCellInventory.class, remap = false)
public abstract class AEUniversalCellInventoryMixin {
    @Shadow @Final @Mutable private long totalBytesEff;

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
        }
    }
}
