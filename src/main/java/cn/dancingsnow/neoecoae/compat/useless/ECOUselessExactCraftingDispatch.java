package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider.ExactPreparation;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/**
 * Exact orders already debit every input in ECO. The released Useless crafting commit
 * consumes a one-copy receipt and stores BigInteger outputs, so its long-window capacity
 * estimate is unnecessary here. Recipe batches retain their native energy/capacity checks.
 */
final class ECOUselessExactCraftingDispatch {
    private static final @Nullable Api API = Api.load();

    private ECOUselessExactCraftingDispatch() {}

    static @Nullable ExactPreparation prepare(Object target, ECOBatchDispatchContext context,
            BigInteger requested) {
        if (API == null || !API.core.getDeclaringClass().isInstance(target)
                || !(context.pattern() instanceof IMolecularAssemblerSupportedPattern)
                || requested.signum() <= 0) return null;
        final Object core;
        try {
            core = API.core.get(target);
        } catch (IllegalAccessException unavailable) {
            return null;
        }
        if (core == null) return null;
        KeyCounter[] receipt = context.inputCounters();
        return new ExactPreparation(requested, () -> {
            try {
                // Public native commit retains structure, execution, recipe assembly and backlog checks.
                // Output delivery remains owned by Useless; callbacks must not credit outputs again.
                return (boolean) API.push.invoke(core, context.pattern(), requested, receipt, null);
            } catch (InvocationTargetException failure) {
                throw new ECOIndeterminateBatchException(
                    "Useless exact crafting commit ownership is uncertain", failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot invoke Useless exact crafting commit", failure);
            }
        });
    }

    private record Api(Field core, Method push) {
        static @Nullable Api load() {
            try {
                var loader = ECOUselessExactCraftingDispatch.class.getClassLoader();
                var target = Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalBigIntegerTarget",
                    false, loader);
                var binding = Class.forName(
                    "com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding",
                    false, loader);
                var core = target.getDeclaredField("core");
                if (!core.trySetAccessible()) return null;
                var push = core.getType().getMethod("pushBigIntegerBatch", IPatternDetails.class,
                    BigInteger.class, KeyCounter[].class, binding);
                if (push.getReturnType() != boolean.class) return null;
                return new Api(core, push);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return null;
            }
        }
    }
}
