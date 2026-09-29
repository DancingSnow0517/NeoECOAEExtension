package cn.dancingsnow.neoecoae.items;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/** Hard-locked source and sink for water, cobblestone, and lava. */
public class ECOInfiniteResourceCellItem extends ECOStorageCellItem {
    private static final List<AEKey> LOCKED_KEYS =
            List.of(AEFluidKey.of(Fluids.WATER), AEItemKey.of(Items.COBBLESTONE), AEFluidKey.of(Fluids.LAVA));

    public ECOInfiniteResourceCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> cellType) {
        super(
                properties,
                tier,
                AEKeyType.items(),
                cellType,
                Long.MAX_VALUE,
                1 << (12 + tier.getTier()),
                LOCKED_KEYS.size(),
                (double) tier.getStorageTotalBytes() / (1 << 20));
    }

    public static List<AEKey> lockedKeys() {
        return LOCKED_KEYS;
    }

    public static boolean isLockedKey(@Nullable AEKey key) {
        return key != null && LOCKED_KEYS.contains(key);
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return Set.of(AEKeyType.items(), AEKeyType.fluids());
    }

    @Override
    public boolean isBlackListed(ItemStack stack, AEKey key) {
        return !isLockedKey(key);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        // Fixed workbench view: edits to this object never persist into the item.
        ConfigInventory config =
                ConfigInventory.configTypes(ECOInfiniteResourceCellItem::isLockedKey, LOCKED_KEYS.size(), () -> {});
        for (int slot = 0; slot < LOCKED_KEYS.size(); slot++) {
            config.setStack(slot, new appeng.api.stacks.GenericStack(LOCKED_KEYS.get(slot), 1));
        }
        return config;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 4);
    }

    @Override
    protected ECOInfiniteResourceCell createCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return new ECOInfiniteResourceCell(stack, host);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        lines.add(Component.translatable("tooltip.neoecoae.infinite_resource.contents")
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.neoecoae.infinite_resource.unbounded")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.neoecoae.infinite_resource.sink")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
