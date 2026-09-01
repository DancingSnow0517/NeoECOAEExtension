package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable execution context for exactly one logical pattern dispatch.
 *
 * <p>Everything derived from the extracted crafting container is normalized once here: the fast-path key
 * (including its per-slot {@code EntrySignature} sorting), the expected outputs, the expected container
 * items (remainders), the aggregated concrete inputs, the fast-path eligibility decision and the arithmetic
 * batch ceiling implied by the per-craft amounts. Every later stage of the same dispatch - offer search,
 * verification, batch push - reuses this one object instead of rebuilding the same data.
 *
 * <p>The three expected-stack lists are unmodifiable and were validated once in {@link #create}; callers
 * must never re-copy or re-validate them. {@link #craftingContainer()} is the single {@code KeyCounter[]}
 * the CPU still owns for this dispatch (it is what {@code fillCraftingGrid} reads and what a rollback
 * reinjects), so it is intentionally shared by reference and must stay on the server thread for the
 * duration of the dispatch.
 */
public final class ECOExtractedPatternExecution {
    private final IPatternDetails details;
    private final KeyCounter[] craftingContainer;
    private final List<GenericStack> expectedOutputs;
    private final List<GenericStack> expectedContainerItems;
    private final List<GenericStack> inputItems;

    @Nullable
    private final ECOFastPathKey key;

    private final boolean fastPathEligible;
    private final long arithmeticBatchLimit;

    private ECOExtractedPatternExecution(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        List<GenericStack> expectedOutputs,
        List<GenericStack> expectedContainerItems,
        List<GenericStack> inputItems,
        @Nullable ECOFastPathKey key,
        boolean fastPathEligible
    ) {
        this.details = details;
        this.craftingContainer = craftingContainer;
        // copyCounter/copyCounters already return unmodifiable lists, so List.copyOf is a no-op for them and
        // only guards the List.of() literals used by the slow-path factory.
        this.expectedOutputs = List.copyOf(expectedOutputs);
        this.expectedContainerItems = List.copyOf(expectedContainerItems);
        this.inputItems = List.copyOf(inputItems);
        this.key = key;
        this.fastPathEligible = fastPathEligible;
        this.arithmeticBatchLimit = ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(
            this.inputItems, this.expectedOutputs, this.expectedContainerItems
        );
    }

    public static ECOExtractedPatternExecution create(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems,
        Level level
    ) {
        List<GenericStack> outputs = ECOFastPathStacks.copyCounter(expectedOutputs);
        List<GenericStack> containers = ECOFastPathStacks.copyCounter(expectedContainerItems);
        List<GenericStack> inputs = ECOFastPathStacks.copyCounters(craftingContainer);
        Optional<ECOFastPathKey> key = AE2PatternIntrospection.buildFastPathKey(details, craftingContainer, level);
        boolean eligible = key.isPresent()
            && NEConfig.ecoAe2FastPathEnabled
            && !NEConfig.postCraftingEvent
            && AE2PatternIntrospection.isAvailable()
            && AE2PatternIntrospection.isKnownSafePatternType(details)
            && outputs.size() == 1
            && ECOFastPathStacks.areValidItemStacks(
                outputs, Integer.MAX_VALUE, true, ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            && ECOFastPathStacks.areValidItemStacks(
                containers, Integer.MAX_VALUE, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH)
            && ECOFastPathStacks.areValidItemStacks(
                inputs, Integer.MAX_VALUE, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT);
        return new ECOExtractedPatternExecution(
            details, craftingContainer, outputs, containers, inputs, key.orElse(null), eligible
        );
    }

    public static ECOExtractedPatternExecution slow(IPatternDetails details, KeyCounter[] craftingContainer) {
        return new ECOExtractedPatternExecution(
            details,
            craftingContainer,
            List.of(),
            List.of(),
            ECOFastPathStacks.copyCounters(craftingContainer),
            null,
            false
        );
    }

    /**
     * Package-private test seam: builds a context from already-normalized lists and an already-built key,
     * bypassing AE2 pattern introspection and item-registry-dependent validation. Production code must always
     * go through {@link #create} or {@link #slow}.
     */
    static ECOExtractedPatternExecution ofNormalizedComponents(
        @Nullable ECOFastPathKey key,
        List<GenericStack> expectedOutputs,
        List<GenericStack> expectedContainerItems,
        List<GenericStack> inputItems
    ) {
        return new ECOExtractedPatternExecution(
            null, new KeyCounter[0], expectedOutputs, expectedContainerItems, inputItems, key, key != null
        );
    }

    public KeyCounter[] craftingContainer() {
        return craftingContainer;
    }

    public IPatternDetails details() {
        return details;
    }

    public List<GenericStack> expectedOutputs() {
        return expectedOutputs;
    }

    public List<GenericStack> expectedContainerItems() {
        return expectedContainerItems;
    }

    public List<GenericStack> inputItems() {
        return inputItems;
    }

    @Nullable
    public ECOFastPathKey key() {
        return key;
    }

    public boolean fastPathEligible() {
        return fastPathEligible;
    }

    /**
     * Largest batch multiplier the per-craft amounts of this dispatch can still represent. Computed once with
     * the rest of the context, because it depends only on the already-normalized per-craft lists.
     */
    public long arithmeticBatchLimit() {
        return arithmeticBatchLimit;
    }

    public boolean canUseFastPath() {
        return key != null
            && fastPathEligible
            && NEConfig.ecoAe2FastPathEnabled
            && !NEConfig.postCraftingEvent;
    }

    @Nullable
    public IMolecularAssemblerSupportedPattern molecularPattern() {
        if (details instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            return supportedPattern;
        }
        return null;
    }
}
