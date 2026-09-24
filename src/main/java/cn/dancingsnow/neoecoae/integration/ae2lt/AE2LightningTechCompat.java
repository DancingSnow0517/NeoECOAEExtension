package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.api.stacks.AEKeyType;

final class AE2LightningTechCompat {
    private AE2LightningTechCompat() {}

    static AEKeyType getLightningKeyType() {
        try {
            Class<?> type = Class.forName("com.moakiee.ae2lt.me.key.LightningKeyType");
            return (AEKeyType) type.getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AE2 Lightning Tech key type is unavailable", e);
        }
    }
}
