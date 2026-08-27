package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;

import java.util.Set;

public interface IECOStorageCellItem {
    IECOTier getTier();

    ECOCellType getCellType();

    Set<AEKeyType> getKeyTypes();
}
