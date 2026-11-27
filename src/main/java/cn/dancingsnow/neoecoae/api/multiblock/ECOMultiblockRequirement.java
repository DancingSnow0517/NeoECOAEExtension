package cn.dancingsnow.neoecoae.api.multiblock;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.block.state.BlockState;

/** One local structure slot: a default building state and every explicitly accepted state. */
public record ECOMultiblockRequirement(BlockState defaultState, List<BlockState> alternatives) {
    public ECOMultiblockRequirement {
        Objects.requireNonNull(defaultState, "defaultState");
        alternatives = List.copyOf(alternatives);
    }
    public static ECOMultiblockRequirement of(BlockState state, BlockState... alternatives) {
        return new ECOMultiblockRequirement(state, List.of(alternatives));
    }
    public ECOMultiblockRequirement transform(UnaryOperator<BlockState> transform) {
        return new ECOMultiblockRequirement(transform.apply(defaultState), alternatives.stream().map(transform).toList());
    }
    public boolean accepts(BlockState state) {
        if (sameStructureState(defaultState, state)) return true;
        return alternatives.stream().anyMatch(candidate -> sameStructureState(candidate, state));
    }
    private static boolean sameStructureState(BlockState expected, BlockState actual) {
        return expected.is(actual.getBlock()) && ECOMultiblockRequirements.matchingProperties(expected, actual);
    }
}
