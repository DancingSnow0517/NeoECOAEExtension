package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link ECOVerifiedFastPathRecipe} bound to one concrete batch size and crafting job.
 *
 * <p>This is the credential that travels from the CPU through the Pattern Bus and the Worker into the
 * Crafting Thread. It replaces the old "carry the three per-craft stack lists along and re-compare them at
 * every layer" model: because the recipe credential can only be minted by
 * {@link ECOCraftingFastPathCache} after a full value verification, the later layers only have to check
 * <ul>
 *   <li>that the credential belongs to the offer they selected (reference identity),</li>
 *   <li>that its batch size still fits the live thread capacity,</li>
 *   <li>and that no reload happened since verification.</li>
 * </ul>
 *
 * <p>Immutable, private constructor, package-private factory - external callers cannot fabricate one.
 */
public final class ECOVerifiedFastPathExecution {
    private final ECOVerifiedFastPathRecipe recipe;
    private final int batchSize;
    private final List<GenericStack> inputTotal;
    private final List<GenericStack> outputTotal;
    private final List<GenericStack> remainingTotal;

    @Nullable
    private final UUID craftingJobId;

    private ECOVerifiedFastPathExecution(
        ECOVerifiedFastPathRecipe recipe,
        int batchSize,
        @Nullable UUID craftingJobId,
        List<GenericStack> inputTotal,
        List<GenericStack> outputTotal,
        List<GenericStack> remainingTotal
    ) {
        this.recipe = recipe;
        this.batchSize = batchSize;
        this.craftingJobId = craftingJobId;
        this.inputTotal = List.copyOf(inputTotal);
        this.outputTotal = List.copyOf(outputTotal);
        this.remainingTotal = List.copyOf(remainingTotal);
    }

    static ECOVerifiedFastPathExecution trusted(
        ECOVerifiedFastPathRecipe recipe,
        int batchSize,
        @Nullable UUID craftingJobId,
        List<GenericStack> inputTotal,
        List<GenericStack> outputTotal,
        List<GenericStack> remainingTotal
    ) {
        ECOBatchCraftingHelper.validateBatchSize(batchSize);
        return new ECOVerifiedFastPathExecution(recipe, batchSize, craftingJobId,
            inputTotal, outputTotal, remainingTotal);
    }

    public ECOVerifiedFastPathRecipe recipe() {
        return recipe;
    }

    public int batchSize() {
        return batchSize;
    }

    @Nullable
    public UUID craftingJobId() {
        return craftingJobId;
    }

    public List<GenericStack> inputTotal() { return inputTotal; }

    public List<GenericStack> outputTotal() { return outputTotal; }

    public List<GenericStack> remainingTotal() { return remainingTotal; }

    /** True while no recipe/datapack/server reload happened since the underlying verification. */
    public boolean isCurrent(long currentReloadGeneration) {
        return recipe.isCurrent(currentReloadGeneration);
    }
}
