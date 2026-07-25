package cn.dancingsnow.neoecoae.blocks.entity.computation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NEComputationUpgradeRulesTest {
    @Test
    void fieldGeneratorMultipliersUseExactStackSize() {
        assertEquals(2, multiplier("iv_field_generator", 16));
        assertEquals(4, multiplier("luv_field_generator", 16));
        assertEquals(8, multiplier("zpm_field_generator", 16));
        assertEquals(16, multiplier("uv_field_generator", 16));
        assertEquals(1, multiplier("uv_field_generator", 15));
        assertEquals(1, multiplier("uv_field_generator", 17));
        assertEquals(
                1,
                NEComputationUpgradeRules.fieldGeneratorMultiplier(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "uv_field_generator"), 16));
    }

    @Test
    void acceleratorLimitLeavesRoomForTheCpuCoProcessorIncrement() {
        assertEquals(Integer.MAX_VALUE - 1, NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS);
    }

    @Test
    void acceleratorMaximumLeavesRoomForCpuCoProcessorAddition() {
        assertEquals(Integer.MAX_VALUE - 1, NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS);
    }

    private static int multiplier(String path, int count) {
        return NEComputationUpgradeRules.fieldGeneratorMultiplier(
                ResourceLocation.fromNamespaceAndPath("gtceu", path), count);
    }
}
