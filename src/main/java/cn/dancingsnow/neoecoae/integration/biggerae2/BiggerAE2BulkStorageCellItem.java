package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The item Digital Singularity shares ECO's bulk compression and marker implementation. */
final class BiggerAE2BulkStorageCellItem extends ECOBulkStorageCellItem {
    BiggerAE2BulkStorageCellItem(Properties properties, Supplier<ECOCellType> type) {
        super(properties.rarity(Rarity.EPIC), ECOTier.L9, type);
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
