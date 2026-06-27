package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class NECraftingGaugePanel extends NESnapshotElement {
    private static final float TEXT_SCALE = 0.95F;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000_000L;
    private static final int EDGE = 7;
    private static final int BAR_Y = 26;
    private static final int BAR_H = 45;
    private static final int BAR_W = 24;
    private static final int BAR_GAP = 7;

    private boolean overclocked;
    private boolean activeCooling;
    private int coolant;
    private long maxEnergyUsage;
    private float mouseX;
    private float mouseY;

    NECraftingGaugePanel(Supplier<byte[]> serverSnapshot) {
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
            maxEnergyUsage = Math.max(0L, buf.readVarLong());
        });
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        this.mouseX = context.mouseX;
        this.mouseY = context.mouseY;
        NEHostUiPrimitives.scaledFittedText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.energy_cooling", "Energy/Coolant"),
            EDGE, EDGE, TEXT_SCALE, Math.round(getSizeWidth()) - EDGE * 2, NEHostUiPrimitives.TEXT_PRIMARY);
        int pairW = BAR_W * 2 + BAR_GAP;
        int energyX = Math.round((getSizeWidth() - pairW) / 2.0F);
        int coolantX = energyX + BAR_W + BAR_GAP;
        double energyRatio = maxEnergyUsage <= 0 ? 0 : Math.min(1.0D, (double) maxEnergyUsage / ENERGY_GAUGE_REFERENCE);
        double coolantRatio = coolant <= 0 ? 0 : Math.min(1.0D, (double) coolant / ECOCraftingSystemBlockEntity.MAX_COOLANT);
        int energyColor = energyRatio >= 0.9D ? NEHostUiPrimitives.TEXT_ERROR
            : energyRatio >= 0.5D ? NEHostUiPrimitives.TEXT_WARNING : NEHostUiPrimitives.TEXT_SUCCESS;
        NEHostUiPrimitives.verticalGauge(this, context, energyX, BAR_Y, BAR_W, BAR_H, energyRatio, energyColor);
        NEHostUiPrimitives.verticalGauge(this, context, coolantX, BAR_Y, BAR_W, BAR_H, coolantRatio, NEHostUiPrimitives.TEXT_BLUE);
        NEHostUiPrimitives.scaledCenteredText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.energy_short", "AE"),
            energyX - 7, BAR_Y + BAR_H + 2, BAR_W + 14, TEXT_SCALE, NEHostUiPrimitives.TEXT_MUTED);
        NEHostUiPrimitives.scaledCenteredText(this, context, NEHostUiPrimitives.tr("gui.neoecoae.crafting.cooling_short", "Cool"),
            coolantX - 7, BAR_Y + BAR_H + 2, BAR_W + 14, TEXT_SCALE, NEHostUiPrimitives.TEXT_MUTED);
    }

    private HoverTooltips tooltipAt(double mouseX, double mouseY) {
        int pairW = BAR_W * 2 + BAR_GAP;
        int energyX = Math.round((getSizeWidth() - pairW) / 2.0F);
        int coolantX = energyX + BAR_W + BAR_GAP;
        if (NEHostUiPrimitives.contains(this, energyX, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            return new HoverTooltips(List.of(
                NEHostUiPrimitives.tr("gui.neoecoae.crafting.energy_usage", "Energy Usage"),
                Component.literal(Tooltips.ofNumber(maxEnergyUsage).getString() + " AE/t")
            ), null, null, null);
        }
        if (NEHostUiPrimitives.contains(this, coolantX, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            return coolantTooltip();
        }
        return null;
    }

    HoverTooltips overclockTooltip() {
        return new HoverTooltips(List.of(
            Component.translatable(overclocked ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off"),
            Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")
        ), null, null, null);
    }

    HoverTooltips coolingTooltip() {
        return new HoverTooltips(List.of(
            Component.translatable(activeCooling ? "gui.neoecoae.crafting.active_cooling.on" : "gui.neoecoae.crafting.active_cooling.off"),
            Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")
        ), null, null, null);
    }

    HoverTooltips coolantTooltip() {
        return new HoverTooltips(List.of(
            NEHostUiPrimitives.tr("gui.neoecoae.crafting.coolant", "Coolant"),
            NEHostUiPrimitives.tr("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s",
                fullNumber(coolant), fullNumber(ECOCraftingSystemBlockEntity.MAX_COOLANT)),
            Component.literal(NEHostFormat.percent(coolant, ECOCraftingSystemBlockEntity.MAX_COOLANT))
        ), null, null, null);
    }

    private static String fullNumber(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }
}
