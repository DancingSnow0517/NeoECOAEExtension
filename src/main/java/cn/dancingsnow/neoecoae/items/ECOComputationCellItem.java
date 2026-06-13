package cn.dancingsnow.neoecoae.items;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class ECOComputationCellItem extends Item {
    @Getter
    private final IECOTier tier;
    public ECOComputationCellItem(Properties properties, IECOTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (stack.getItem() instanceof ECOComputationCellItem cellItem) {
            IECOTier tier = cellItem.getTier();
            tooltipComponents.accept(Component.translatable(
                "tooltip.neoecoae.computation_cell",
                Tooltips.ofUnformattedNumber(tier.getCPUTotalBytes())
            ));
        }
    }
}
