package cn.dancingsnow.neoecoae.api.me.provider;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;
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
 * <p>Preparation must not consume resources. Dispatch returns true only after accepting every input and
 * output in the batch. False or an ordinary exception guarantees no batch was accepted; the caller then
 * restores inputs. If acceptance is uncertain, throw {@link ECOIndeterminateBatchException} instead.
 * Providers never debit or refund the CPU inventory or energy themselves.</p>
 */
public interface ECOFastPathDispatchProvider {
    /** Resolves FastPath capacity, material rules and the matching commit target. */
    @Nullable
    Preparation eco$prepareFastPath(ECOBatchDispatchContext context);

    /** Explicit big-order contract. All quantities stay exact; legacy implementations opt out. */
    default @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
        return null;
    }

    record ExactPreparation(BigInteger craftCount, java.util.function.BooleanSupplier dispatch) {
        public ExactPreparation {
            if (craftCount.signum() <= 0) throw new IllegalArgumentException("Invalid exact batch");
            Objects.requireNonNull(dispatch);
        }
    }

    /** Exact inputs are opt-in: only a provider accepting a count plus unit receipt may enable them. */
    record Preparation(
        long capacity,
        @Nullable ECOStatefulBatchCalculator statefulCalculator,
        boolean statefulCalculatorRequired,
        Predicate<Batch> dispatch,
        boolean supportsExactInputs
    ) {
        public Preparation(long capacity, @Nullable ECOStatefulBatchCalculator calculator,
                boolean calculatorRequired, Predicate<Batch> dispatch) {
            this(capacity, calculator, calculatorRequired, dispatch, false);
        }

        public Preparation {
            if (capacity <= 0L) throw new IllegalArgumentException("capacity must be positive");
            Objects.requireNonNull(dispatch, "dispatch");
        }

        public boolean push(Batch batch) {
            return batch.craftCount() <= capacity && dispatch.test(batch);
        }
    }

    /** exactInputTotal is populated only for an exact-order debit; legacy batches use inputTotal. */
    record Batch(
        long craftCount,
        List<GenericStack> inputTotal,
        List<GenericStack> outputTotal,
        List<GenericStack> remainingTotal,
        Map<AEKey, BigInteger> exactInputTotal
    ) {
        public Batch(long count, List<GenericStack> inputs, List<GenericStack> outputs, List<GenericStack> remainders) {
            this(count, inputs, outputs, remainders, Map.of());
        }

        /** Legacy providers cannot accidentally consume a truncated exact input amount. */
        @Override public List<GenericStack> inputTotal() {
            if (inputTotal.isEmpty() && !exactInputTotal.isEmpty()) {
                return exactInputTotal.entrySet().stream()
                    .map(entry -> new GenericStack(entry.getKey(), entry.getValue().longValueExact())).toList();
            }
            return inputTotal;
        }

        public Batch {
            if (craftCount <= 0L) throw new IllegalArgumentException("craftCount must be positive");
            exactInputTotal = Map.copyOf(exactInputTotal);
            inputTotal = List.copyOf(inputTotal);
            outputTotal = List.copyOf(outputTotal);
            remainingTotal = List.copyOf(remainingTotal);
        }
    }
}
