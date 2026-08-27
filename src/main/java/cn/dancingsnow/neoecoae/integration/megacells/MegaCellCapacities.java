package cn.dancingsnow.neoecoae.integration.megacells;

public final class MegaCellCapacities {
    public static final long BASE_16M_CAPACITY = 1L << 24;
    public static final long BASE_64M_CAPACITY = 1L << 26;
    public static final long BASE_256M_CAPACITY = 1L << 28;
    public static final int COMPRESSION_INPUT_COUNT = 10;
    public static final long COMPRESSED_4G_CAPACITY = 1L << 32;

    private MegaCellCapacities() {
    }

    public static int bytesPerType(long capacity) {
        return Math.toIntExact(capacity / 128L);
    }

    public static double idleDrain(long capacity) {
        return (double) capacity / (1L << 20);
    }
}
