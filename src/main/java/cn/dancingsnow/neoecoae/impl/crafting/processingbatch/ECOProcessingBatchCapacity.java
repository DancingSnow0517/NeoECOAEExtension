package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Read-only capacity captured for one fixed Pattern Provider target. */
public final class ECOProcessingBatchCapacity {
    private final ECOProcessingBatchTarget target;
    private final long maxCrafts;

    private ECOProcessingBatchCapacity(ECOProcessingBatchTarget target, long maxCrafts) {
        this.target = Objects.requireNonNull(target, "target");
        this.maxCrafts = maxCrafts;
    }

    /**
     * Simulates the aggregate input keys without changing provider routing state.
     *
     * <p>Returning {@code null} means that the target could not be represented safely. Callers
     * must use the ordinary one-craft provider path in that case.</p>
     */
    @Nullable
    public static ECOProcessingBatchCapacity capture(
            ECOProcessingBatchTarget target,
            KeyCounter[] prototype,
            long requestedCrafts) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(prototype, "prototype");
        if (requestedCrafts <= 0L) {
            throw new IllegalArgumentException("requestedCrafts must be positive");
        }

        try {
            KeyCounter aggregated = aggregate(prototype);
            if (!aggregated.iterator().hasNext()) {
                // An empty processing input cannot start multiple machine operations by itself.
                return new ECOProcessingBatchCapacity(target, 1L);
            }

            long admitted = requestedCrafts;
            for (var entry : aggregated) {
                long perCraft = entry.getLongValue();
                long requestedAmount = Math.multiplyExact(perCraft, requestedCrafts);
                long inserted = target.insert(entry.getKey(), requestedAmount, Actionable.SIMULATE);
                if (inserted < 0L || inserted > requestedAmount) {
                    return null;
                }
                admitted = Math.min(admitted, inserted / perCraft);
                if (admitted <= 0L) {
                    break;
                }
            }
            return new ECOProcessingBatchCapacity(target, admitted);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public ECOProcessingBatchTarget target() {
        return target;
    }

    public long maxCrafts() {
        return maxCrafts;
    }

    private static KeyCounter aggregate(KeyCounter[] prototype) {
        KeyCounter aggregated = new KeyCounter();
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter counter = prototype[index];
            if (counter == null) {
                throw new IllegalArgumentException("prototype counter at index " + index + " is null");
            }
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (key == null || amount < 0L) {
                    throw new IllegalArgumentException("prototype contains an invalid input");
                }
                if (amount > 0L) {
                    aggregated.set(key, Math.addExact(aggregated.get(key), amount));
                }
            }
        }
        return aggregated;
    }
}
