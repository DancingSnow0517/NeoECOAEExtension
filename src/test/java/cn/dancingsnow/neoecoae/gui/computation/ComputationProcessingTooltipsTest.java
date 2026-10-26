package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ComputationProcessingTooltipsTest {
    @Test void summaryNamesCycleStateLocationAndAssociationAndKeepsUnknownTimingUnknown() {
        var progress = ECOProcessingProgress.summarize(List.of(new Machine(Component.literal("Factory"),
            "overworld [1,2,3]", "stone", 0, 0, 1, State.PAUSED, Component.literal("Shared recipe"))));
        String text = ComputationProcessingTooltips.lines(progress).toString();
        assertTrue(text.contains("processing.summary"));
        assertTrue(text.contains("processing.state.paused"));
        assertTrue(text.contains("overworld [1,2,3]"));
        assertTrue(text.contains("Shared recipe"));
        assertFalse(text.contains("0%"));
    }

    @Test void absentMachinesAddNothingAndTruncatedDetailsKeepTheFullSummaryCount() {
        assertTrue(ComputationProcessingTooltips.lines(ECOProcessingProgress.EMPTY).isEmpty());
        var machine = new Machine(Component.literal("FX"), "overworld", "stone", 25, 100, 1, State.RUNNING, Component.empty());
        var progress = ECOProcessingProgress.summarize(List.of(machine, machine, machine, machine)).page(0);
        var lines = ComputationProcessingTooltips.lines(progress).toString();
        assertTrue(lines.contains("25%"));
        assertTrue(lines.contains("processing.more"));
        assertEquals(4, progress.machineCount());
    }
}
