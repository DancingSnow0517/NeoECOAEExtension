package cn.dancingsnow.neoecoae.compat.advanced_ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.IConfigManager;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails;

/** Optional AdvancedAE boundary for ECO's ordinary processing batch ramp. */
public final class ECOAdvancedAEPatternScaling {
    private static final boolean AVAILABLE = detect();

    private ECOAdvancedAEPatternScaling() {}

    public static boolean isProvider(Object provider) {
        return AVAILABLE && Types.isProvider(provider);
    }

    public static boolean isAdvancedPattern(Object pattern) {
        return AVAILABLE && Types.isAdvancedPattern(pattern);
    }

    public static boolean isBlocking(Object provider) {
        return Types.isBlocking(provider);
    }

    public static IConfigManager configManager(Object provider) {
        return Types.configManager(provider);
    }

    public static IPatternDetails scale(IPatternDetails pattern, long multiplier) {
        return Types.scale(pattern, multiplier);
    }

    private static boolean detect() {
        try {
            Class.forName("net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", false,
                    ECOAdvancedAEPatternScaling.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    private static final class Types {
        static boolean isProvider(Object provider) { return provider instanceof AdvPatternProviderLogic; }
        static boolean isAdvancedPattern(Object pattern) { return pattern instanceof AdvProcessingPattern; }
        static boolean isBlocking(Object provider) { return ((AdvPatternProviderLogic) provider).isBlocking(); }
        static IConfigManager configManager(Object provider) {
            return ((AdvPatternProviderLogic) provider).getConfigManager();
        }
        static IPatternDetails scale(IPatternDetails pattern, long multiplier) {
            return new ScaledAdvancedPattern((AdvProcessingPattern) pattern, multiplier);
        }
    }

    private static final class ScaledAdvancedPattern implements IPatternDetails, IAdvPatternDetails {
        private final AdvProcessingPattern original;
        private final long multiplier;

        private ScaledAdvancedPattern(AdvProcessingPattern original, long multiplier) {
            this.original = original;
            this.multiplier = multiplier;
        }

        @Override public AEItemKey getDefinition() { return original.getDefinition(); }
        @Override public IInput[] getInputs() {
            var inputs = original.getInputs();
            var scaled = new IInput[inputs.length];
            for (int i = 0; i < inputs.length; i++) scaled[i] = new ScaledInput(inputs[i], multiplier);
            return scaled;
        }
        @Override public List<GenericStack> getOutputs() {
            return ECOBatchCraftingHelper.multiply(original.getOutputs(), multiplier);
        }
        @Override public boolean supportsPushInputsToExternalInventory() { return true; }
        @Override public void pushInputsToExternalInventory(KeyCounter[] inputs, PatternInputSink sink) {
            for (var counter : inputs) for (var entry : counter) sink.pushInput(entry.getKey(), entry.getLongValue());
        }
        @Override public boolean directionalInputsSet() { return original.directionalInputsSet(); }
        @Override public HashMap<AEKey, Direction> getDirectionMap() { return original.getDirectionMap(); }
        @Override public Direction getDirectionSideForInputKey(AEKey key) {
            return original.getDirectionSideForInputKey(key);
        }
        @Override public boolean equals(Object other) {
            return other == original || other instanceof ScaledAdvancedPattern scaled
                    && original.equals(scaled.original) && multiplier == scaled.multiplier;
        }
        @Override public int hashCode() { return original.hashCode(); }
    }

    private record ScaledInput(IPatternDetails.IInput original, long multiplier) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return original.getPossibleInputs(); }
        @Override public long getMultiplier() { return Math.multiplyExact(original.getMultiplier(), multiplier); }
        @Override public boolean isValid(AEKey input, Level level) { return original.isValid(input, level); }
        @Override public AEKey getRemainingKey(AEKey template) { return null; }
    }
}
