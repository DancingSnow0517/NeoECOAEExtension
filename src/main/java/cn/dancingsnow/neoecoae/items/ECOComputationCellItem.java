package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ECOComputationCellItem extends Item implements IECOComputationCellItem {
    @Getter
    private final IECOTier tier;
    public ECOComputationCellItem(Properties properties, IECOTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.getItem() instanceof IECOComputationCellItem cellItem) {
            IECOTier tier = cellItem.getTier();
            tooltipComponents.add(Component.translatable(
                "tooltip.neoecoae.computation_cell",
                Tooltips.ofUnformattedNumber(cellItem.getComputationBytes(stack))
            ));
            tooltipComponents.add(Component.translatable("tooltip.neoecoae.computation_cell.install"));
        }
    }
}
