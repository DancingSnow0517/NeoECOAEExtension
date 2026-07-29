package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;
import java.util.Set;

/** Metadata exposed by storage matrices that can be mounted in an ECO drive. */
public interface IECOStorageCellItem {
    IECOTier getTier();

    ECOCellType getCellType();

    Set<AEKeyType> getKeyTypes();
}
