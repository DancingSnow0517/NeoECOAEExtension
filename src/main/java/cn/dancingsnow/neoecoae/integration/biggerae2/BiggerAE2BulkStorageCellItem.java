package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.AEConfig;
import appeng.core.localization.Tooltips;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The item Digital Singularity shares ECO's bulk compression and marker implementation. */
final class BiggerAE2BulkStorageCellItem extends ECOBulkStorageCellItem {
    BiggerAE2BulkStorageCellItem(Properties properties, Supplier<ECOCellType> type) {
        super(properties.rarity(Rarity.EPIC), ECOTier.L9, type, 5);
    }

    @Override
    protected int upgradedTypeLimit() { return 25; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var cell = getCellInventory(stack);
        if (cell == null) return;
        long used = cell.getStoredItemTypes();
        long total = cell.getTotalItemTypes();
        lines.add(Component.translatable("tooltip.neoecoae.bulk.used_types",
            Tooltips.ofUnformattedNumberWithRatioColor(used, (double) used / total, false),
            Tooltips.ofUnformattedNumber(total)));
        lines.add(Component.translatable("tooltip.neoecoae.bulk.compression",
            Component.translatable("tooltip.neoecoae.bulk.compression_enabled").withStyle(ChatFormatting.GREEN)));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var cell = getCellInventory(stack);
        if (cell == null) return Optional.empty();
        var available = cell.getAvailableStacks();
        List<GenericStack> content = new ArrayList<>();
        for (var entry : available) content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        var config = getConfigInventory(stack);
        for (int slot = 0; slot < config.size(); slot++) {
            var key = config.getKey(slot);
            if (key != null && available.get(key) <= 0) content.add(new GenericStack(key, 0));
        }
        if (content.isEmpty()) return Optional.empty();
        content.sort(Comparator.comparingLong(GenericStack::amount).reversed());
        int maxShown = Math.max(1, AEConfig.instance().getTooltipMaxCellContentShown());
        boolean hasMore = content.size() > maxShown;
        if (hasMore) content.subList(maxShown, content.size()).clear();
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, hasMore, true));
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        var legacy = stack.get(BiggerAE2SingularityContents.TYPE);
        if (legacy != null) {
            Map<AEItemKey, BigInteger> contents = new LinkedHashMap<>();
            for (var entry : legacy.entries()) {
                if (!(entry.key() instanceof AEItemKey key)) {
                    throw new IllegalStateException("Non-item contents in an item Digital Singularity");
                }
                contents.merge(key, entry.amount(), BigInteger::add);
            }
            // Persist the exact replacement before removing the old component or notifying the host.
            new ECOBulkStorageCell(stack, null, contents).persist();
            stack.remove(BiggerAE2SingularityContents.TYPE);
            if (host != null) host.saveChanges();
        }
        return super.createCellInventory(stack, host);
    }
}
