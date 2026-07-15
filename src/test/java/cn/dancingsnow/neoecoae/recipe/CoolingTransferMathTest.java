package cn.dancingsnow.neoecoae.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CoolingTransferMathTest {
    @Test
    void limitsInputToRequestedCoolantDeficit() {
        assertEquals(20, CoolingTransferMath.inputForDeficit(1000, 100, 5000));
        assertEquals(1, CoolingTransferMath.inputForDeficit(1, 100, 1500));
        assertEquals(0, CoolingTransferMath.inputForDeficit(0, 100, 1500));
    }

    @Test
    void scalesWasteAndCoolantWithoutOverflowingInt() {
        assertEquals(250, CoolingTransferMath.scaleAmount(50, 500, 100));
        assertEquals(Integer.MAX_VALUE, CoolingTransferMath.scaleAmount(Integer.MAX_VALUE, Integer.MAX_VALUE, 1));
    }

    @Test
    void rejectsInvalidRecipeRatios() {
        assertThrows(IllegalArgumentException.class, () -> CoolingTransferMath.inputForDeficit(1, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> CoolingTransferMath.scaleAmount(1, 1, 0));
    }
}
