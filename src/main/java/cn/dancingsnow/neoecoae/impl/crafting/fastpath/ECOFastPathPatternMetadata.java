package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathPatternMetadata {
    private final ECOCompiledFastPathPattern compiledPattern;

    @Nullable private final ResourceLocation dimension;

    private final List<GenericStack> inputItems;

    @Nullable private final ECOFastPathKey key;

    private final boolean fastPathEligible;

    private ECOFastPathPatternMetadata(
            ECOCompiledFastPathPattern compiledPattern,
            @Nullable ResourceLocation dimension,
            List<GenericStack> inputItems,
            @Nullable ECOFastPathKey key,
            boolean fastPathEligible) {
        this.compiledPattern = compiledPattern;
        this.dimension = dimension;
        this.inputItems = List.copyOf(inputItems);
        this.key = key;
        this.fastPathEligible = fastPathEligible;
    }

    public static ECOFastPathPatternMetadata create(
            ECOCompiledFastPathPattern compiledPattern, KeyCounter[] craftingContainer, Level level) {
        List<GenericStack> inputItems = ECOFastPathStacks.copyCounters(craftingContainer);
        Optional<ECOFastPathKey> key = compiledPattern.buildKey(craftingContainer, level);
        boolean fastPathEligible = key.isPresent() && ECOFastPathStacks.isSafeForFastPath(inputItems, true);
        return new ECOFastPathPatternMetadata(
                compiledPattern, ECOFastPathKey.dimension(level), inputItems, key.orElse(null), fastPathEligible);
    }

    public boolean isCurrent(ECOCompiledFastPathPattern compiledPattern, Level level) {
        return this.compiledPattern == compiledPattern
                && compiledPattern.isCurrent()
                && Objects.equals(dimension, ECOFastPathKey.dimension(level));
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
}
