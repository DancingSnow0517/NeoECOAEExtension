package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingTooltipCard;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Persistent button-local animation; rebuilding a hover card never restarts the swap. */
final class LargeWorkstationPowerPriorityCard {
    private static final String PREFIX = "gui.neoecoae.workstation.power_priority.";
    private static final int AE = 0xFF73DFED;
    private static final int FE = 0xFFE6BB68;
    private static final int HEIGHT = 104;
    private static final double RESPONSE = 18;
    private final SDFRectTexture tile = SDFRectTexture.of(0).setRadius(4).setStroke(0.7f);
    private final Component primary = tr("primary");
    private final Component backup = tr("backup");
    private boolean fePreferred;
    private long clicks;
    private double position;
    private double velocity;
    private long lastFrame;

    LargeWorkstationPowerPriorityCard(boolean fePreferred) {
        this.fePreferred = fePreferred;
        position = fePreferred ? 1 : 0;
    }

    void click() {
        clicks++;
        fePreferred = !fePreferred;
        lastFrame = 0;
    }

    void receive(CompoundTag state) {
        // Acknowledgements and priority travel together. Older replies must retain pending local clicks.
        long pending = clicks - state.getLong("acknowledgedClicks");
        boolean preferred = state.getBoolean("fePreferred") ^ ((pending & 1) != 0);
        if (preferred != fePreferred) lastFrame = 0;
        fePreferred = preferred;
    }

    boolean fePreferred() { return fePreferred; }
    double position() { return position; }

    void advance(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return;
        double target = fePreferred ? 1 : 0;
        double displacement = position - target;
        double decay = Math.exp(-RESPONSE * seconds);
        double step = (velocity + RESPONSE * displacement) * seconds;
        position = target + (displacement + step) * decay;
        velocity = (velocity - RESPONSE * step) * decay;
        if (Math.abs(position - target) < 0.0001 && Math.abs(velocity) < 0.001) {
            position = target;
            velocity = 0;
        }
    }

    CraftingListCard.Builder builder(CompoundTag state) {
        return CraftingListCard.builder().minimumWidth(240)
            .header(CraftingListCard.Glyph.BOLT, tr("title"), card("energy_monitor"), CraftingTooltipCard.MUTED)
            .divider().space(3)
            .row(null, FE, tr("peak"), amount(state, "energyPeak", "ae_rate"), FE)
            .space(5)
            .row(null, FE, tr("network_fe"), amount(state, "networkFE", "fe_amount"), FE)
            .space(5)
            .row(null, AE, tr("network_ae"), amount(state, "networkAE", "ae_amount"), AE)
            .space(7).divider()
            .content(160, HEIGHT, this::drawSources, Component.literal(fePreferred ? "FE → AE" : "AE → FE"),
                primary, backup)
            .divider()
            .wrapped(CraftingListCard.Glyph.INFO, tr("fallback_hint"), CraftingTooltipCard.MUTED)
            .text(tr("switch_hint"), Component.empty(), CraftingTooltipCard.MUTED);
    }

    private static Component amount(CompoundTag state, String key, String unit) {
        if (!state.contains(key)) return Component.literal("—");
        return card(unit, "networkFE".equals(key)
            ? DisplayNumbers.compact(state.getLong(key)) : DisplayNumbers.compact(state.getDouble(key)));
    }

    private void drawSources(GuiGraphics graphics, Font font, int x, int y, int width) {
        long now = System.nanoTime();
        if (lastFrame != 0) advance(Math.min(1, (now - lastFrame) / 1_000_000_000.0));
        lastFrame = now;
        float progress = (float) position;
        float centerX = x + width / 2f, centerY = y + HEIGHT / 2f;
        float arc = 28 * (float) Math.sin(Math.PI * progress);
        graphics.drawString(font, "→", Math.round(centerX) - font.width("→") / 2,
            Math.round(centerY) - 4, CraftingTooltipCard.MUTED, false);
        drawSource(graphics, font, "AE", AE, 1 - progress,
            centerX + Mth.lerp(progress, -38, 38), centerY - arc);
        drawSource(graphics, font, "FE", FE, progress,
            centerX + Mth.lerp(progress, 38, -38), centerY + arc);
    }

    private void drawSource(GuiGraphics graphics, Font font, String label, int color, float emphasis, float x, float y) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            float scale = Mth.lerp(emphasis, 0.68f, 1);
            pose.scale(scale, scale, 1);
            int background = ColorUtils.blendRGBColor(0xFF111319, color, Mth.lerp(emphasis, 0.10f, 0.21f));
            tile.setColor(background).setBorderColor(ColorUtils.blendRGBColor(background, color, emphasis * 0.65f))
                .draw(graphics, 0, 0, -26, -24, 52, 48, 0);
            pose.pushPose();
            try {
                pose.translate(0, -13, 0);
                pose.scale(2.3f, 2.3f, 1);
                graphics.drawString(font, label, -font.width(label) / 2, 0,
                    ColorUtils.blendRGBColor(CraftingTooltipCard.MUTED, color, 0.55f + emphasis * 0.45f), false);
            } finally {
                pose.popPose();
            }
            role(graphics, font, primary, color, emphasis);
            role(graphics, font, backup, CraftingTooltipCard.MUTED, 1 - emphasis);
        } finally {
            pose.popPose();
        }
    }

    private static void role(GuiGraphics graphics, Font font, Component text, int color, float opacity) {
        int alpha = Math.round(255 * opacity);
        // Minecraft treats very small text alpha as opaque, so skip fully faded labels.
        if (alpha < 4) return;
        float scale = Math.min(0.8f, 46f / Math.max(1, font.width(text)));
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0, 13, 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text) / 2, 0, (alpha << 24) | (color & 0xFFFFFF), false);
        } finally {
            pose.popPose();
        }
    }

    private static Component tr(String key) { return Component.translatable(PREFIX + key); }
    private static Component card(String key, Object... args) {
        return Component.translatable("gui.neoecoae.workstation.card." + key, args);
    }
}
