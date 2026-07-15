package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dancingsnow.neoecoae.compat.ae2.IWSUpgradeCompat;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class IntegratedWorkingStationSpeedTest {
    @Test
    void preservesAe2SpeedCurve() {
        assertEquals(2, IntegratedWorkingStationSpeed.calculate(0, List.of()));
        assertEquals(3, IntegratedWorkingStationSpeed.calculate(1, List.of()));
        assertEquals(5, IntegratedWorkingStationSpeed.calculate(2, List.of()));
        assertEquals(10, IntegratedWorkingStationSpeed.calculate(3, List.of()));
        assertEquals(50, IntegratedWorkingStationSpeed.calculate(4, List.of()));
    }

    @Test
    void combinesAddonCardsAndCapsAtOneRecipePerTick() {
        assertEquals(4, IntegratedWorkingStationSpeed.calculate(0, List.of(2)));
        assertEquals(80, IntegratedWorkingStationSpeed.calculate(3, List.of(2, 4)));
        assertEquals(200, IntegratedWorkingStationSpeed.calculate(4, List.of(16, 16, 16, 16)));
        assertEquals(200, IntegratedWorkingStationSpeed.calculate(4, List.of(Integer.MAX_VALUE)));
    }

    @Test
    void readsSupportedEaepMultipliersAndFallsBackSafely() {
        CompoundTag tag = new CompoundTag();
        assertEquals(2, IWSUpgradeCompat.readEaepMultiplierTag(tag));

        for (int multiplier : new int[] {2, 4, 8, 16}) {
            tag.putInt("EAS:mult", multiplier);
            assertEquals(multiplier, IWSUpgradeCompat.readEaepMultiplierTag(tag));
        }

        for (int invalid : new int[] {-1, 0, 3, 32, Integer.MAX_VALUE}) {
            tag.putInt("EAS:mult", invalid);
            assertEquals(2, IWSUpgradeCompat.readEaepMultiplierTag(tag));
        }
    }

    @Test
    void progressEnergyAccountingAlwaysTotalsToRecipeEnergy() {
        for (int speed : new int[] {2, 3, 5, 10, 50, 80, 200}) {
            int progress = 0;
            int consumed = 0;
            while (progress < IntegratedWorkingStationSpeed.MAX_PROGRESS_PER_TICK) {
                int advance = Math.min(speed, IntegratedWorkingStationSpeed.MAX_PROGRESS_PER_TICK - progress);
                consumed += IntegratedWorkingStationSpeed.energyForProgress(12347, progress, advance);
                progress += advance;
            }
            assertEquals(12347, consumed, "speed=" + speed);
        }
    }
}
