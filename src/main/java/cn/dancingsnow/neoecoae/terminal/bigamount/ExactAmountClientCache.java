package cn.dancingsnow.neoecoae.terminal.bigamount;

import appeng.api.stacks.AEKey;
import java.util.Map;

public final class ExactAmountClientCache {
    private static int containerId = -1;
    private static Map<AEKey, ExactAmount> amounts = Map.of();

    private ExactAmountClientCache() {}

    public static void replace(int id, Map<AEKey, ExactAmount> replacement) {
        containerId = id;
        amounts = Map.copyOf(replacement);
    }

    public static ExactAmount get(int id, AEKey key) { return id == containerId ? amounts.get(key) : null; }
    public static void clear(int id) { if (id == containerId) { containerId = -1; amounts = Map.of(); } }
}
