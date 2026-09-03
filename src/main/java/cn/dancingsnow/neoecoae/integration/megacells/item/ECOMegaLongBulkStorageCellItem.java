package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaLongBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ECOMegaLongBulkStorageCellItem extends ECOStorageCellItem {
    public ECOMegaLongBulkStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type) {
        super(properties, tier, AEKeyType.items(), type, Long.MAX_VALUE,
            MegaCellCapacities.normalBytesPerType(tier), 1024.0);
    }

    @Override
    public int getTotalTypes() {
        return MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(Set.of(AEKeyType.items()), stack, MegaCellCapacities.LONG_BULK_TYPE_LIMIT);
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, ISaveProvider host) {
        return new ECOMegaLongBulkStorageCell(stack, host);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        MegaCellTooltips.append(this, lines);
        lines.add(Component.translatable("tooltip.neoecoae.megacells.configure_item")
            .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.neoecoae.megacells.compression_builtin")
            .withStyle(ChatFormatting.GRAY));
    }
}
