package cn.dancingsnow.neoecoae.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NECraftingLayoutTest {
    @Test
    void ldlib2PanelGeometryMatchesReferenceHost() {
        assertEquals(304, UI_WIDTH);
        assertEquals(196, UI_HEIGHT);
        assertEquals(70, STATUS_AREA_H);
        assertEquals(70, STATS_AREA_H);
        assertEquals(70, GAUGE_AREA_H);
        assertEquals(76, STATUS_AREA_W);
        assertEquals(114, STATS_AREA_W);
        assertEquals(90, GAUGE_AREA_W);
        assertEquals(6, STATUS_AREA_X);
        assertEquals(27, STATUS_AREA_Y);
        assertEquals(88, STATS_AREA_X);
        assertEquals(208, GAUGE_AREA_X);
        assertEquals(176, TASK_PANEL_X);
        assertEquals(102, TASK_PANEL_Y);
        assertEquals(122, TASK_PANEL_W);
        assertEquals(88, TASK_PANEL_H);

        assertTrue(STATUS_AREA_X + STATUS_AREA_W < STATS_AREA_X);
        assertTrue(STATS_AREA_X + STATS_AREA_W < GAUGE_AREA_X);
        assertTrue(GAUGE_AREA_X + GAUGE_AREA_W <= UI_WIDTH);
        assertTrue(TASK_PANEL_X + TASK_PANEL_W <= UI_WIDTH);
        assertTrue(TASK_PANEL_Y + TASK_PANEL_H <= UI_HEIGHT);
        assertTrue(PLAYER_INV_LABEL_Y >= STATUS_AREA_Y + STATUS_AREA_H);
    }

    @Test
    void gaugeFramesMatchLdlib2Geometry() {
        assertEquals(20, ENERGY_GAUGE_W);
        assertEquals(23, COOLANT_GAUGE_W);
        assertEquals(14, GAUGE_GAP);
        assertEquals(GAUGE_AREA_Y + 26, GAUGE_BAR_Y);
        assertEquals(energyGaugeX() + ENERGY_GAUGE_W + GAUGE_GAP, coolantGaugeX());
        assertTrue(coolantGaugeX() + COOLANT_GAUGE_W <= GAUGE_AREA_X + GAUGE_AREA_W);
    }
}
