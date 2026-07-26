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
        this.expectedOutputs = List.copyOf(expectedOutputs);
        this.expectedContainerItems = List.copyOf(expectedContainerItems);
        this.inputItems = List.copyOf(inputItems);
        this.key = key;
        this.fastPathEligible = fastPathEligible;
    }

    public static ECOExtractedPatternExecution create(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems,
        Level level,
        boolean ecoPatternBusPresent
    ) {
        if (!shouldAttemptFastPathMetadata(
            ecoPatternBusPresent,
            NEConfig.ecoAe2FastPathEnabled,
            NEConfig.postCraftingEvent,
            AE2PatternIntrospection.isAvailable(),
            AE2PatternIntrospection.isKnownSafePatternType(details)
        )) {
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
                false
            );
        }
        List<GenericStack> outputs = ECOFastPathStacks.copySorted(expectedOutputs);
        List<GenericStack> containers = ECOFastPathStacks.copySorted(expectedContainerItems);
        List<GenericStack> inputs = ECOFastPathStacks.copyCounters(craftingContainer);
        Optional<ECOFastPathKey> key = AE2PatternIntrospection.buildFastPathKey(details, craftingContainer, level);
        boolean eligible = key.isPresent()
            && outputs.size() == 1
            && ECOFastPathStacks.isSafeForFastPath(outputs, false)
            && ECOFastPathStacks.isSafeForFastPath(containers, false)
            && ECOFastPathStacks.isSafeForFastPath(inputs, true);
        return new ECOExtractedPatternExecution(
            details, craftingContainer, outputs, containers, inputs, key.orElse(null), eligible
        );
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
        return ecoPatternBusPresent
            && fastPathEnabled
            && !postCraftingEvent
            && introspectionAvailable
            && knownSafePatternType;
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
            false
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
    public IMolecularAssemblerSupportedPattern molecularPattern() {
        if (details instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            return supportedPattern;
        }
        return null;
    }
}
