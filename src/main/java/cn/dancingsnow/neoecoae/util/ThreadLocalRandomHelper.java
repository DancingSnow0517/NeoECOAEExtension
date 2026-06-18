package cn.dancingsnow.neoecoae.util;

import net.minecraft.util.RandomSource;

public class ThreadLocalRandomHelper {
    private static final ThreadLocal<RandomSource> RANDOM_SOURCE = ThreadLocal.withInitial(RandomSource::create);

    public static RandomSource getRandom() {
        return RANDOM_SOURCE.get();
    }
}
