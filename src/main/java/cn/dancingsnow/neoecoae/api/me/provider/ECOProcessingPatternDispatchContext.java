package cn.dancingsnow.neoecoae.api.me.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.util.Objects;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Immutable context reserved for the future processing-pattern dispatch contract. */
public record ECOProcessingPatternDispatchContext(
        IPatternDetails pattern,
        KeyCounter[] inputs,
        KeyCounter outputs,
        KeyCounter remainders,
        Level level,
        @Nullable java.util.UUID craftingJobId) {
    public ECOProcessingPatternDispatchContext {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(remainders, "remainders");
        Objects.requireNonNull(level, "level");
        inputs = java.util.Arrays.stream(inputs)
                .map(Objects::requireNonNull)
                .toArray(KeyCounter[]::new);
    }
}
