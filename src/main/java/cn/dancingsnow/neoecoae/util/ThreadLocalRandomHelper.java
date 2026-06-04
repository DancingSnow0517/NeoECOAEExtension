package cn.dancingsnow.neoecoae.util;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;

public class ThreadLocalRandomHelper {
    private static final Map<Thread, RandomSource> randomSourceMap = new IdentityHashMap<>();

    public static RandomSource getRandom() {
        synchronized (randomSourceMap) {
            Thread thread = Thread.currentThread();
            if (randomSourceMap.containsKey(thread)) {
                return randomSourceMap.get(thread);
            }
            RandomSource randomSource = RandomSource.create();
            randomSourceMap.put(thread, randomSource);
            return randomSource;
        }
    }
}
