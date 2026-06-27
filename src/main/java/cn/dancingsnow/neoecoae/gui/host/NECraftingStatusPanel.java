package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class NECraftingStatusPanel extends NESnapshotElement {
    private static final float TEXT_SCALE = 0.95F;
    private static final int EDGE = 7;
    private static final int LIGHT_SIZE = 15;
    private static final int LIGHT_X = EDGE;
    private static final int ROW_0_Y = 23;
    private static final int ROW_1_Y = 44;
    private static final int ROW_2_Y = 65;

    private boolean overclocked;
    private boolean activeCooling;
    private int coolant;
    private float mouseX;
    private float mouseY;

    NECraftingStatusPanel(Supplier<byte[]> serverSnapshot) {
        super(serverSnapshot);
        layout(layout -> layout.widthPercent(100).heightPercent(100));
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            HoverTooltips tooltip = tooltipAt(mouseX, mouseY);
            if (tooltip != null) {
                event.hoverTooltips = tooltip;
                event.stopPropagation();
            }
        });
    }

    @Override
    protected void acceptSnapshot(byte[] snapshotData) {
        NEHostSnapshots.decode(snapshotData, buf -> {
            overclocked = buf.readBoolean();
            activeCooling = buf.readBoolean();
            coolant = Math.max(0, buf.readVarInt());
        });
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        this.mouseX = context.mouseX;
        this.mouseY = context.mouseY;
        NEHostUiPrimitives.scaledFittedText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.status", "Status"),
            EDGE, EDGE, TEXT_SCALE, Math.round(getSizeWidth()) - EDGE * 2, NEHostUiPrimitives.TEXT_PRIMARY);
        drawStatusRow(context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.overclock", "OC"), overclocked, ROW_0_Y);
        drawStatusRow(context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.cooling_short", "Cool"), activeCooling, ROW_1_Y);
        drawStatusRow(context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.waste_short", "Waste"), coolant > 0, ROW_2_Y);
    }

    private void drawStatusRow(GUIContext context, Component label, boolean enabled, int y) {
        NEHostUiPrimitives.insetRect(this, context, LIGHT_X, y - 4, LIGHT_SIZE, LIGHT_SIZE);
        NEHostUiPrimitives.rect(this, context, LIGHT_X + 4, y, 7, 7, enabled ? NEHostUiPrimitives.TEXT_SUCCESS : NEHostUiPrimitives.TEXT_ERROR);
        String value = NEHostUiPrimitives.trString(enabled ? "gui.neoecoae.common.on" : "gui.neoecoae.common.off", enabled ? "On" : "Off");
        int valueWidth = Math.round(context.mc.font.width(value) * TEXT_SCALE);
        int labelX = LIGHT_X + LIGHT_SIZE + EDGE;
        int labelMax = Math.round(getSizeWidth()) - EDGE - labelX - valueWidth;
        NEHostUiPrimitives.scaledFittedText(this, context, label, labelX, y, TEXT_SCALE, labelMax, NEHostUiPrimitives.TEXT_MUTED);
        NEHostUiPrimitives.scaledText(this, context, value, getSizeWidth() - EDGE - valueWidth, y, TEXT_SCALE,
            enabled ? NEHostUiPrimitives.TEXT_SUCCESS : NEHostUiPrimitives.TEXT_ERROR);
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        if (NEHostUiPrimitives.contains(this, LIGHT_X, ROW_0_Y - 4, getSizeWidth() - EDGE * 2, LIGHT_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                Component.translatable(overclocked ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off"),
                Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")
            ), null, null, null);
        }
        if (NEHostUiPrimitives.contains(this, LIGHT_X, ROW_1_Y - 4, getSizeWidth() - EDGE * 2, LIGHT_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                Component.translatable(activeCooling ? "gui.neoecoae.crafting.active_cooling.on" : "gui.neoecoae.crafting.active_cooling.off"),
                Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")
            ), null, null, null);
        }
        if (NEHostUiPrimitives.contains(this, LIGHT_X, ROW_2_Y - 4, getSizeWidth() - EDGE * 2, LIGHT_SIZE, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                NEHostUiPrimitives.tr("gui.neoecoae.crafting.coolant", "Coolant"),
                NEHostUiPrimitives.tr("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s",
                    fullNumber(coolant), fullNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)),
                Component.literal(NEHostFormat.percent(coolant, ECOCraftingSystemBlockEntity.MAX_COOLANT))
            ), null, null, null);
        }
        return null;
    }

    private static String fullNumber(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }
}
