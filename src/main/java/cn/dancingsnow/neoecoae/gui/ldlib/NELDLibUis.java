package cn.dancingsnow.neoecoae.gui.ldlib;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.world.entity.player.Player;

/**
 * LDLib1 migration proof of concept.
 *
 * <p>The nativeui screens remain the active fallback path until individual
 * machine UIs are migrated and registered deliberately.
 */
public final class NELDLibUis {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 90;

    public static ModularUI createProofOfConcept(Player player) {
        ModularUI ui = new ModularUI(WIDTH, HEIGHT, IUIHolder.EMPTY, player)
                .background(new ColorRectTexture(0xE0101018), new ColorRectAndBorderTexture(0x00000000, 0xFF4FA8DE, 1));

        ui.widget(new LabelWidget(12, 12, "NeoECOAE LDLib1 POC").setTextColor(0xFFE6F6FF));
        ui.widget(new LabelWidget(12, 30, "nativeui remains fallback").setTextColor(0xFFB8C7D1));
        ui.widget(new ButtonWidget(
                12,
                52,
                72,
                20,
                new GuiTextureGroup(
                        new ColorRectAndBorderTexture(0xFF26323A, 0xFF6CB8E6, 1), new TextTexture("Probe", 0xFFE6F6FF)),
                click -> {}));

        return ui;
    }

    private NELDLibUis() {}
}
