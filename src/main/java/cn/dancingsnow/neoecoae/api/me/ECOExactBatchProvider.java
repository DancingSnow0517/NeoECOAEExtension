package cn.dancingsnow.neoecoae.api.me;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

/** Opt-in full-virtual exact batch admission with an atomic provider commit. */
public interface ECOExactBatchProvider {
    @Nullable ExactPreparation eco$prepareExactBatch(ECOBatchDispatchContext context, BigInteger requested);

    record ExactPreparation(BigInteger craftCount, BooleanSupplier dispatch) {
        public ExactPreparation {
            if (craftCount == null || craftCount.signum() <= 0 || dispatch == null) {
                throw new IllegalArgumentException("Invalid exact batch preparation");
            }
        }
    }
}
