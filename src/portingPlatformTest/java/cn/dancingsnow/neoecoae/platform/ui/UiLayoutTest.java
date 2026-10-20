package cn.dancingsnow.neoecoae.platform.ui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiLayoutTest {
    @Test
    void retainedFluidHatchLayoutCentersTankAndKeepsInventoryBelowIt() {
        var root = new UiLayout.Node(0, 0);
        UiLayoutJson.apply(root.style, JsonParser.parseString("{\"padding\":4,\"gap\":2}").getAsJsonObject());
        var title = new UiLayout.Node(162, 10);
        title.style.width = new UiLayout.Length(100, true);
        var fluid = new UiLayout.Node(0, 0);
        UiLayoutJson.apply(fluid.style, JsonParser.parseString(
            "{\"width\":\"100%\",\"marginTop\":10,\"marginBottom\":10,\"justifyContent\":\"center\",\"alignItems\":\"center\",\"flexDirection\":\"row\"}")
            .getAsJsonObject());
        var tank = new UiLayout.Node(18, 18);
        fluid.children.add(tank);
        var inventory = new UiLayout.Node(162, 77);
        root.children.addAll(java.util.List.of(title, fluid, inventory));
        UiLayout.arrange(root);
        assertEquals(170, root.width);
        assertEquals(137, root.height);
        assertEquals(4, title.x);
        assertEquals(26, fluid.y);
        assertEquals(72, tank.x);
        assertEquals(56, inventory.y);
        assertTrue(inventory.y > fluid.y + fluid.height);
    }

    @Test
    void percentagesAndAbsoluteElementsUseTheParentContentBox() {
        var root = new UiLayout.Node(0, 0);
        UiLayoutJson.apply(root.style, JsonParser.parseString(
            "{\"width\":200,\"height\":100,\"padding\":10}").getAsJsonObject());
        var child = new UiLayout.Node(0, 0);
        UiLayoutJson.apply(child.style, JsonParser.parseString(
            "{\"position\":\"absolute\",\"width\":\"50%\",\"height\":\"50%\",\"left\":\"25%\",\"top\":\"25%\"}")
            .getAsJsonObject());
        root.children.add(child);
        UiLayout.arrange(root);
        assertEquals(90, child.width);
        assertEquals(40, child.height);
        assertEquals(55, child.x);
        assertEquals(30, child.y);
    }

    @Test
    void invalidAndUnsupportedLayoutFailsExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> UiLayoutJson.apply(new UiLayout.Style(),
            JsonParser.parseString("{\"flexGrow\":1}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> UiLayoutJson.length(JsonParser.parseString("\"NaN\"")));
        assertThrows(IllegalArgumentException.class, () -> UiLayoutJson.apply(new UiLayout.Style(),
            JsonParser.parseString("{\"display\":\"grid\"}").getAsJsonObject()));
    }
}
