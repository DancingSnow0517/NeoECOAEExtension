package cn.dancingsnow.neoecoae.api;

import appeng.api.stacks.AEKeyType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class ECOAETypeCounts {
    private static final Object2IntMap<AEKeyType> byType = new Object2IntOpenHashMap<>();

    static {
        register(AEKeyType.items(), 315);
        register(AEKeyType.fluids(), 25);
    }

    public static void register(AEKeyType type, int count) {
        byType.put(type, count);
    }

    public static int getByType(AEKeyType keyType) {
        return byType.getInt(keyType);
    }
}
