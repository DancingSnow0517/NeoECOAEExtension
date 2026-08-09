package cn.dancingsnow.neoecoae.integration.ae2omnicells.item;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.localization.PlayerMessages;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalCellHandler;
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
import net.minecraft.world.entity.player.Inventory;
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
    private static final long MAX_EXTERNAL_STORAGE_BYTES = (long) Integer.MAX_VALUE / 1024L * 1024L;

    @Getter
    private final IECOTier tier;
    private final Supplier<ECOCellType> cellType;
    private final long ecoStorageTotalBytes;
    private final boolean externallyUnlimited;

    public ECOUniversalStorageCellItem(
        Properties properties,
        IECOTier tier,
        Supplier<ECOCellType> cellType,
        double idleDrain,
        int totalTypes
    ) {
        this(properties, tier, cellType, idleDrain, totalTypes, tier.getStorageTotalBytes());
    }

    public ECOUniversalStorageCellItem(
        Properties properties,
        IECOTier tier,
        Supplier<ECOCellType> cellType,
        double idleDrain,
        int totalTypes,
        long totalBytes
    ) {
        super(properties.stacksTo(1), idleDrain, totalTypes, externalKilobytes(totalBytes));
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
        return Set.copyOf(AEKeyTypes.getAll());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (isExternallyUnlimited()) {
            lines.add(AEUniversalTooltips.bytesUsed(IAEUniversalCell.getUsedBytes(stack), getECOStorageTotalBytes()));
            long usedTypes = IAEUniversalCell.getUsedTypes(stack);
            lines.add(AEUniversalTooltips.typesUsed(usedTypes, getTotalTypes()));
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
        if (InteractionUtil.isInAlternateUseMode(player)) {
            disassembleDrive(stack, level, player);
            return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
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
        if (player != null && InteractionUtil.isInAlternateUseMode(player)) {
            return disassembleDrive(stack, context.getLevel(), player)
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
        }
        return super.onItemUseFirst(stack, context);
    }

    private boolean disassembleDrive(ItemStack stack, Level level, Player player) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
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

        var cellInventory = ECOUniversalCellHandler.INSTANCE.getCellInventory(stack, null);
        if (cellInventory != null && !cellInventory.getAvailableStacks().isEmpty()) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);

        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }

        getUpgrades(stack).forEach(playerInventory::placeItemBackInInventory);
        return true;
    }
}
