package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ECOMegaStorageCellItem extends ECOStorageCellItem {
    @Nullable
    private final Predicate<AEKey> blacklist;

    public ECOMegaStorageCellItem(
        Properties properties,
        IECOTier tier,
        AEKeyType keyType,
        Supplier<ECOCellType> type,
        long capacity,
        @Nullable Predicate<AEKey> blacklist
    ) {
        super(properties, tier, keyType, type, capacity,
            MegaCellCapacities.normalBytesPerType(tier), MegaCellCapacities.normalIdleDrain(capacity));
        this.blacklist = blacklist;
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return blacklist != null ? blacklist.test(what) : super.isBlackListed(cellStack, what);
    }
}
