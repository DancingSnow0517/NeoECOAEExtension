package cn.dancingsnow.neoecoae.integration.megacells.item;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ECOMegaEnergyStorageCellItem extends ECOStorageCellItem {
    public ECOMegaEnergyStorageCellItem(
            Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(
                properties,
                tier,
                FluxKeyType.TYPE,
                type,
                capacity,
                MegaCellCapacities.normalBytesPerType(tier),
                1,
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
