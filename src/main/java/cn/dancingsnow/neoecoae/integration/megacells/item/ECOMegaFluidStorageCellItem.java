package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;

import java.util.function.Supplier;

public final class ECOMegaFluidStorageCellItem extends ECOStorageCellItem {
    public ECOMegaFluidStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(properties, tier, AEKeyType.fluids(), type, capacity,
            MegaCellCapacities.bytesPerType(capacity), MegaCellCapacities.idleDrain(capacity));
    }
}
