package cn.dancingsnow.neoecoae.items.cell;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.IECOTier;

public interface IBasicECOCellItem {
    IECOTier getTier();
    AEKeyType getKeyType();
    long getBytes();
    int getBytesPerType();
    int getTotalTypes();
}
