package cn.dancingsnow.neoecoae.mixins.compat.ae2omnicells;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Extend the upstream item-identity check while retaining its configurable radioactive-chemical policy. */
@Pseudo
@Mixin(targets = "com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory$MekIntegration", remap = false)
public abstract class OmniWasteCellRecognitionMixin {
    @ModifyVariable(method = "allowInsert", at = @At("STORE"), ordinal = 0)
    private static boolean neoecoae$recognizeWasteCell(boolean original, ItemStack stack, AEKey key) {
        return original || stack.getItem() instanceof ECOUniversalStorageCellItem cell
            && cell.isSpentNuclearWasteCell();
    }
}
