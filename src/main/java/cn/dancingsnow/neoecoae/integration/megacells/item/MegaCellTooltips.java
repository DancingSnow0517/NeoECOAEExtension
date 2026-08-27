package cn.dancingsnow.neoecoae.integration.megacells.item;

import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

final class MegaCellTooltips {
    private MegaCellTooltips() {
    }

    static void append(ECOStorageCellItem cell, List<Component> lines) {
        lines.add(Component.translatable("tooltip.neoecoae.megacells.storage_type", cell.getCellType().desc())
            .withStyle(ChatFormatting.DARK_GRAY));

        if (cell.getBytes() == MegaCellCapacities.BASE_256M_CAPACITY) {
            lines.add(Component.translatable("tooltip.neoecoae.megacells.compressible",
                    MegaCellCapacities.COMPRESSION_INPUT_COUNT)
                .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.neoecoae.megacells.empty_only")
                .withStyle(ChatFormatting.DARK_GRAY));
        } else if (cell.getBytes() == MegaCellCapacities.COMPRESSED_4G_CAPACITY) {
            lines.add(Component.translatable("tooltip.neoecoae.megacells.compressed")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable("tooltip.neoecoae.megacells.empty_only")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
