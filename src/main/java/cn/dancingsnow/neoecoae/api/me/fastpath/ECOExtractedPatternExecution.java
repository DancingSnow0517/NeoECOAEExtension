package cn.dancingsnow.neoecoae.api.me.fastpath;

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
            && ECOFastPathStacks.isSafeForFastPath(outputs, false)
            && ECOFastPathStacks.isSafeForFastPath(containers, false)
            && ECOFastPathStacks.isSafeForFastPath(inputs, true);
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
