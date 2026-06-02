package cn.dancingsnow.neoecoae.items;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ECOComputationCellItem extends Item {
    @Getter
    private final IECOTier tier;
    public ECOComputationCellItem(Properties properties, IECOTier tier) {
        super(properties);
        this.tier = tier;
    }

    public long getBytes() {
        return NEConfig.getEcoComputationCellCapacity(tier, tier.getCPUTotalBytes());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.getItem() instanceof ECOComputationCellItem cellItem) {
            tooltipComponents.add(Component.translatable(
                "tooltip.neoecoae.computation_cell",
                Tooltips.ofUnformattedNumber(cellItem.getBytes())
            ));
        }
    }
}
