package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum ECOTier implements IECOTier {
    L4(
        1,
        24,
        32,
        64,
        4,
        1 << 26,
        1 << 24,
        10_000_000,
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/computation/cpu_overlay/l4.png")
    ),
    L6(
        2,
        72,
        96,
        192,
        16,
        1 << 28,
        1 << 26,
        100_000_000,
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/computation/cpu_overlay/l6.png")
    ),
    L9(
        3,
        256,
        384,
        576,
        64,
        1 << 30,
        1 << 28,
        1_000_000_000,
        ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/computation/cpu_overlay/l9.png")
    );
    @Getter
    private final int tier;
    private final int crafterParallel;
    private final int overclockedCrafterParallel;
    private final int cpuAccelerators;
    private final int cpuThreads;
    private final long cpuTotalBytes;
    @Getter
    private final long storageTotalBytes;
    @Getter
    private final long powerStorageSize;
    private final ResourceLocation cpuOverlayTexture;

    ECOTier(
        int tier,
        int crafterParallel,
        int overclockedCrafterParallel,
        int cpuAccelerators,
        int cpuThreads,
        long cpuTotalBytes,
        long storageTotalBytes,
        long powerStorageSize,
        ResourceLocation cpuOverlayTexture
    ) {
        this.tier = tier;
        this.crafterParallel = crafterParallel;
        this.overclockedCrafterParallel = overclockedCrafterParallel;
        this.cpuAccelerators = cpuAccelerators;
        this.cpuThreads = cpuThreads;
        this.cpuTotalBytes = cpuTotalBytes;
        this.storageTotalBytes = storageTotalBytes;
        this.powerStorageSize = powerStorageSize;
        this.cpuOverlayTexture = cpuOverlayTexture;
    }

    @Override
    public Component getPanelTierName() {
        return Component.literal(name());
    }

    @Override
    public int getCrafterParallel() {
        return crafterParallel;
    }

    @Override
    public int getOverclockedCrafterParallel() {
        return overclockedCrafterParallel;
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

    @Override
    public ResourceLocation getCPUOverlayTexture() {
        return cpuOverlayTexture;
    }
}
