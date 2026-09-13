package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Calculates the physical material contract for a verified reusable/durability batch.
 *
 * <p>Unlike an ordinary linear batch, a stateful batch cannot multiply every first-craft input by the
 * requested craft count. The verified state model owns that calculation: an unlimited reusable crystal,
 * for example, is extracted once for the whole batch and returned once after it completes.</p>
 */
public final class ECOStatefulBatchCalculator {
    private final ECOVerifiedFastPathRecipe recipe;

    private ECOStatefulBatchCalculator(ECOVerifiedFastPathRecipe recipe) {
        this.recipe = recipe;
    }

    @Nullable
    public static ECOStatefulBatchCalculator create(
            @Nullable ECOVerifiedFastPathRecipe recipe, ECOExtractedPatternExecution execution) {
        if (recipe == null || recipe.reusableStateModel() == null || !recipe.isVerifiedFor(execution)) {
            return null;
        }
        return new ECOStatefulBatchCalculator(recipe);
    }

    public long arithmeticBatchLimit() {
        return recipe.statefulDispatchArithmeticBatchLimit();
    }

    public long maxCraftsFromInventory(ListCraftingInventory inventory, long requested) {
        long bounded = Math.min(Math.max(0L, requested), arithmeticBatchLimit());
        ECODurabilityBatchModel durability = recipe.durabilityModel();
        if (durability != null && durability.supportsToolPool()) {
            var batch = durability.prepareToolPoolBatch(
                inventory, recipe.inputsPerCraft(), recipe.remainingPerCraft(), bounded);
            return batch == null ? 0L : batch.craftCount();
        }
        return ECOBatchCraftingHelper.maxBatchSizeFromBatchInputs(
            inventory, bounded, (java.util.function.LongFunction<List<GenericStack>>) recipe::batchInputs);
    }

    @Nullable
    public BatchContract prepareBatch(ListCraftingInventory inventory, long requested) {
        long bounded = Math.min(Math.max(0L, requested), arithmeticBatchLimit());
        if (bounded <= 0L) return null;
        ECODurabilityBatchModel durability = recipe.durabilityModel();
        if (durability != null && durability.supportsToolPool()) {
            var batch = durability.prepareToolPoolBatch(
                inventory, recipe.inputsPerCraft(), recipe.remainingPerCraft(), bounded);
            return batch == null ? null
                : new BatchContract(batch.craftCount(), batch.inputs(), batch.remainders());
        }
        long crafts = ECOBatchCraftingHelper.maxBatchSizeFromBatchInputs(
            inventory, bounded, (java.util.function.LongFunction<List<GenericStack>>) recipe::batchInputs);
        return crafts <= 0L ? null
            : new BatchContract(crafts, recipe.batchInputs(crafts), recipe.batchRemainders(crafts));
    }

    public List<GenericStack> batchInputs(long craftCount) {
        return recipe.batchInputs(craftCount);
    }

    public List<GenericStack> batchRemainders(long craftCount) {
        return recipe.batchRemainders(craftCount);
    }

    public record BatchContract(long craftCount, List<GenericStack> inputs, List<GenericStack> remainders) {
        public BatchContract {
            if (craftCount <= 0L) throw new IllegalArgumentException("craftCount must be positive");
            inputs = List.copyOf(inputs);
            remainders = List.copyOf(remainders);
        }
    }
}
