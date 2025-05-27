package cn.dancingsnow.neoecoae.items.cell;

import appeng.api.stacks.AEKeyType;

public interface IBasicECOCellItem {
    AEKeyType getKeyType();
    long getBytes();
    int getBytesPerType();
    int getTotalTypes();
}
