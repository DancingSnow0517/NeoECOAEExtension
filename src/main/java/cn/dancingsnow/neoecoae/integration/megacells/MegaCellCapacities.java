package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.api.IECOTier;

public final class MegaCellCapacities {
    public static final long MEGA_4G_CAPACITY = 1L << 32;
    public static final int MEGA_4G_TYPE_LIMIT = 315;
    public static final int LONG_BULK_TYPE_LIMIT = 25;

    private MegaCellCapacities() {
    }

    public static int normalBytesPerType(IECOTier tier) {
        return 1 << (12 + tier.getTier());
    }

    public static double normalIdleDrain(long capacity) {
        return (double) capacity / (1L << 22);
    }

}
