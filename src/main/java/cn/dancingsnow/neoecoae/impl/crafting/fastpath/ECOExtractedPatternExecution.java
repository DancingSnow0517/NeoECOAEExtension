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

public final class ECOExtractedPatternExecution {
    private final IPatternDetails details;
    private final KeyCounter[] craftingContainer;
    private final List<GenericStack> expectedOutputs;
    private final List<GenericStack> expectedContainerItems;
    private final List<GenericStack> inputItems;

    @Nullable
    private final ECOFastPathKey key;

    private final boolean fastPathEligible;

    @Nullable
    private final ECOFastPathFallbackReason fallbackReason;

    private ECOExtractedPatternExecution(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        List<GenericStack> expectedOutputs,
        List<GenericStack> expectedContainerItems,
        List<GenericStack> inputItems,
        @Nullable ECOFastPathKey key,
        boolean fastPathEligible,
        @Nullable ECOFastPathFallbackReason fallbackReason
    ) {
        this.details = details;
        this.craftingContainer = craftingContainer;
        this.expectedOutputs = List.copyOf(expectedOutputs);
        this.expectedContainerItems = List.copyOf(expectedContainerItems);
        this.inputItems = List.copyOf(inputItems);
        this.key = key;
        this.fastPathEligible = fastPathEligible;
        this.fallbackReason = fallbackReason;
    }

    public static ECOExtractedPatternExecution create(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems,
        Level level,
        boolean ecoPatternBusPresent
    ) {
        ECOFastPathFallbackReason metadataRejection = metadataRejectionReason(
            ecoPatternBusPresent,
            NEConfig.ecoAe2FastPathEnabled,
            NEConfig.postCraftingEvent,
            AE2PatternIntrospection.isAvailable(),
            AE2PatternIntrospection.isKnownSafePatternType(details)
        );
        if (metadataRejection != null) {
            // Non-FastPath execution keeps only the unsorted output and container-item snapshots
            // required for normal crafting accounting (waitingFor bookkeeping); no canonical
            // input snapshot, no ECOFastPathKey, no sorting or key hashing.
            return new ECOExtractedPatternExecution(
                details,
                craftingContainer,
                ECOFastPathStacks.toGenericStacks(expectedOutputs),
                ECOFastPathStacks.toGenericStacks(expectedContainerItems),
                List.of(),
                null,
                false,
                metadataRejection
            );
        }
        FastPathEligibility patternEligibility = mapEligibility(
            AE2PatternIntrospection.classifyPatternEligibility(details)
        );
        if (patternEligibility != FastPathEligibility.ELIGIBLE) {
            return new ECOExtractedPatternExecution(
                details,
                craftingContainer,
                ECOFastPathStacks.toGenericStacks(expectedOutputs),
                ECOFastPathStacks.toGenericStacks(expectedContainerItems),
                List.of(),
                null,
                false,
                fallbackReasonFor(patternEligibility)
            );
        }
        List<GenericStack> outputs = ECOFastPathStacks.copySorted(expectedOutputs);
        List<GenericStack> containers = ECOFastPathStacks.copySorted(expectedContainerItems);
        List<GenericStack> inputs = ECOFastPathStacks.copyCounters(craftingContainer);
        Optional<ECOFastPathKey> key = AE2PatternIntrospection.buildFastPathKey(details, craftingContainer, level);
        ECOFastPathFallbackReason fallbackReason = eligibilityRejectionReason(key, outputs, containers, inputs);
        boolean eligible = fallbackReason == null;
        return new ECOExtractedPatternExecution(
            details, craftingContainer, outputs, containers, inputs, key.orElse(null), eligible, fallbackReason
        );
    }

    /** Compatibility entry point used by Thunderbolt Core's optional NeoECO bridge. */
    public static ECOExtractedPatternExecution create(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems,
        Level level
    ) {
        return create(details, craftingContainer, expectedOutputs, expectedContainerItems, level, true);
    }

    /**
     * Cheap O(1) eligibility gate evaluated before any FastPath metadata (snapshots, canonical
     * sorting, key construction, hashing) is built. Patterns that can never use the FastPath -
     * for example third-party dynamic patterns or patterns whose providers contain no ECO
     * pattern bus - must not pay the metadata construction cost.
     */
    static boolean shouldAttemptFastPathMetadata(
        boolean ecoPatternBusPresent,
        boolean fastPathEnabled,
        boolean postCraftingEvent,
        boolean introspectionAvailable,
        boolean knownSafePatternType
    ) {
        return metadataRejectionReason(
            ecoPatternBusPresent,
            fastPathEnabled,
            postCraftingEvent,
            introspectionAvailable,
            knownSafePatternType
        ) == null;
    }

    @Nullable
    private static ECOFastPathFallbackReason metadataRejectionReason(
        boolean ecoPatternBusPresent,
        boolean fastPathEnabled,
        boolean postCraftingEvent,
        boolean introspectionAvailable,
        boolean knownSafePatternType
    ) {
        if (!fastPathEnabled) {
            return ECOFastPathFallbackReason.FAST_PATH_DISABLED;
        }
        if (postCraftingEvent) {
            return ECOFastPathFallbackReason.POST_CRAFTING_EVENT;
        }
        if (!ecoPatternBusPresent) {
            return ECOFastPathFallbackReason.NO_ECO_PATTERN_BUS;
        }
        if (!introspectionAvailable) {
            return ECOFastPathFallbackReason.INTROSPECTION_UNAVAILABLE;
        }
        if (!knownSafePatternType) {
            return ECOFastPathFallbackReason.UNSUPPORTED_PATTERN_TYPE;
        }
        return null;
    }

    @Nullable
    private static ECOFastPathFallbackReason eligibilityRejectionReason(
        Optional<ECOFastPathKey> key,
        List<GenericStack> outputs,
        List<GenericStack> containers,
        List<GenericStack> inputs
    ) {
        if (key.isEmpty()) {
            return ECOFastPathFallbackReason.KEY_BUILD_FAILED;
        }
        if (outputs.size() != 1) {
            return ECOFastPathFallbackReason.OUTPUT_COUNT_NOT_ONE;
        }
        if (!ECOFastPathStacks.isSafeForFastPath(outputs, false)) {
            return ECOFastPathFallbackReason.UNSAFE_EXPECTED_OUTPUT;
        }
        ECOReusableCraftingPlan plan = ECOReusableCraftingPlan.of(inputs, containers);
        // A component-only transition (for example ExtendedAE's staged Basic Core) is
        // deterministic for a concrete AE2 pattern: every craft consumes the exact input key
        // and produces the exact output key captured above.  FastPath batches those keys as
        // separate inventory entries, so differing components do not make the operation unsafe.
        // Mutable/damageable items remain excluded by isSafeForFastPath below.
        if (!ECOFastPathStacks.isSafeForFastPath(plan.ordinaryRemainingPerCraft(), false)) {
            return ECOFastPathFallbackReason.UNSAFE_CONTAINER_ITEM;
        }
        if (!ECOFastPathStacks.isSafeForFastPath(plan.consumedInputsPerCraft(), true)) {
            return ECOFastPathFallbackReason.UNSAFE_INPUT;
        }
        if (!ECOFastPathStacks.isSafeReusableCatalysts(plan.reusableInputs())) {
            return ECOFastPathFallbackReason.UNSAFE_INPUT;
        }
        return null;
    }

    private static FastPathEligibility mapEligibility(
        AE2PatternIntrospection.PatternEligibility eligibility
    ) {
        return switch (eligibility) {
            case ELIGIBLE -> FastPathEligibility.ELIGIBLE;
            case UNSUPPORTED_PATTERN_TYPE -> FastPathEligibility.UNSUPPORTED_PATTERN_TYPE;
            case RECIPE_UNAVAILABLE -> FastPathEligibility.RECIPE_UNAVAILABLE;
            case SPECIAL_RECIPE -> FastPathEligibility.SPECIAL_RECIPE;
        };
    }

    private static ECOFastPathFallbackReason fallbackReasonFor(FastPathEligibility eligibility) {
        return switch (eligibility) {
            case SPECIAL_RECIPE -> ECOFastPathFallbackReason.DYNAMIC_SPECIAL;
            case RECIPE_UNAVAILABLE -> ECOFastPathFallbackReason.INTROSPECTION_UNAVAILABLE;
            case UNSUPPORTED_PATTERN_TYPE -> ECOFastPathFallbackReason.UNSUPPORTED_PATTERN_TYPE;
            case ELIGIBLE -> null;
        };
    }

    public enum FastPathEligibility {
        ELIGIBLE,
        UNSUPPORTED_PATTERN_TYPE,
        RECIPE_UNAVAILABLE,
        SPECIAL_RECIPE
    }

    public static ECOExtractedPatternExecution slow(IPatternDetails details, KeyCounter[] craftingContainer) {
        // Executions without a FastPath key never read inputItems(): every consumer
        // (batch offers, cache verification, result matching) is gated on key() != null.
        return new ECOExtractedPatternExecution(
            details,
            craftingContainer,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            ECOFastPathFallbackReason.LEGACY_SLOW_EXECUTION
        );
    }

    public IPatternDetails details() {
        return details;
    }

    public KeyCounter[] craftingContainer() {
        return craftingContainer;
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

    @Nullable
    public ECOFastPathFallbackReason fallbackReason() {
        return fallbackReason;
    }

    @Nullable
    public IMolecularAssemblerSupportedPattern molecularPattern() {
        if (details instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            return supportedPattern;
        }
        return null;
    }
}
