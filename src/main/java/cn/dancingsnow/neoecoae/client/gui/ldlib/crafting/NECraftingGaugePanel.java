package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import static cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout.*;

import cn.dancingsnow.neoecoae.client.gui.ldlib.host.NEHostTextures;
import cn.dancingsnow.neoecoae.gui.ldlib.crafting.NECraftingLayout;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Owns the energy/coolant gauges, fluid texture rendering, and gauge tooltips. */
public final class NECraftingGaugePanel {
    private static final long ENERGY_REFERENCE = 1_000_000L;

    public void drawBackground(NECraftingRenderContext context, NECraftingUiState state, int mouseX, int mouseY) {
        NEHostTextures.drawPanel(
                context.graphics(),
                context.x(GAUGE_AREA_X),
                context.y(GAUGE_AREA_Y),
                GAUGE_AREA_W,
                GAUGE_AREA_H,
                mouseX,
                mouseY);
        double energyRatio = ratio(state.energyUsage(), ENERGY_REFERENCE);
        double coolantRatio = ratio(state.coolantAmount(), state.coolantCapacity());
        drawEnergy(
                context.graphics(),
                context.x(NECraftingLayout.energyGaugeX()),
                context.y(GAUGE_BAR_Y),
                energyColor(energyRatio),
                energyRatio);
        drawCoolant(
                context.graphics(),
                context.x(NECraftingLayout.coolantGaugeX()),
                context.y(GAUGE_BAR_Y),
                state,
                coolantRatio);
    }

    public void draw(NECraftingRenderContext context) {
        context.drawFitted(
                Component.translatable("gui.neoecoae.crafting.energy_cooling"),
                GAUGE_AREA_X + 8,
                GAUGE_AREA_Y + 5,
                GAUGE_AREA_W - 16,
                NELDLibStyle.DARK_TEXT_PRIMARY);
    }

    public boolean drawTooltip(NECraftingRenderContext context, NECraftingUiState state, int mouseX, int mouseY) {
        if (contains(context, NECraftingLayout.energyGaugeX(), ENERGY_GAUGE_W, mouseX, mouseY)) {
            context.graphics()
                    .renderTooltip(
                            context.font(),
                            List.of(
                                    Component.translatable("gui.neoecoae.crafting.energy_usage"),
                                    Component.literal(NELDLibText.number(state.energyUsage()) + " AE/t")),
                            Optional.empty(),
                            mouseX,
                            mouseY);
            return true;
        }
        if (contains(context, NECraftingLayout.coolantGaugeX(), COOLANT_GAUGE_W, mouseX, mouseY)) {
            context.graphics()
                    .renderTooltip(
                            context.font(),
                            List.of(
                                    Component.translatable("gui.neoecoae.crafting.coolant"),
                                    Component.translatable("gui.neoecoae.crafting.coolant_fluid", fluidName(state)),
                                    Component.literal(
                                            NELDLibText.usedTotal(state.coolantAmount(), state.coolantCapacity())
                                                    + " mB"),
                                    Component.literal(
                                            NELDLibText.percentOrNA(state.coolantAmount(), state.coolantCapacity())),
                                    maxOverclockLine(state)),
                            Optional.empty(),
                            mouseX,
                            mouseY);
            return true;
        }
        return false;
    }

    private void drawEnergy(GuiGraphics graphics, int x, int y, int color, double fillRatio) {
        drawGaugeFrame(graphics, x, y, ENERGY_GAUGE_W);
        int innerHeight = GAUGE_BAR_H - 8;
        int fillHeight = (int) Math.round(innerHeight * fillRatio);
        if (fillHeight > 0) {
            int fillY = y + 4 + innerHeight - fillHeight;
            graphics.fill(x + 4, fillY, x + ENERGY_GAUGE_W - 4, y + GAUGE_BAR_H - 4, color);
            graphics.fill(x + 4, fillY, x + ENERGY_GAUGE_W - 4, Math.min(fillY + 2, y + GAUGE_BAR_H - 4), 0x70FFFFFF);
        }
    }

    private void drawCoolant(GuiGraphics graphics, int x, int y, NECraftingUiState state, double fillRatio) {
        drawGaugeFrame(graphics, x, y, COOLANT_GAUGE_W);
        int innerWidth = COOLANT_GAUGE_W - 8;
        int innerHeight = GAUGE_BAR_H - 8;
        int fillHeight = (int) Math.round(innerHeight * fillRatio);
        if (fillHeight <= 0) {
            return;
        }
        FluidStack fluid = fluidStack(state);
        int fillY = y + 4 + innerHeight - fillHeight;
        int fallback = NELDLibAe2StyleRenderer.resolveFluidColor(fluid, NELDLibStyle.DARK_TEXT_BLUE);
        graphics.fill(x + 4, fillY, x + 4 + innerWidth, y + 4 + innerHeight, fallback);
        NELDLibAe2StyleRenderer.drawFluidFill(
                graphics, x + 4, y + 4, innerWidth, innerHeight, fluid, state.coolantAmount(), state.coolantCapacity());
    }

    private void drawGaugeFrame(GuiGraphics graphics, int x, int y, int width) {
        NEHostTextures.drawPanel(graphics, x, y, width, GAUGE_BAR_H, 0, 0);
        graphics.fill(x + 3, y + 3, x + width - 3, y + GAUGE_BAR_H - 3, 0xFF17141E);
        graphics.fill(x + 4, y + 4, x + width - 4, y + GAUGE_BAR_H - 4, 0xFF201E27);
    }

    private boolean contains(NECraftingRenderContext context, int localX, int width, int mouseX, int mouseY) {
        int x = context.x(localX);
        int y = context.y(GAUGE_BAR_Y);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + GAUGE_BAR_H;
    }

    private FluidStack fluidStack(NECraftingUiState state) {
        if (state.coolantAmount() <= 0L || state.coolantFluidId().isBlank()) {
            return FluidStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(state.coolantFluidId());
        Fluid fluid = id == null ? null : ForgeRegistries.FLUIDS.getValue(id);
        return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, 1);
    }

    private Component fluidName(NECraftingUiState state) {
        if (state.coolantAmount() <= 0L) {
            return Component.translatable("gui.neoecoae.crafting.coolant_fluid.none");
        }
        if (state.coolantFluidId().isBlank()) {
            return Component.translatable("gui.neoecoae.crafting.coolant_fluid.unknown");
        }
        FluidStack fluid = fluidStack(state);
        return fluid.isEmpty() ? Component.literal(state.coolantFluidId()) : fluid.getDisplayName();
    }

    private Component maxOverclockLine(NECraftingUiState state) {
        return state.coolantAmount() <= 0L || state.coolantMaxOverclock() < 0
                ? Component.translatable("gui.neoecoae.crafting.coolant_max_overclock.none")
                : Component.translatable("gui.neoecoae.crafting.coolant_max_overclock", state.coolantMaxOverclock());
    }

    private static double ratio(long value, long max) {
        return max <= 0L ? 0.0D : Math.max(0.0D, Math.min(1.0D, value / (double) max));
    }

    private static int energyColor(double ratio) {
        if (ratio >= 0.9D) {
            return NELDLibStyle.DARK_TEXT_ERROR;
        }
        if (ratio >= 0.5D) {
            return NELDLibStyle.DARK_TEXT_WARNING;
        }
        return NELDLibStyle.DARK_TEXT_SUCCESS;
    }
}
