package cn.dancingsnow.neoecoae.api;

import lombok.Getter;

public enum ECOTier implements IECOTier {
    L4(1, 24, 32, 64, 1, 1 << 26, 1 << 24),
    L6(2, 72, 96, 192, 2, 1 << 28, 1 << 26),
    L9(3, 256, 384, 576, 4, 1 << 30, 1 << 28);
    @Getter
    private final int tier;
    @Getter
    private final int crafterParallel;
    @Getter
    private final int overclockedCrafterParallel;
    private final int cpuAccelerators;
    private final int cpuThreads;
    private final long cpuTotalBytes;
    @Getter
    private final long storageTotalBytes;

    ECOTier(
        int tire,
        int crafterParallel,
        int overclockedCrafterParallel,
        int cpuAccelerators,
        int cpuThreads,
        long cpuTotalBytes,
        long storageTotalBytes
    ) {
        this.tier = tire;
        this.crafterParallel = crafterParallel;
        this.overclockedCrafterParallel = overclockedCrafterParallel;
        this.cpuAccelerators = cpuAccelerators;
        this.cpuThreads = cpuThreads;
        this.cpuTotalBytes = cpuTotalBytes;
        this.storageTotalBytes = storageTotalBytes;
    }

    @Override
    public int getCPUAccelerators() {
        return cpuAccelerators;
    }

    @Override
    public int getCPUThreads() {
        return cpuThreads;
    }

    @Override
    public long getCPUTotalBytes() {
        return cpuTotalBytes;
    }
}
