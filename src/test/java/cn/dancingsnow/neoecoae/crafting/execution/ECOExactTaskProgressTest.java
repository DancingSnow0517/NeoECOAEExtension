package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ECOExactTaskProgressTest {
    @Test
    void progressCrossesLongBoundaryAndSurvivesRestart() {
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(9));
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.setExact(total, total);
        assertEquals(Long.MAX_VALUE, progress.value);
        progress.accept(10);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE - 1), progress.remainingExact());

        CompoundTag saved = new CompoundTag();
        progress.writeExact(saved);
        var restored = new ExecutingCraftingJob.TaskProgress();
        restored.readExact(saved);
        assertEquals(progress.remainingExact(), restored.remainingExact());
        restored.accept(Long.MAX_VALUE - 1);
        assertEquals(BigInteger.ZERO, restored.remainingExact());
        assertEquals(0, restored.value);
        assertThrows(IllegalArgumentException.class, () -> restored.accept(1));
    }
}
