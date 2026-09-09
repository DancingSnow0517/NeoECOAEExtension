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
        if (recipe == null || recipe.reusableStateModel() == null
                || !recipe.batchSafe() || !recipe.isVerifiedFor(execution)) {
            return null;
        }
        return new ECOStatefulBatchCalculator(recipe);
    }

    public long arithmeticBatchLimit() {
        return recipe.arithmeticBatchLimit();
    }

    public long maxCraftsFromInventory(ListCraftingInventory inventory, long requested) {
        long bounded = Math.min(Math.max(0L, requested), arithmeticBatchLimit());
        return ECOBatchCraftingHelper.maxBatchSizeFromBatchInputs(
            inventory, bounded, (java.util.function.LongFunction<List<GenericStack>>) recipe::batchInputs);
    }

    public List<GenericStack> batchInputs(long craftCount) {
        return recipe.batchInputs(craftCount);
    }

    public List<GenericStack> batchRemainders(long craftCount) {
        return recipe.batchRemainders(craftCount);
    }
}
