package cn.dancingsnow.neoecoae.items;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.core.AEConfig;
import appeng.core.localization.Tooltips;
import appeng.items.storage.StorageCellTooltipComponent;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.items.cell.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.cell.IBasicECOCellItem;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ECOStorageCellItem extends Item implements IBasicECOCellItem {

    @Getter
    private final IECOTier tier;
    private final long totalBytes;
    private final int bytesPerType;
    private final int totalTypes;
    private final AEKeyType keyType;

    public ECOStorageCellItem(Properties properties, IECOTier tier, AEKeyType keyType) {
        super(properties);
        this.tier = tier;
        this.totalBytes = tier.getStorageTotalBytes();
        this.bytesPerType = 1 << (12 + tier.getTier());
        this.totalTypes = tier.getStorageTotalTypes(keyType);
        this.keyType = keyType;
    }

    @Override
    public AEKeyType getKeyType() {
        return keyType;
    }

    @Override
    public long getBytes() {
        return totalBytes;
    }

    @Override
    public int getBytesPerType() {
        return bytesPerType;
    }

    @Override
    public int getTotalTypes() {
        return totalTypes;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        var handler = getCellInventory(stack);
        if (handler == null) {
            return;
        }
        lines.add(Tooltips.bytesUsed(handler.getUsedBytes(), handler.getTotalBytes()));
        lines.add(Tooltips.typesUsed(handler.getStoredItemTypes(), handler.getTotalItemTypes()));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var handler = getCellInventory(stack);
        if (handler == null) {
            return Optional.empty();
        }

        // Find items with the highest stored amount
        boolean hasMoreContent;
        List<GenericStack> content;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ArrayList<>();

            var maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();

            var availableStacks = handler.getAvailableStacks();
            for (var entry : availableStacks) {
                content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }

            // Sort by amount descending
            content.sort(Comparator.comparingLong(GenericStack::amount).reversed());

            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content.subList(maxCountShown, content.size()).clear();
            }
        } else {
            hasMoreContent = false;
            content = Collections.emptyList();
        }

        return Optional.of(new StorageCellTooltipComponent(
            List.of(),
            content,
            hasMoreContent,
            true)
        );
    }

    @Nullable
    public static ECOStorageCell getCellInventory(ItemStack stack) {
        if (stack.getItem() instanceof ECOStorageCellItem) {
            return new ECOStorageCell(stack, null);
        }
        return null;
    }
}
