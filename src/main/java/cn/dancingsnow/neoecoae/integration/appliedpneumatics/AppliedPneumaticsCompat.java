package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import appeng.api.stacks.AEKeyType;

final class AppliedPneumaticsCompat {
    private AppliedPneumaticsCompat() {}

    static AEKeyType getAirKeyType() {
        try {
            Class<?> type = Class.forName("com.wintercogs.appliedpneumatics.common.me.keys.types.AirKeyType");
            return (AEKeyType) type.getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Applied Pneumatics air key type is unavailable", e);
        }
    }
}
