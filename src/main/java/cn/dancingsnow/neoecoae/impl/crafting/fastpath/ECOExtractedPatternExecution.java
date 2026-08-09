package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
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

    @Nullable private final ECOFastPathKey key;

    private final boolean fastPathEligible;

    private ECOExtractedPatternExecution(
            IPatternDetails details,
            KeyCounter[] craftingContainer,
            List<GenericStack> expectedOutputs,
            List<GenericStack> expectedContainerItems,
            List<GenericStack> inputItems,
            @Nullable ECOFastPathKey key,
            boolean fastPathEligible) {
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
            ECOCompiledFastPathPattern compiledPattern,
            @Nullable ECOFastPathPatternMetadata metadata,
            KeyCounter[] craftingContainer,
            List<GenericStack> expectedContainerItems,
            boolean canBuildFastPath,
            Level level) {
        List<GenericStack> outputs = compiledPattern.outputs();
        List<GenericStack> inputs = List.of();
        @Nullable ECOFastPathKey key = null;
        boolean eligible = false;
        if (canBuildFastPath) {
            if (metadata != null && metadata.isCurrent(compiledPattern, level)) {
                inputs = metadata.inputItems();
                key = metadata.key();
                eligible = metadata.fastPathEligible();
            } else {
                inputs = ECOFastPathStacks.copyCounters(craftingContainer);
                Optional<ECOFastPathKey> builtKey = compiledPattern.buildKey(craftingContainer, level);
                key = builtKey.orElse(null);
                eligible = builtKey.isPresent() && ECOFastPathStacks.isSafeForFastPath(inputs, true);
            }
        }
        return new ECOExtractedPatternExecution(
                details, craftingContainer, outputs, expectedContainerItems, inputs, key, eligible);
    }

    /** Builds FastPath metadata from the concrete inputs and outputs extracted by an external AE2 CPU. */
    public static ECOExtractedPatternExecution create(
            IPatternDetails details,
            KeyCounter[] craftingContainer,
            KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems,
            Level level) {
        List<GenericStack> outputs = ECOFastPathStacks.copyCounter(expectedOutputs);
        List<GenericStack> containers = ECOFastPathStacks.copyCounter(expectedContainerItems);
        if (AE2PatternIntrospection.classifyPatternEligibility(details)
                != AE2PatternIntrospection.PatternEligibility.ELIGIBLE) {
            return new ECOExtractedPatternExecution(
                    details, craftingContainer, outputs, containers, List.of(), null, false);
        }

        List<GenericStack> inputs = ECOFastPathStacks.copyCounters(craftingContainer);
        Optional<ECOFastPathKey> key = AE2PatternIntrospection.buildFastPathKey(details, craftingContainer, level);
        boolean eligible = key.isPresent() && isConcreteExecutionSafe(outputs, containers, inputs);
        return new ECOExtractedPatternExecution(
                details, craftingContainer, outputs, containers, inputs, key.orElse(null), eligible);
    }

    static boolean isConcreteExecutionSafe(
            List<GenericStack> outputs, List<GenericStack> containers, List<GenericStack> inputs) {
        ECOReusableCraftingPlan plan = ECOReusableCraftingPlan.of(inputs, containers);
        return isConcreteExecutionPolicySafe(
                outputs.size(),
                ECOFastPathStacks.isSafeForFastPath(outputs, false),
                ECOFastPathStacks.isSafeForFastPath(plan.ordinaryRemainingPerCraft(), false),
                ECOFastPathStacks.isSafeForFastPath(plan.consumedInputsPerCraft(), true),
                ECOFastPathStacks.isSafeReusableCatalysts(plan.reusableInputs()));
    }

    static boolean isConcreteExecutionPolicySafe(
            int outputCount,
            boolean outputsSafe,
            boolean ordinaryRemainingSafe,
            boolean consumedInputsSafe,
            boolean reusableCatalystsSafe) {
        return outputCount == 1 && outputsSafe && ordinaryRemainingSafe && consumedInputsSafe && reusableCatalystsSafe;
    }

    public static ECOExtractedPatternExecution slow(IPatternDetails details, KeyCounter[] craftingContainer) {
        return new ECOExtractedPatternExecution(
                details,
                craftingContainer,
                List.of(),
                List.of(),
                ECOFastPathStacks.copyCounters(craftingContainer),
                null,
                false);
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

    @Nullable public ECOFastPathKey key() {
        return key;
    }

    public boolean fastPathEligible() {
        return fastPathEligible;
    }

    @Nullable public IMolecularAssemblerSupportedPattern molecularPattern() {
        if (details instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            return supportedPattern;
        }
        return null;
    }
}
