package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.ExactPreparation;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/**
 * Exact orders already debit every input in ECO. The released Useless crafting commit
 * consumes a one-copy receipt and stores BigInteger outputs, so its long-window capacity
 * estimate is unnecessary here. Recipe batches retain their native energy/capacity checks.
 *
 * <p><b>Updated for Useless 1.21.1-2.4.2+</b>: Uses public {@link AlloyFurnaceBigIntegerTarget}
 * API exclusively; no reflection required.</p>
 */
final class ECOUselessExactCraftingDispatch {
    private ECOUselessExactCraftingDispatch() {}

    static @Nullable ExactPreparation prepare(Object target, ECOBatchDispatchContext context,
            BigInteger requested) {
        if (!(target instanceof AlloyFurnaceBigIntegerTarget bigIntTarget)
                || !(context.pattern() instanceof IMolecularAssemblerSupportedPattern)
                || requested.signum() <= 0) return null;

        KeyCounter[] receipt = context.inputCounters();
        // Admit returns null if machine cannot accept the batch right now
        AlloyFurnaceBigIntegerBatch batch = bigIntTarget.admit(
            context.pattern(), receipt, requested, null);
        if (batch == null) return null;

        return new ExactPreparation(batch.count(), () -> {
            try {
                // Public API commit retains structure, execution, recipe assembly and backlog checks.
                // Output delivery remains owned by Useless; callbacks must not credit outputs again.
                // The batch must be committed with the same receipt array (API contract requirement).
                return batch.commit(receipt);
            } catch (IllegalStateException | IllegalArgumentException failure) {
                throw new ECOIndeterminateBatchException(
                    "Useless exact crafting commit ownership is uncertain", failure);
            }
        });
    }
}
