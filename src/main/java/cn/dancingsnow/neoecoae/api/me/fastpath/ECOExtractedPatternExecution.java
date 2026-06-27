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
    private final KeyCounter expectedOutputsCounter;
    private final KeyCounter expectedContainerItemsCounter;
    private final Level level;
    private List<GenericStack> expectedOutputs;
    private List<GenericStack> expectedContainerItems;
    private List<GenericStack> inputItems;

    @Nullable
    private ECOFastPathKey key;

    @Nullable
    private Boolean fastPathEligible;

    private ECOExtractedPatternExecution(
        IPatternDetails details,
        KeyCounter[] craftingContainer,
        KeyCounter expectedOutputsCounter,
        KeyCounter expectedContainerItemsCounter,
        @Nullable Level level,
        List<GenericStack> expectedOutputs,
        List<GenericStack> expectedContainerItems,
        List<GenericStack> inputItems,
        @Nullable ECOFastPathKey key,
        @Nullable Boolean fastPathEligible
    ) {
        this.details = details;
        this.craftingContainer = craftingContainer;
        this.expectedOutputsCounter = expectedOutputsCounter;
        this.expectedContainerItemsCounter = expectedContainerItemsCounter;
        this.level = level;
        this.expectedOutputs = expectedOutputs;
        this.expectedContainerItems = expectedContainerItems;
        this.inputItems = inputItems;
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
        return new ECOExtractedPatternExecution(
            details,
            craftingContainer,
            expectedOutputs,
            expectedContainerItems,
            level,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static ECOExtractedPatternExecution slow(IPatternDetails details, KeyCounter[] craftingContainer) {
        return new ECOExtractedPatternExecution(
            details,
            craftingContainer,
            new KeyCounter(),
            new KeyCounter(),
            null,
            List.of(),
            List.of(),
            null,
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
        if (expectedOutputs == null) {
            expectedOutputs = ECOFastPathStacks.copyCounter(expectedOutputsCounter);
        }
        return expectedOutputs;
    }

    public List<GenericStack> expectedContainerItems() {
        if (expectedContainerItems == null) {
            expectedContainerItems = ECOFastPathStacks.copyCounter(expectedContainerItemsCounter);
        }
        return expectedContainerItems;
    }

    public List<GenericStack> inputItems() {
        if (inputItems == null) {
            inputItems = ECOFastPathStacks.copyCounters(craftingContainer);
        }
        return inputItems;
    }

    @Nullable
    public ECOFastPathKey key() {
        if (key == null && canAttemptFastPath()) {
            key = AE2PatternIntrospection.buildFastPathKey(details, craftingContainer, level).orElse(null);
        }
        return key;
    }

    public boolean fastPathEligible() {
        if (fastPathEligible == null) {
            fastPathEligible = computeFastPathEligible();
        }
        return fastPathEligible;
    }

    private boolean computeFastPathEligible() {
        return key() != null
            && canAttemptFastPath()
            && expectedOutputs().size() == 1
            && ECOFastPathStacks.isSafeForFastPath(expectedOutputs(), false)
            && ECOFastPathStacks.isSafeForFastPath(expectedContainerItems(), false)
            && ECOFastPathStacks.isSafeForFastPath(inputItems(), true);
    }

    private boolean canAttemptFastPath() {
        return NEConfig.ecoAe2FastPathEnabled
            && !NEConfig.postCraftingEvent
            && AE2PatternIntrospection.isAvailable()
            && AE2PatternIntrospection.isKnownSafePatternType(details);
    }

    @Nullable
    public IMolecularAssemblerSupportedPattern molecularPattern() {
        if (details instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            return supportedPattern;
        }
        return null;
    }
}
