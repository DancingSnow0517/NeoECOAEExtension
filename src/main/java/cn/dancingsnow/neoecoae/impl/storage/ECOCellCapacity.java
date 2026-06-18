package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.CellState;
import com.google.common.math.LongMath;

record ECOCellCapacity(
        AEKeyType keyType,
        long totalBytes,
        int bytesPerType,
        long totalItemTypes,
        long storedItemTypes,
        long storedItemCount) {

    CellState status() {
        if (storedItemTypes == 0) {
            return CellState.EMPTY;
        }
        if (canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (remainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    long usedBytes() {
        long roundedItemCount = LongMath.saturatedAdd(storedItemCount, unusedItemCount());
        long bytesForItemCount = roundedItemCount / keyType.getAmountPerByte();
        long typeBytes = LongMath.saturatedMultiply(storedItemTypes, bytesPerType);
        return LongMath.saturatedAdd(typeBytes, bytesForItemCount);
    }

    long freeBytes() {
        return totalBytes - usedBytes();
    }

    int unusedItemCount() {
        int div = (int) (storedItemCount % keyType.getAmountPerByte());
        if (div == 0) {
            return 0;
        }
        return keyType.getAmountPerByte() - div;
    }

    long remainingItemCount() {
        long remaining = LongMath.saturatedAdd(
                LongMath.saturatedMultiply(freeBytes(), keyType.getAmountPerByte()), unusedItemCount());
        return remaining > 0 ? remaining : 0;
    }

    long remainingItemTypes() {
        long basedOnStorage = freeBytes() / bytesPerType;
        long basedOnTotal = totalItemTypes - storedItemTypes;
        return Math.min(basedOnStorage, basedOnTotal);
    }

    boolean canHoldNewItem() {
        long bytesFree = freeBytes();
        return (bytesFree > bytesPerType || bytesFree == bytesPerType && unusedItemCount() > 0)
                && remainingItemTypes() > 0;
    }
}
