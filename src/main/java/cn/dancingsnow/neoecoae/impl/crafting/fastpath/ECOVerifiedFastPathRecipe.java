package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Unforgeable proof that one cached {@link ECOFastPathResult} was value-verified against one concrete
 * {@link ECOExtractedPatternExecution}.
 *
 * <p>Invariants:
 * <ul>
 *   <li>It can only be produced by {@link ECOCraftingFastPathCache}, which is the single place that runs the
 *       three {@code List&lt;GenericStack&gt;.equals} comparisons (outputs, remainders, inputs). The
 *       constructor is private and the factory is package-private, so no caller outside this package can
 *       fabricate a "verified" object.</li>
 *   <li>It pins the exact execution instance it was verified for, so a later stage can assert identity
 *       ({@link #isVerifiedFor}) instead of re-comparing values.</li>
 *   <li>It pins the reload generation observed at verification time, so a recipe/datapack reload between
 *       verification and execution invalidates it ({@link #isCurrent}).</li>
 *   <li>It carries no worker capacity and no worker eligibility. It is purely recipe-level knowledge and is
 *       therefore safe to share across every worker of a crafting cluster or Network Switch group.</li>
 * </ul>
 */
public final class ECOVerifiedFastPathRecipe {
    private final ECOCraftingFastPathCache issuer;
    private final long issuerEpoch;
    private final ECOExtractedPatternExecution execution;
    private final ECOFastPathKey key;
    private final ECOFastPathResult result;
    private final long reloadGeneration;

    private ECOVerifiedFastPathRecipe(
        ECOCraftingFastPathCache issuer,
        ECOExtractedPatternExecution execution,
        ECOFastPathKey key,
        ECOFastPathResult result,
        long reloadGeneration
    ) {
        this.issuer = issuer;
        this.issuerEpoch = issuer.currentCredentialEpoch();
        this.execution = execution;
        this.key = key;
        this.result = result;
        this.reloadGeneration = reloadGeneration;
    }

    /**
     * Trusted construction. Only {@link ECOCraftingFastPathCache} may call this, and only after a complete
     * value verification of {@code result} against {@code execution}.
     */
    static ECOVerifiedFastPathRecipe trusted(
        ECOCraftingFastPathCache issuer,
        ECOExtractedPatternExecution execution,
        ECOFastPathKey key,
        ECOFastPathResult result,
        long reloadGeneration
    ) {
        return new ECOVerifiedFastPathRecipe(issuer, execution, key, result, reloadGeneration);
    }

    public ECOFastPathKey key() {
        return key;
    }

    public ECOFastPathResult result() {
        return result;
    }

    public long reloadGeneration() {
        return reloadGeneration;
    }

    public List<GenericStack> outputsPerCraft() {
        return result.outputEntries();
    }

    public List<GenericStack> remainingPerCraft() {
        return result.remainingEntries();
    }

    public List<GenericStack> inputsPerCraft() {
        return result.inputEntries();
    }

    /** True only for the very execution context this credential was verified against. */
    public boolean isVerifiedFor(ECOExtractedPatternExecution candidate) {
        return this.execution == candidate;
    }

    /** True only for the cache instance that performed the value verification and minted this credential. */
    public boolean isIssuedBy(ECOCraftingFastPathCache candidate) {
        return this.issuer == candidate && candidate.isCredentialEpochCurrent(issuerEpoch);
    }

    /** True while no recipe/datapack/server reload happened since verification. */
    public boolean isCurrent(long currentReloadGeneration) {
        return this.reloadGeneration == currentReloadGeneration;
    }

    /** Largest batch the per-craft amounts of the verified execution can still represent. */
    public int arithmeticBatchLimit() {
        return execution.arithmeticBatchLimit();
    }

    /**
     * Binds this recipe-level credential to one concrete batch size. The returned object is what the Pattern
     * Bus, Worker and Crafting Thread pass around instead of re-verifying stack lists.
     */
    @Nullable
    public ECOVerifiedFastPathExecution withBatch(int batchSize, @Nullable UUID craftingJobId) {
        if (batchSize <= 0 || batchSize > arithmeticBatchLimit()) {
            return null;
        }
        return ECOVerifiedFastPathExecution.trusted(this, batchSize, craftingJobId);
    }
}
