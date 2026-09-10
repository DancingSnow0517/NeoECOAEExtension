package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ECOMegaItemStorageCellItem extends ECOStorageCellItem {
    public ECOMegaItemStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(
                properties,
                tier,
                AEKeyType.items(),
                type,
                capacity,
                MegaCellCapacities.normalBytesPerType(tier),
                MegaCellCapacities.MEGA_4G_TYPE_LIMIT,
                MegaCellCapacities.normalIdleDrain(capacity));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
            List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        MegaCellTooltips.append(this, lines);
    }
}
