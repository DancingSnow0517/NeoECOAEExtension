package cn.dancingsnow.neoecoae.crafting.execution.batch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.Objects;
import java.util.function.Predicate;

/** Material inputs scale with crafts; configuration inputs are sent once per accepted batch. */
public record ECOBatchInputScaling(Predicate<AEKey> perBatch) {
    public static final ECOBatchInputScaling LINEAR = new ECOBatchInputScaling(key -> false);

    public ECOBatchInputScaling {
        Objects.requireNonNull(perBatch, "perBatch");
    }

    public long multiplier(AEKey key, long crafts) {
        if (crafts < 0) throw new IllegalArgumentException("Negative craft count");
        return crafts == 0 ? 0 : perBatch.test(key) ? 1 : crafts;
    }

    public KeyCounter scale(KeyCounter source, long crafts) {
        var result = new KeyCounter();
        for (var entry : source) {
            result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier(entry.getKey(), crafts)));
        }
        return result;
    }

    public KeyCounter[] scale(KeyCounter[] source, long crafts) {
        var result = new KeyCounter[source.length];
        for (int i = 0; i < source.length; i++) result[i] = scale(source[i], crafts);
        return result;
    }
}
