package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Supplier;

public final class ECOMegaFluidStorageCellItem extends ECOStorageCellItem {
    public ECOMegaFluidStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(properties, tier, AEKeyType.fluids(), type, capacity,
            MegaCellCapacities.bytesPerType(capacity), MegaCellCapacities.idleDrain(capacity));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        MegaCellTooltips.append(this, lines);
    }
}
