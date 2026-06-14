package cn.dancingsnow.neoecoae.api;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.resources.ResourceLocation;

public interface IECOTier {
    int getTier();

    /**
     * Base parallelism provided by a crafting parallel core of this tier.
     *
     * @return the base crafting parallel count
     */
    int getCrafterParallel();

    /**
     * Parallelism provided by a crafting parallel core when overclocking is enabled.
     *
     * @return the overclocked crafting parallel count
     */
    int getOverclockedCrafterParallel();

    default int getOverclockedCrafterQueueMultiply() {
        return 2 << getTier();
    }

    default int getOverclockedCrafterPowerMultiply() {
        return getOverclockedCrafterQueueMultiply();
    }

    /**
     * Accelerator count provided by a computation component of this tier.
     *
     * @return the accelerator count
     */
    int getCPUAccelerators();

    /**
     * Thread count provided by a computation component of this tier.
     *
     * @return the thread count
     */
    int getCPUThreads();

    /**
     * Computation storage capacity provided by this tier.
     *
     * @return total computation bytes
     */
    long getCPUTotalBytes();

    /**
     * Storage cell capacity provided by this tier.
     *
     * @return total storage bytes
     */
    long getStorageTotalBytes();

    /**
     * Energy capacity provided by storage power blocks of this tier.
     *
     * @return energy storage capacity
     */
    long getPowerStorageSize();

    ResourceLocation getCPUOverlayTexture();

    default ResourceLocation getCraftingOverlayTexture() {
        return NeoECOAE.id("textures/gui/crafting/f0.png");
    }

    /**
     * Maximum number of stored key types supported by this tier.
     *
     * @param keyType AE key type whose type limit should be queried
     * @return total supported type count for the given key type
     */
    default int getStorageTotalTypes(AEKeyType keyType) {
        return ECOAETypeCounts.getByType(keyType);
    }

    default int compareTo(IECOTier tier) {
        return Integer.compare(this.getTier(), tier.getTier());
    }

    default boolean supportsComponentTier(IECOTier componentTier) {
        return compareTo(componentTier) >= 0;
    }
}
