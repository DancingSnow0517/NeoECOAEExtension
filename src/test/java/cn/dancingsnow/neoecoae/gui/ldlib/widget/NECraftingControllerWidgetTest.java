package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NECraftingControllerWidgetTest {
    @Test
    void ldlib2PanelGeometryMatchesReferenceHost() {
        assertEquals(304, NECraftingControllerWidget.UI_WIDTH);
        assertEquals(196, NECraftingControllerWidget.UI_HEIGHT);
        assertEquals(70, NECraftingControllerWidget.STATUS_AREA_H);
        assertEquals(70, NECraftingControllerWidget.STATS_AREA_H);
        assertEquals(70, NECraftingControllerWidget.GAUGE_AREA_H);
        assertEquals(76, NECraftingControllerWidget.STATUS_AREA_W);
        assertEquals(114, NECraftingControllerWidget.STATS_AREA_W);
        assertEquals(90, NECraftingControllerWidget.GAUGE_AREA_W);
        assertEquals(6, NECraftingControllerWidget.STATUS_AREA_X);
        assertEquals(27, NECraftingControllerWidget.STATUS_AREA_Y);
        assertEquals(88, NECraftingControllerWidget.STATS_AREA_X);
        assertEquals(208, NECraftingControllerWidget.GAUGE_AREA_X);
        assertEquals(176, NECraftingControllerWidget.TASK_PANEL_X);
        assertEquals(102, NECraftingControllerWidget.TASK_PANEL_Y);
        assertEquals(122, NECraftingControllerWidget.TASK_PANEL_W);
        assertEquals(88, NECraftingControllerWidget.TASK_PANEL_H);

        assertTrue(NECraftingControllerWidget.STATUS_AREA_X + NECraftingControllerWidget.STATUS_AREA_W
                < NECraftingControllerWidget.STATS_AREA_X);
        assertTrue(NECraftingControllerWidget.STATS_AREA_X + NECraftingControllerWidget.STATS_AREA_W
                < NECraftingControllerWidget.GAUGE_AREA_X);
        assertTrue(NECraftingControllerWidget.GAUGE_AREA_X + NECraftingControllerWidget.GAUGE_AREA_W
                <= NECraftingControllerWidget.UI_WIDTH);
        assertTrue(NECraftingControllerWidget.TASK_PANEL_X + NECraftingControllerWidget.TASK_PANEL_W
                <= NECraftingControllerWidget.UI_WIDTH);
        assertTrue(NECraftingControllerWidget.TASK_PANEL_Y + NECraftingControllerWidget.TASK_PANEL_H
                <= NECraftingControllerWidget.UI_HEIGHT);
        assertTrue(NECraftingControllerWidget.PLAYER_INV_LABEL_Y
                >= NECraftingControllerWidget.STATUS_AREA_Y + NECraftingControllerWidget.STATUS_AREA_H);
    }

    @Test
    void gaugeFramesMatchLdlib2Geometry() {
        assertEquals(20, NECraftingControllerWidget.ENERGY_GAUGE_W);
        assertEquals(23, NECraftingControllerWidget.COOLANT_GAUGE_W);
        assertEquals(14, NECraftingControllerWidget.GAUGE_GAP);
        assertEquals(NECraftingControllerWidget.GAUGE_AREA_Y + 26, NECraftingControllerWidget.GAUGE_BAR_Y);
        assertEquals(
                NECraftingControllerWidget.energyGaugeX()
                        + NECraftingControllerWidget.ENERGY_GAUGE_W
                        + NECraftingControllerWidget.GAUGE_GAP,
                NECraftingControllerWidget.coolantGaugeX());
        assertTrue(NECraftingControllerWidget.coolantGaugeX() + NECraftingControllerWidget.COOLANT_GAUGE_W
                <= NECraftingControllerWidget.GAUGE_AREA_X + NECraftingControllerWidget.GAUGE_AREA_W);
    }
}
