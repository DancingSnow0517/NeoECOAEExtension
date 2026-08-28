package cn.dancingsnow.neoecoae.multiblock.calculator;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NEClusterCalculatorTest {
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos ABOVE = CENTER.above();
    private static final BlockPos BELOW = CENTER.below();

    @Test
    void acceptsExactlyOneController() {
        Map<BlockPos, String> controllers = Map.of(CENTER, "controller");

        assertEquals(
            "controller",
            NEClusterCalculator.findUnique(List.of(BELOW, CENTER, ABOVE), controllers::get).orElseThrow()
        );
    }

    @Test
    void rejectsControllerAboveThePrimaryController() {
        Map<BlockPos, String> controllers = Map.of(CENTER, "center", ABOVE, "above");

        assertTrue(NEClusterCalculator.findUnique(List.of(BELOW, CENTER, ABOVE), controllers::get).isEmpty());
    }

    @Test
    void rejectsControllerBelowThePrimaryController() {
        Map<BlockPos, String> controllers = Map.of(CENTER, "center", BELOW, "below");

        assertTrue(NEClusterCalculator.findUnique(List.of(BELOW, CENTER, ABOVE), controllers::get).isEmpty());
    }

    @Test
    void rejectsMissingController() {
        assertTrue(NEClusterCalculator.findUnique(List.of(BELOW, CENTER, ABOVE), ignored -> null).isEmpty());
    }
}
