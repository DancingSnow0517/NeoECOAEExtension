package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaBulkStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ECOMegaItemStorageCellItem extends ECOStorageCellItem {
    public ECOMegaItemStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(properties, tier, AEKeyType.items(), type, capacity,
            MegaCellCapacities.bytesPerType(capacity), MegaCellCapacities.idleDrain(capacity));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        MegaCellTooltips.append(this, lines);
        var inventory = getCellInventory(stack);
        if (inventory instanceof ECOMegaBulkStorageCell bulk) {
            if (bulk.getFilterItem() == null) {
                lines.add(Component.translatable("tooltip.neoecoae.megacells.configure_item")
                    .withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.translatable("tooltip.neoecoae.megacells.partitioned_for",
                        bulk.getFilterItem().getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("tooltip.neoecoae.megacells.auto_compression",
                    Component.translatable(bulk.isCompressionEnabled()
                        ? "tooltip.neoecoae.megacells.enabled"
                        : "tooltip.neoecoae.megacells.disabled"))
                .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public int getTotalTypes() {
        return MegaCellCapacities.typeLimit(getBytes(), super.getTotalTypes());
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return getBytes() == MegaCellCapacities.OMNI_ASSEMBLED_4G_CAPACITY
            ? CellConfig.create(Set.of(AEKeyType.items()), stack, 1)
            : super.getConfigInventory(stack);
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, ISaveProvider host) {
        return getBytes() == MegaCellCapacities.OMNI_ASSEMBLED_4G_CAPACITY
            ? new ECOMegaBulkStorageCell(stack, host)
            : super.createCellInventory(stack, host);
    }
}
