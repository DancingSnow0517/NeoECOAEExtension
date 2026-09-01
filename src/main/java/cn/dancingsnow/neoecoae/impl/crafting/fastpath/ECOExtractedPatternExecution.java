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
    private final ECORecipeClassifier.Classification classification;

    @Nullable
    private final ECOFastPathKey key;

    @Nullable
    private final String fastPathRejectionReason;
    private final long arithmeticBatchLimit;

    private ECOExtractedPatternExecution(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        List<GenericStack> expectedOutputs,
        List<GenericStack> expectedContainerItems,
        List<GenericStack> inputItems,
        ECORecipeClassifier.Classification classification,
        @Nullable ECOFastPathKey key,
        @Nullable String fastPathRejectionReason
    ) {
        this.details = details;
        this.craftingContainer = craftingContainer;
        // copyCounter/copyCounters already return unmodifiable lists, so List.copyOf is a no-op for them and
        // only guards the List.of() literals used by the slow-path factory.
        this.expectedOutputs = List.copyOf(expectedOutputs);
        this.expectedContainerItems = List.copyOf(expectedContainerItems);
        this.inputItems = List.copyOf(inputItems);
        this.classification = classification;
        this.key = key;
        this.fastPathRejectionReason = fastPathRejectionReason;
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
        ECORecipeClassifier.Classification classification = ECORecipeClassifier.classify(details);
        Optional<ECOFastPathKey> key = AE2PatternIntrospection.buildFastPathKey(details, craftingContainer, level);
        ECOFastPathStacks.ItemStackValidation resultValidation = classification.type() == ECORecipeClassifier.Type.NORMAL
            ? ECOFastPathStacks.ItemStackValidation.FAST_PATH
            : ECOFastPathStacks.ItemStackValidation.FAST_PATH_MUTATION;
        String rejectionReason = findFastPathRejectionReason(
            details, key, outputs, containers, inputs, resultValidation);
        return new ECOExtractedPatternExecution(
            details, craftingContainer, outputs, containers, inputs, classification, key.orElse(null), rejectionReason
        );
    }

    public static ECOExtractedPatternExecution slow(IPatternDetails details, KeyCounter[] craftingContainer) {
        return new ECOExtractedPatternExecution(
            details,
            craftingContainer,
            List.of(),
            List.of(),
            ECOFastPathStacks.copyCounters(craftingContainer),
            ECORecipeClassifier.classify(details),
            null,
            "SLOW_EXECUTION_CONTEXT"
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
            null, new KeyCounter[0], expectedOutputs, expectedContainerItems, inputItems,
            new ECORecipeClassifier.Classification(ECORecipeClassifier.Type.NORMAL, true, "TEST_NORMALIZED"),
            key, key == null ? "KEY_BUILD_FAILED" : null
        );
    }

    @Nullable
    private static String findFastPathRejectionReason(
        IPatternDetails details,
        Optional<ECOFastPathKey> key,
        List<GenericStack> outputs,
        List<GenericStack> containers,
        List<GenericStack> inputs,
        ECOFastPathStacks.ItemStackValidation resultValidation
    ) {
        if (key.isEmpty()) return "KEY_BUILD_FAILED";
        if (!NEConfig.ecoAe2FastPathEnabled) return "FAST_PATH_DISABLED";
        if (NEConfig.postCraftingEvent) return "POST_CRAFTING_EVENT_ENABLED";
        if (!AE2PatternIntrospection.isAvailable()) return "AE2_INTROSPECTION_UNAVAILABLE";
        if (!AE2PatternIntrospection.isKnownSafePatternType(details)) return "UNSAFE_PATTERN_TYPE";
        if (outputs.size() != 1) return "OUTPUT_COUNT_" + outputs.size();

        String outputFailure = validationFailure(
            "OUTPUT", outputs, true, resultValidation);
        if (outputFailure != null) return outputFailure;
        String remainderFailure = validationFailure(
            "REMAINDER", containers, false, resultValidation);
        if (remainderFailure != null) return remainderFailure;
        return validationFailure(
            "INPUT", inputs, false, ECOFastPathStacks.ItemStackValidation.FAST_PATH_INPUT);
    }

    @Nullable
    private static String validationFailure(
        String role,
        List<GenericStack> stacks,
        boolean requireNonEmpty,
        ECOFastPathStacks.ItemStackValidation validation
    ) {
        ECOFastPathStacks.ItemStackValidationFailure failure = ECOFastPathStacks.validateItemStacks(
            stacks, Integer.MAX_VALUE, requireNonEmpty, validation);
        return failure == ECOFastPathStacks.ItemStackValidationFailure.NONE
            ? null
            : role + "_" + failure.name();
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

    public ECORecipeClassifier.Classification classification() {
        return classification;
    }

    public ECORecipeClassifier.Type fastPathType() {
        return classification.type();
    }

    public String fastPathReason() {
        if (!NEConfig.ecoAe2FastPathEnabled) return "FAST_PATH_DISABLED";
        if (NEConfig.postCraftingEvent) return "POST_CRAFTING_EVENT_ENABLED";
        return fastPathRejectionReason == null ? classification.reason() : fastPathRejectionReason;
    }

    @Nullable
    public ECOFastPathKey key() {
        return key;
    }

    public boolean fastPathEligible() {
        return fastPathRejectionReason == null;
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
            && fastPathRejectionReason == null
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
