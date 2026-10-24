package cn.dancingsnow.neoecoae.integration.megacells.item;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import java.util.function.Supplier;

public final class ECOMegaEnergyStorageCellItem extends ECOStorageCellItem {
    public ECOMegaEnergyStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(properties, tier, FluxKeyType.TYPE, type, capacity,
            MegaCellCapacities.normalBytesPerType(tier), MegaCellCapacities.normalIdleDrain(capacity));
    }

}
