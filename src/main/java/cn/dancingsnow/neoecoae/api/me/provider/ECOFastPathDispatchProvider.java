package cn.dancingsnow.neoecoae.api.me.provider;

import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOStatefulBatchCalculator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * FastPath-only provider capability.
 *
 * <p>This contract is for verified ECO/F9 execution and provider integrations that can commit a
 * complete batch synchronously. Processing-pattern dispatch has a separate contract and must not
 * be added here.</p>
 */
public interface ECOFastPathDispatchProvider {
    /** Resolves FastPath capacity, material rules and the matching commit target. */
    @Nullable
    Preparation eco$prepareFastPath(ECOBatchDispatchContext context);

    record Preparation(
        long capacity,
        @Nullable ECOStatefulBatchCalculator statefulCalculator,
        boolean statefulCalculatorRequired,
        Predicate<Batch> dispatch
    ) {
        public Preparation {
            if (capacity <= 0L) throw new IllegalArgumentException("capacity must be positive");
            Objects.requireNonNull(dispatch, "dispatch");
        }

        public boolean push(Batch batch) {
            return batch.craftCount() <= capacity && dispatch.test(batch);
        }
    }

    record Batch(
        long craftCount,
        List<GenericStack> inputTotal,
        List<GenericStack> outputTotal,
        List<GenericStack> remainingTotal
    ) {
        public Batch {
            if (craftCount <= 0L) throw new IllegalArgumentException("craftCount must be positive");
            inputTotal = List.copyOf(inputTotal);
            outputTotal = List.copyOf(outputTotal);
            remainingTotal = List.copyOf(remainingTotal);
        }
    }
}
