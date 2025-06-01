package cn.dancingsnow.neoecoae.api;

import appeng.api.stacks.AEKeyType;

public interface IECOTier {
    int getTier();
    /**
     * 合成系统并行核心并行数量
     *
     * @return 并行数量
     */
    int getCrafterParallel();

    /**
     * 合成系统并行核心超频后并行数量
     *
     * @return 超频后并行数量
     */
    int getOverclockedCrafterParallel();

    default int getOverclockedCrafterQueueMultiply() {
        return 1 << getTier();
    }

    default int getOverclockedCrafterPowerMultiply() {
        return 2 * getOverclockedCrafterQueueMultiply();
    }
    /**
     * 计算系统并行核心并行数量
     *
     * @return 并行数量
     */
    int getCPUAccelerators();

    /**
     * 计算系统线程核心线程数量
     *
     * @return 线程数量
     */
    int getCPUThreads();

    /**
     * 计算系统闪存晶阵字节数量
     *
     * @return 闪存晶阵字节数量
     */
    long getCPUTotalBytes();

    /**
     * 存储系统存储矩阵字节数量
     *
     * @return 存储矩阵字节数量
     */
    long getStorageTotalBytes();

    /**
     * 存储系统储电方块每个方块储电量
     *
     * @return 储电量
     */
    long getPowerStorageSize();

    /**
     * 存储系统存储矩阵类型数量
     *
     * @param keyType 根据 {@link AEKeyType} 不同，类型数量
     * @return 存储矩阵类型数量
     */
    default int getStorageTotalTypes(AEKeyType keyType) {
        return ECOAETypeCounts.getByType(keyType);
    }

    default int compareTo(IECOTier tier) {
        return Integer.compare(this.getTier(), tier.getTier());
    }
}
