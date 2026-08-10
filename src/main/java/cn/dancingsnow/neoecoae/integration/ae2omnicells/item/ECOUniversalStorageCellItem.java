package cn.dancingsnow.neoecoae.integration.ae2omnicells.item;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.localization.Tooltips;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.compat.ae2.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import com.wintercogs.ae2omnicells.common.items.AEUniversalCellItem;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import com.wintercogs.ae2omnicells.common.me.localization.AEUniversalTooltips;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ECOUniversalStorageCellItem extends AEUniversalCellItem implements IECOStorageCellItem {
    // AEUniversalCellItem stores capacity in an int and multiplies this KiB value by 1024 internally.
    private static final long MAX_EXTERNAL_STORAGE_BYTES = (Integer.MAX_VALUE / 1024L) * 1024L;

    @Getter
    private final IECOTier tier;

    private final Supplier<ECOCellType> cellType;
    private final long ecoStorageTotalBytes;
    private final boolean externallyUnlimited;

    public ECOUniversalStorageCellItem(
            Properties properties, IECOTier tier, Supplier<ECOCellType> cellType, double idleDrain, int totalTypes) {
        this(properties, tier, cellType, idleDrain, totalTypes, tier.getStorageTotalBytes());
    }

    public ECOUniversalStorageCellItem(
            Properties properties,
            IECOTier tier,
            Supplier<ECOCellType> cellType,
            double idleDrain,
            int totalTypes,
            long totalBytes) {
        super(
                properties.stacksTo(1),
                idleDrain,
                totalTypes,
                externalKilobytes(totalBytes));
        this.tier = tier;
        this.cellType = cellType;
        this.ecoStorageTotalBytes = totalBytes;
        this.externallyUnlimited = totalBytes > MAX_EXTERNAL_STORAGE_BYTES;
    }

    private static int externalKilobytes(long totalBytes) {
        if (totalBytes > MAX_EXTERNAL_STORAGE_BYTES) {
            return -1;
        }
        return Math.toIntExact(totalBytes / 1024L);
    }

    public long getECOStorageTotalBytes() {
        return ecoStorageTotalBytes;
    }

    public boolean isExternallyUnlimited() {
        return externallyUnlimited;
    }

    @Override
    public ECOCellType getCellType() {
        return cellType.get();
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        Set<AEKeyType> result = new HashSet<>();
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            result.add(keyType);
        }
        return Set.copyOf(result);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag tooltipFlag) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (externallyUnlimited) {
            lines.add(AEUniversalTooltips.bytesUsed(IAEUniversalCell.getUsedBytes(stack), ecoStorageTotalBytes));
            long usedTypes = IAEUniversalCell.getUsedTypes(stack);
            if (getTotalTypes() < 0) {
                lines.add(Component.empty()
                        .append(Tooltips.ofUnformattedNumberWithRatioColor(usedTypes, Long.MAX_VALUE, false))
                        .append(Tooltips.of(" "))
                        .append(Tooltips.of(GuiText.Types)));
            } else {
                lines.add(AEUniversalTooltips.typesUsed(usedTypes, getTotalTypes()));
            }
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
        super.appendHoverText(stack, level, lines, tooltipFlag);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return ECOInfiniteStorageMember.isMember(stack) ? Optional.empty() : super.getTooltipImage(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        disassembleDrive(stack, level, player);
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return disassembleDrive(stack, context.getLevel(), player)
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    private boolean disassembleDrive(ItemStack stack, Level level, Player player) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }
        if (ECOInfiniteStorageMember.isMember(stack)) {
            player.displayClientMessage(Component.translatable("tooltip.neoecoae.storage.infinite_member"), true);
            return false;
        }

        List<ItemStack> disassembledStacks = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        Inventory playerInventory = player.getInventory();
        if (playerInventory.getSelected() != stack) {
            return false;
        }

        if (!level.isClientSide) {
            IECOStorageCell cellInventory = ECOStorageCells.getCellInventory(stack, null);
            if (cellInventory != null && !cellInventory.canFitInsideCell()) {
                player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
                return false;
            }
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);
        for (ItemStack disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }
        getUpgrades(stack).forEach(playerInventory::placeItemBackInInventory);
        return true;
    }
}
