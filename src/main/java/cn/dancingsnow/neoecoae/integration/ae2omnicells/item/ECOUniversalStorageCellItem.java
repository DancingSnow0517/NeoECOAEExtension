package cn.dancingsnow.neoecoae.integration.ae2omnicells.item;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import com.wintercogs.ae2omnicells.common.items.AEUniversalCellItem;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import com.wintercogs.ae2omnicells.common.me.localization.AEUniversalTooltips;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class ECOUniversalStorageCellItem extends AEUniversalCellItem implements IECOStorageCellItem {
    @Getter
    private final IECOTier tier;
    private final Supplier<ECOCellType> cellType;

    public ECOUniversalStorageCellItem(
        Properties properties,
        IECOTier tier,
        Supplier<ECOCellType> cellType,
        double idleDrain,
        int totalTypes
    ) {
        super(properties.stacksTo(1), idleDrain, totalTypes, Math.toIntExact(tier.getStorageTotalBytes() / 1024L));
        this.tier = tier;
        this.cellType = cellType;
    }

    @Override
    public ECOCellType getCellType() {
        return cellType.get();
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return Set.copyOf(AEKeyTypes.getAll());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (getTotalTypes() < 0) {
            lines.add(AEUniversalTooltips.bytesUsed(IAEUniversalCell.getUsedBytes(stack), getTotalBytes()));
            long usedTypes = IAEUniversalCell.getUsedTypes(stack);
            lines.add(Component.empty()
                .append(Tooltips.ofUnformattedNumberWithRatioColor(usedTypes, Long.MAX_VALUE, false))
                .append(Tooltips.of(" "))
                .append(Tooltips.of(GuiText.Types)));
            return;
        }
        super.appendHoverText(stack, context, lines, tooltipFlag);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return ECOInfiniteStorageMember.isMember(stack) ? Optional.empty() : super.getTooltipImage(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ECOInfiniteStorageMember.isMember(stack) && InteractionUtil.isInAlternateUseMode(player)) {
            player.displayClientMessage(Component.translatable("tooltip.neoecoae.storage.infinite_member"), true);
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && ECOInfiniteStorageMember.isMember(stack) && InteractionUtil.isInAlternateUseMode(player)) {
            player.displayClientMessage(Component.translatable("tooltip.neoecoae.storage.infinite_member"), true);
            return InteractionResult.FAIL;
        }
        return super.onItemUseFirst(stack, context);
    }
}
