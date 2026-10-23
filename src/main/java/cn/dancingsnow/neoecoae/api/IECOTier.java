package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface IECOTier {
    int getTier();

    /** Default draw of ordinary components in AE/t. Specialized roles retain their own baseline. */
    default double getComponentIdlePower() { return 16.0D; }

    @Nullable
    default cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition getBuildDefinition() { return null; }

    @Nullable
    default cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition getBuildDefinition(
        ECOMachineKind kind) { return getBuildDefinition(); }

    default boolean supportsFeature(ECOFeature feature) {
        return switch (feature) {
            case NETWORK_SWITCH, VIRTUAL_CRAFTING -> getTier() == ECOTier.L9.getTier();
            case INFINITE_STORAGE_HOST, INFINITE_STORAGE_CELL -> this == ECOTier.L9;
        };
    }

    /** Display name used by host panels and storage-cell tooltips. */
    default Component getPanelTierName() {
        return Component.literal("L" + getTier());
    }

    /**
     * Optional 13x16 texture: upper marker in the first 13x8 row, lower marker in the second.
     * Panels scale each row to their marker area. Null retains the built-in atlas markers.
     */
    @Nullable
    default ResourceLocation getHostMarkerTexture() {
        return null;
    }

    /** Optional complete panel atlas, addressed by its existing ui-eco name without the prefix. */
    @Nullable
    default ResourceLocation getPanelAtlas(String atlas) {
        return null;
    }
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
        return 2 << getTier();
    }

    default int getOverclockedCrafterPowerMultiply() {
        return getOverclockedCrafterQueueMultiply();
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

    ResourceLocation getCPUOverlayTexture();

    default int compareTo(IECOTier tier) {
        return Integer.compare(this.getTier(), tier.getTier());
    }

    default boolean supportsComponentTier(IECOTier componentTier) {
        return compareTo(componentTier) >= 0;
    }
}
