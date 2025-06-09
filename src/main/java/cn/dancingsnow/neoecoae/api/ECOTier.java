package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

public enum ECOTier implements IECOTier {
    L4(
        1,
        24,
        32,
        64,
        1,
        1 << 26,
        1 << 24,
        10_000_000,
        NeoECOAE.id("textures/gui/cpu_overlay/l4.png"),
        GuiTextures.Crafting.F4
    ),
    L6(
        2,
        72,
        96,
        192,
        2,
        1 << 28,
        1 << 26,
        100_000_000,
        NeoECOAE.id("textures/gui/cpu_overlay/l6.png"),
        GuiTextures.Crafting.F6
    ),
    L9(
        3,
        256,
        384,
        576,
        4,
        1 << 30,
        1 << 28,
        1_000_000_000,
        NeoECOAE.id("textures/gui/cpu_overlay/l9.png"),
        GuiTextures.Crafting.F9
    );
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
    @Getter
    private final long powerStorageSize;
    private final ResourceLocation cpuOverlayTexture;
    @Getter
    private final IGuiTexture craftingOverlayTexture;

    ECOTier(
        int tier,
        int crafterParallel,
        int overclockedCrafterParallel,
        int cpuAccelerators,
        int cpuThreads,
        long cpuTotalBytes,
        long storageTotalBytes,
        long powerStorageSize,
        ResourceLocation cpuOverlayTexture,
        IGuiTexture craftingOverlayTexture
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
        this.craftingOverlayTexture = craftingOverlayTexture;
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
