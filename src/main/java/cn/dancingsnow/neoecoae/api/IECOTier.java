package cn.dancingsnow.neoecoae.api;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.resources.ResourceLocation;

public interface IECOTier {
    int getTier();
    /**
     * 鍚堟垚绯荤粺骞惰鏍稿績骞惰鏁伴噺
     *
     * @return 骞惰鏁伴噺
     */
    int getCrafterParallel();

    /**
     * 鍚堟垚绯荤粺骞惰鏍稿績瓒呴鍚庡苟琛屾暟閲?
     *
     * @return 瓒呴鍚庡苟琛屾暟閲?
     */
    int getOverclockedCrafterParallel();

    default int getOverclockedCrafterQueueMultiply() {
        return 2 << getTier();
    }

    default int getOverclockedCrafterPowerMultiply() {
        return getOverclockedCrafterQueueMultiply();
    }
    /**
     * 璁＄畻绯荤粺骞惰鏍稿績骞惰鏁伴噺
     *
     * @return 骞惰鏁伴噺
     */
    int getCPUAccelerators();

    /**
     * 璁＄畻绯荤粺绾跨▼鏍稿績绾跨▼鏁伴噺
     *
     * @return 绾跨▼鏁伴噺
     */
    int getCPUThreads();

    /**
     * 璁＄畻绯荤粺闂瓨鏅堕樀瀛楄妭鏁伴噺
     *
     * @return 闂瓨鏅堕樀瀛楄妭鏁伴噺
     */
    long getCPUTotalBytes();

    /**
     * 瀛樺偍绯荤粺瀛樺偍鐭╅樀瀛楄妭鏁伴噺
     *
     * @return 瀛樺偍鐭╅樀瀛楄妭鏁伴噺
     */
    long getStorageTotalBytes();

    /**
     * 瀛樺偍绯荤粺鍌ㄧ數鏂瑰潡姣忎釜鏂瑰潡鍌ㄧ數閲?
     *
     * @return 鍌ㄧ數閲?
     */
    long getPowerStorageSize();

    ResourceLocation getCPUOverlayTexture();

    default ResourceLocation getCraftingOverlayTexture() {
        return NeoECOAE.id("textures/gui/crafting/f0.png");
    }

    /**
     * 瀛樺偍绯荤粺瀛樺偍鐭╅樀绫诲瀷鏁伴噺
     *
     * @param keyType 鏍规�?{@link AEKeyType} 涓嶅悓锛岀被鍨嬫暟�?
     * @return 瀛樺偍鐭╅樀绫诲瀷鏁伴噺
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
