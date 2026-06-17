package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ECOCompiledFastPathPattern {
    private final long reloadGeneration;

    @Nullable private final Object patternIdentity;

    private final List<GenericStack> outputs;
    private final boolean knownSafePatternType;
    private final boolean cacheableFastPathInputs;
    private final boolean safeOutputs;

    private ECOCompiledFastPathPattern(
            long reloadGeneration,
            @Nullable Object patternIdentity,
            List<GenericStack> outputs,
            boolean knownSafePatternType,
            boolean cacheableFastPathInputs,
            boolean safeOutputs) {
        this.reloadGeneration = reloadGeneration;
        this.patternIdentity = patternIdentity;
        this.outputs = List.copyOf(outputs);
        this.knownSafePatternType = knownSafePatternType;
        this.cacheableFastPathInputs = cacheableFastPathInputs;
        this.safeOutputs = safeOutputs;
    }

    public static ECOCompiledFastPathPattern compile(IPatternDetails details) {
        long reloadGeneration = AE2PatternIntrospection.getReloadGeneration();
        boolean available = AE2PatternIntrospection.isAvailable();
        List<GenericStack> outputs = ECOFastPathStacks.copyStacks(details.getOutputs());
        Object patternIdentity = available ? AE2PatternIntrospection.getStablePatternIdentity(details) : null;
        boolean knownSafePatternType = available && AE2PatternIntrospection.isKnownSafePatternType(details);
        boolean cacheableFastPathInputs = available && AE2PatternIntrospection.hasStableFastPathInputs(details);
        boolean safeOutputs = outputs.size() == 1 && ECOFastPathStacks.isSafeForFastPath(outputs, false);
        return new ECOCompiledFastPathPattern(
                reloadGeneration, patternIdentity, outputs, knownSafePatternType, cacheableFastPathInputs, safeOutputs);
    }

    public boolean isCurrent() {
        return reloadGeneration == AE2PatternIntrospection.getReloadGeneration();
    }

    public List<GenericStack> outputs() {
        return outputs;
    }

    public boolean canBuildFastPath(List<GenericStack> containers) {
        return patternIdentity != null
                && knownSafePatternType
                && safeOutputs
                && ECOFastPathStacks.isSafeForFastPath(containers, false);
    }

    public boolean canCacheFastPathInputs() {
        return patternIdentity != null && cacheableFastPathInputs && safeOutputs;
    }

    public Optional<ECOFastPathKey> buildKey(KeyCounter[] craftingContainer, @Nullable Level level) {
        if (patternIdentity == null) {
            return Optional.empty();
        }
        return ECOFastPathKey.of(patternIdentity, craftingContainer, level, reloadGeneration);
    }
}
