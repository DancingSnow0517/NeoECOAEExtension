package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.client.gui.ldlib.computation.NEComputationTaskPanel;
import org.junit.jupiter.api.Test;

class NEComputationControllerWidgetTest {
    @Test
    void ldlib2HostGeometryFitsTheCompactCanvas() {
        assertEquals(344, NEComputationControllerWidget.UI_WIDTH);
        assertEquals(232, NEComputationControllerWidget.UI_HEIGHT);
        assertEquals(162, CAPACITY_PANEL_W);
        assertEquals(108, CAPACITY_PANEL_H);
        assertEquals(156, TASK_PANEL_W);
        assertEquals(200, TASK_PANEL_H);
        assertTrue(CAPACITY_PANEL_X + CAPACITY_PANEL_W < TASK_PANEL_X);
        assertTrue(TASK_PANEL_X + TASK_PANEL_W <= UI_WIDTH);
        assertTrue(PLAYER_HOTBAR_Y + 18 <= UI_HEIGHT);
        assertTrue(TASK_PANEL_Y + TASK_PANEL_H <= UI_HEIGHT);
    }

    @Test
    void taskCardGeometryShowsSixCardsAndClampsScrolling() {
        assertEquals(6, NEComputationTaskPanel.visibleCardCount());
        assertEquals(0, NEComputationTaskPanel.clampScrollOffset(-2, 20));
        assertEquals(14, NEComputationTaskPanel.clampScrollOffset(99, 20));
        assertEquals(0, NEComputationTaskPanel.clampScrollOffset(3, 4));
        assertEquals(132, TASK_CARD_W);
        assertEquals(28, TASK_CARD_H);
        assertEquals(30, TASK_CARD_STRIDE);
    }

    @Test
    void cpuSelectionModeCyclesEvenWithoutAFormedCluster() {
        assertEquals(
                CpuSelectionMode.PLAYER_ONLY, NEComputationControllerWidget.nextCpuSelectionMode(CpuSelectionMode.ANY));
        assertEquals(
                CpuSelectionMode.MACHINE_ONLY,
                NEComputationControllerWidget.nextCpuSelectionMode(CpuSelectionMode.PLAYER_ONLY));
        assertEquals(
                CpuSelectionMode.ANY,
                NEComputationControllerWidget.nextCpuSelectionMode(CpuSelectionMode.MACHINE_ONLY));
    }
}
