package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchCapacityProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Scales only the accepted dispatch, preserving the confirmed ECO plan and its logical task counts. */
final class ECOUselessScaledBatchDispatch implements ECOBatchCapacityProvider {
    private static final BigInteger MAX_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);
    private final ICraftingProvider provider;

    ECOUselessScaledBatchDispatch(ICraftingProvider provider) {
        this.provider = provider;
    }

    static boolean supports(ICraftingProvider provider) {
        return provider instanceof AdvancedAlloyFurnaceBlockEntity || provider instanceof MePatternAssemblyBlockEntity;
    }

    @Nullable
    private CraftingTaskContext onlineContext() {
        var entity = (BlockEntity) provider;
        var level = entity.getLevel();
        if (level == null || level.isClientSide || entity.isRemoved()) return null;
        if (provider instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            return furnace.getMainNode().isActive() ? furnace : null;
        }
        var assembly = (MePatternAssemblyBlockEntity) provider;
        return assembly.getMainNode().isActive() ? assembly.getController() : null;
    }

    @Override
    public @Nullable Preparation eco$prepareBatch(ECOBatchDispatchContext context) {
        var pattern = context.pattern();
        var prototype = context.inputCounters();
        long capacity = availableCount(pattern, prototype, Long.MAX_VALUE);
        if (capacity <= 0L) return null;
        return new Preparation(capacity, null, false,
            batch -> pushBatch(pattern, prototype, batch.craftCount()));
    }

    private long availableCount(IPatternDetails pattern, KeyCounter[] prototype, long requested) {
        if (requested <= 0) return 0;
        var context = onlineContext();
        if (context == null) return 0;
        var execution = SmartDoublingPatterns.resolve(pattern);
        var original = execution.pattern();
        if (!provider.getAvailablePatterns().contains(original)) return 0;
        long operations = execution.operationsPerPush();
        long maximum = Math.min(requested, SmartDoublingPatterns.maximumSafeMultiplier(original) / operations);
        Map<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        for (var counter : prototype) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0) throw new IllegalArgumentException("Negative batch input");
                if (amount > 0) {
                    maximum = Math.min(maximum, Long.MAX_VALUE / amount);
                    inputs.merge(entry.getKey(), BigInteger.valueOf(amount), BigInteger::add);
                }
            }
        }
        if (maximum == 0) return 0;
        var recipe = context.resolveTaskRecipe(pattern, List.of(), List.of(), compact(inputs), operations);
        if (recipe == null) return 0;
        long manual = SmartDoublingPatterns.manualOperationsPerPattern(recipe, original);
        if (manual <= 0) return 0;
        BigInteger wrapper = BigInteger.valueOf(operations);
        BigInteger actualOperations = wrapper.multiply(BigInteger.valueOf(manual));
        Map<AEKey, BigInteger> outputs = new LinkedHashMap<>();
        boolean recipeOutputs = original instanceof OmniversalPatternDetails
            || original instanceof DynamicComponentPattern dynamic && dynamic.usesDynamicOutputs();
        if (recipeOutputs) {
            for (var item : recipe.outputs()) merge(outputs, GenericStack.fromItemStack(item));
            for (var fluid : recipe.outputFluids()) merge(outputs, GenericStack.fromFluidStack(fluid));
            for (var output : recipe.keyOutputs()) merge(outputs, output);
            return limit(maximum, outputs, actualOperations);
        }
        for (var output : original.getOutputs()) merge(outputs, output);
        maximum = limit(maximum, outputs, wrapper);
        Map<AEKey, BigInteger> hidden = new LinkedHashMap<>();
        for (var output : recipe.keyOutputs()) {
            if (!outputs.containsKey(output.what())) merge(hidden, output);
        }
        return limit(maximum, hidden, actualOperations);
    }

    private boolean pushBatch(IPatternDetails pattern, KeyCounter[] prototype, long count) {
        if (availableCount(pattern, prototype, count) < count || count <= 0) return false;
        var scaledPattern = SmartDoublingPatterns.scale(pattern, count);
        KeyCounter[] scaled = new KeyCounter[prototype.length];
        for (int slot = 0; slot < prototype.length; slot++) {
            scaled[slot] = new KeyCounter();
            for (var entry : prototype[slot]) {
                scaled[slot].add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), count));
            }
        }
        return provider.pushPattern(scaledPattern, scaled);
    }

    private static void merge(Map<AEKey, BigInteger> totals, @Nullable GenericStack stack) {
        if (stack != null && stack.amount() > 0) {
            totals.merge(stack.what(), BigInteger.valueOf(stack.amount()), BigInteger::add);
        }
    }

    private static long limit(long maximum, Map<AEKey, BigInteger> outputs, BigInteger operations) {
        for (var amount : outputs.values()) {
            maximum = Math.min(maximum, MAX_AMOUNT.divide(amount.multiply(operations)).longValueExact());
        }
        return maximum;
    }

    private static List<GenericStack> compact(Map<AEKey, BigInteger> inputs) {
        var result = new ArrayList<GenericStack>();
        inputs.forEach((key, amount) -> {
            while (amount.compareTo(MAX_AMOUNT) > 0) {
                result.add(new GenericStack(key, Long.MAX_VALUE));
                amount = amount.subtract(MAX_AMOUNT);
            }
            if (amount.signum() > 0) result.add(new GenericStack(key, amount.longValueExact()));
        });
        return result;
    }
}
