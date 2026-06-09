package cn.dancingsnow.neoecoae.gui.ldlib;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import cn.dancingsnow.neoecoae.network.NENetwork;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * LDLib1 display for the ECO Crafting Controller status view. It consumes the
 * existing {@link NECraftingUiState} packet stream and keeps control actions on
 * {@link NENetwork.NECraftingUiActionPacket}.
 */
@SuppressWarnings("deprecation")
public class NECraftingControllerLDLibUI extends NELDLibMachineScreen<NECraftingControllerMenu> {
    private static final int WIDTH = 372;
    private static final int HEIGHT = 240;

    private static final int BUTTON_Y = 5;
    private static final int BUTTON_H = 18;
    private static final int BUTTON_W = 68;
    private static final int BUTTON_GAP = 4;
    private static final int OVERCLOCK_BUTTON_X = 156;
    private static final int ACTIVE_COOLING_BUTTON_X = OVERCLOCK_BUTTON_X + BUTTON_W + BUTTON_GAP;
    private static final int AUTO_CLEAR_BUTTON_X = ACTIVE_COOLING_BUTTON_X + BUTTON_W + BUTTON_GAP;

    private static final int STATUS_PANEL_X = 8;
    private static final int STATUS_PANEL_Y = 25;
    private static final int STATUS_PANEL_W = 356;
    private static final int STATUS_PANEL_H = 69;

    private static final int THREAD_PANEL_X = 8;
    private static final int THREAD_PANEL_Y = 102;
    private static final int THREAD_PANEL_W = 356;
    private static final int THREAD_PANEL_H = 38;

    private static final int WORKER_PANEL_X = 8;
    private static final int WORKER_PANEL_Y = 148;
    private static final int WORKER_PANEL_W = 356;
    private static final int WORKER_PANEL_H = 49;

    private static final int FORMED_BAR_X = 8;
    private static final int FORMED_BAR_Y = 205;
    private static final int FORMED_BAR_W = 356;
    private static final int FORMED_BAR_H = 25;

    private static final int WORKER_SLOT_SIZE = 18;
    private static final int WORKER_SLOT_GAP = 2;
    private static final int MAX_VISIBLE_WORKER_SLOTS = 16;
    private static final int WORKER_SLOT_Y = WORKER_PANEL_Y + 16;

    private static final int TIER_TOOLTIP_X = WORKER_PANEL_X + 8;
    private static final int TIER_TOOLTIP_Y = WORKER_PANEL_Y + 38;
    private static final int TIER_TOOLTIP_W = WORKER_PANEL_W - 16;
    private static final int TIER_TOOLTIP_H = 9;

    private static final long MAX_ENERGY_USAGE = 563200L;

    private boolean hasCraftingState;
    private NECraftingUiState craftingState;

    public NECraftingControllerLDLibUI(NECraftingControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, WIDTH, HEIGHT);
        this.craftingState = NECraftingUiState.empty(menu.getMachinePos());
    }

    public void setCraftingUiState(NECraftingUiState state) {
        this.hasCraftingState = true;
        this.craftingState = state;
    }

    @Override
    protected void initLdWidgets() {
        addActionButton(
                OVERCLOCK_BUTTON_X,
                "Overclock",
                () -> currentState().overclocked(),
                NENetwork.NECraftingUiActionPacket.Action.TOGGLE_OVERCLOCK);
        addActionButton(
                ACTIVE_COOLING_BUTTON_X,
                "Cooling",
                () -> currentState().activeCooling(),
                NENetwork.NECraftingUiActionPacket.Action.TOGGLE_ACTIVE_COOLING);
        addActionButton(
                AUTO_CLEAR_BUTTON_X,
                "Waste",
                () -> currentState().autoClearCoolingWaste(),
                NENetwork.NECraftingUiActionPacket.Action.TOGGLE_AUTO_CLEAR_COOLING_WASTE);

        addProgress(
                70,
                84,
                72,
                6,
                () -> percent(currentState().energyUsage(), MAX_ENERGY_USAGE),
                0xFF49A36E,
                ProgressTexture.FillDirection.LEFT_TO_RIGHT);
        addProgress(
                235,
                84,
                72,
                6,
                () -> percent(currentState().coolantAmount(), currentState().coolantCapacity()),
                0xFF3A8FD6,
                ProgressTexture.FillDirection.LEFT_TO_RIGHT);
        addProgress(
                108,
                132,
                244,
                8,
                () -> percent(currentState().runningThreadCount(), threadBarMax(currentState())),
                0xFF49A36E,
                ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    }

    private void addActionButton(
            int x,
            String label,
            java.util.function.BooleanSupplier enabled,
            NENetwork.NECraftingUiActionPacket.Action action) {
        addLdWidget(new ButtonWidget(
                        x,
                        BUTTON_Y,
                        BUTTON_W,
                        BUTTON_H,
                        buttonTexture(),
                        click -> NENetwork.CHANNEL.sendToServer(
                                new NENetwork.NECraftingUiActionPacket(menu.getMachinePos(), action))))
                .setHoverTexture(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));
        addText(
                x,
                BUTTON_Y + 5,
                BUTTON_W,
                8,
                () -> Component.literal(label + " " + onOff(enabled.getAsBoolean())),
                TEXT_VALUE,
                TextTexture.TextType.NORMAL);
    }

    @Override
    protected void renderLdBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawPanel(guiGraphics, STATUS_PANEL_X, STATUS_PANEL_Y, STATUS_PANEL_W, STATUS_PANEL_H);
        drawPanel(guiGraphics, THREAD_PANEL_X, THREAD_PANEL_Y, THREAD_PANEL_W, THREAD_PANEL_H);
        drawPanel(guiGraphics, WORKER_PANEL_X, WORKER_PANEL_Y, WORKER_PANEL_W, WORKER_PANEL_H);
        drawPanel(guiGraphics, FORMED_BAR_X, FORMED_BAR_Y, FORMED_BAR_W, FORMED_BAR_H);

        NECraftingUiState state = currentState();
        drawStatusPanel(guiGraphics, state);
        drawThreadPanel(guiGraphics, state);
        drawWorkerPanel(guiGraphics, state);
        drawFormedBar(guiGraphics, state);
    }

    private void drawStatusPanel(GuiGraphics g, NECraftingUiState state) {
        drawLocalString(
                g, Component.literal("Controller Status"), STATUS_PANEL_X + 8, STATUS_PANEL_Y + 7, TEXT_PRIMARY);

        int y = STATUS_PANEL_Y + 20;
        drawPair(g, "Formed", yesNo(state.formed()), STATUS_PANEL_X + 8, y, state.formed() ? TEXT_SUCCESS : TEXT_ERROR);
        drawPair(
                g, "Active", yesNo(state.active()), STATUS_PANEL_X + 74, y, state.active() ? TEXT_SUCCESS : TEXT_MUTED);
        drawPair(
                g,
                "OC",
                onOff(state.overclocked()),
                STATUS_PANEL_X + 136,
                y,
                state.overclocked() ? TEXT_SUCCESS : TEXT_ERROR);
        drawPair(
                g,
                "Cooling",
                onOff(state.activeCooling()),
                STATUS_PANEL_X + 190,
                y,
                state.activeCooling() ? TEXT_SUCCESS : TEXT_ERROR);
        drawPair(
                g,
                "Waste",
                onOff(state.autoClearCoolingWaste()),
                STATUS_PANEL_X + 278,
                y,
                state.autoClearCoolingWaste() ? TEXT_SUCCESS : TEXT_ERROR);

        y = STATUS_PANEL_Y + 33;
        drawPair(g, "Workers", fmt(state.workerCount()), STATUS_PANEL_X + 8, y, TEXT_VALUE);
        drawPair(g, "Parallel", fmt(state.parallelCount()), STATUS_PANEL_X + 96, y, TEXT_VALUE);
        drawPair(g, "Pattern Buses", fmt(state.patternBusCount()), STATUS_PANEL_X + 190, y, TEXT_VALUE);

        y = STATUS_PANEL_Y + 46;
        drawPair(
                g, "Energy", fmt(state.energyUsage()) + " AE", STATUS_PANEL_X + 8, y, energyColor(state.energyUsage()));
        drawPair(
                g,
                "Coolant",
                fmt(state.coolantAmount()) + " / " + fmt(state.coolantCapacity()) + " mB",
                STATUS_PANEL_X + 172,
                y,
                TEXT_VALUE);
    }

    private void drawThreadPanel(GuiGraphics g, NECraftingUiState state) {
        drawLocalString(g, Component.literal("Crafting Threads"), THREAD_PANEL_X + 8, THREAD_PANEL_Y + 7, TEXT_PRIMARY);
        int y = THREAD_PANEL_Y + 20;
        drawPair(g, "Max", fmt(state.threadCount()), THREAD_PANEL_X + 8, y, TEXT_VALUE);
        drawPair(g, "Running", fmt(state.runningThreadCount()), THREAD_PANEL_X + 70, y, TEXT_SUCCESS);
        drawPair(g, "Available", fmt(state.availableThreads()), THREAD_PANEL_X + 156, y, TEXT_VALUE);
        drawPair(g, "Effective Parallel", fmt(state.effectiveParallel()), THREAD_PANEL_X + 252, y, TEXT_VALUE);
    }

    private void drawWorkerPanel(GuiGraphics g, NECraftingUiState state) {
        drawLocalString(g, Component.literal("Worker Outputs"), WORKER_PANEL_X + 8, WORKER_PANEL_Y + 5, TEXT_PRIMARY);
        drawRightLocalString(
                g,
                Component.literal(workerHeader(state)),
                WORKER_PANEL_X + WORKER_PANEL_W - 8,
                WORKER_PANEL_Y + 5,
                TEXT_MUTED);

        int visibleSlots = visibleWorkerSlots(state);
        if (visibleSlots <= 0) {
            drawCenteredLocalString(
                    g,
                    Component.literal("No worker cores detected"),
                    WORKER_PANEL_X,
                    WORKER_SLOT_Y + 4,
                    WORKER_PANEL_W,
                    TEXT_MUTED);
        } else {
            int startX = workerSlotsStartX(visibleSlots);
            for (int i = 0; i < visibleSlots; i++) {
                drawWorkerSlot(g, state, i, startX + i * (WORKER_SLOT_SIZE + WORKER_SLOT_GAP), WORKER_SLOT_Y);
            }
        }

        drawLocalString(g, Component.literal(parallelTierSummary(state)), TIER_TOOLTIP_X, TIER_TOOLTIP_Y, TEXT_VALUE);
    }

    private void drawWorkerSlot(GuiGraphics g, NECraftingUiState state, int index, int x, int y) {
        int left = leftPos + x;
        int top = topPos + y;
        g.fill(left, top, left + WORKER_SLOT_SIZE, top + WORKER_SLOT_SIZE, 0xFF9AA0AA);
        g.fill(left + 1, top + 1, left + WORKER_SLOT_SIZE - 1, top + WORKER_SLOT_SIZE - 1, 0xFFECEEF3);
        g.fill(left + 2, top + 2, left + WORKER_SLOT_SIZE - 2, top + WORKER_SLOT_SIZE - 2, 0xFFD7DAE2);

        ItemStack stack = workerOutputAt(state, index);
        if (!stack.isEmpty()) {
            g.renderItem(stack, left + 1, top + 1);
            g.renderItemDecorations(font, stack, left + 1, top + 1);
        }
    }

    private void drawFormedBar(GuiGraphics g, NECraftingUiState state) {
        Component text = Component.translatable("gui.neoecoae.machine.formed")
                .append(": ")
                .append(boolText(state.formed()))
                .append("    ")
                .append(Component.translatable("gui.neoecoae.machine.active"))
                .append(": ")
                .append(boolText(state.active()));
        drawCenteredLocalString(
                g, text, FORMED_BAR_X, FORMED_BAR_Y + 8, FORMED_BAR_W, state.formed() ? TEXT_SUCCESS : TEXT_ERROR);
    }

    @Override
    protected void renderLdTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (renderActionTooltip(guiGraphics, mouseX, mouseY)) {
            return;
        }
        if (renderResourceTooltip(guiGraphics, mouseX, mouseY)) {
            return;
        }
        if (renderWorkerTooltip(guiGraphics, mouseX, mouseY)) {
            return;
        }
        renderParallelTierTooltip(guiGraphics, mouseX, mouseY);
    }

    private boolean renderActionTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        if (isMouseIn(OVERCLOCK_BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            renderSimpleTooltip(g, mouseX, mouseY, "Toggle Overclock", "Current: " + onOff(state.overclocked()));
            return true;
        }
        if (isMouseIn(ACTIVE_COOLING_BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            renderSimpleTooltip(g, mouseX, mouseY, "Toggle Active Cooling", "Current: " + onOff(state.activeCooling()));
            return true;
        }
        if (isMouseIn(AUTO_CLEAR_BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            renderSimpleTooltip(
                    g,
                    mouseX,
                    mouseY,
                    "Toggle Auto Clear Cooling Waste",
                    "Current: " + onOff(state.autoClearCoolingWaste()));
            return true;
        }
        return false;
    }

    private boolean renderResourceTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        if (isMouseIn(70, 84, 72, 6, mouseX, mouseY)) {
            renderSimpleTooltip(g, mouseX, mouseY, "Energy Usage", fmt(state.energyUsage()) + " AE");
            return true;
        }
        if (isMouseIn(235, 84, 72, 6, mouseX, mouseY)) {
            renderSimpleTooltip(
                    g,
                    mouseX,
                    mouseY,
                    "Coolant",
                    fmt(state.coolantAmount()) + " / " + fmt(state.coolantCapacity()) + " mB");
            return true;
        }
        if (isMouseIn(108, 132, 244, 8, mouseX, mouseY)) {
            renderSimpleTooltip(
                    g,
                    mouseX,
                    mouseY,
                    "Thread Usage",
                    fmt(state.runningThreadCount()) + " / " + fmt(threadBarMax(state)));
            return true;
        }
        return false;
    }

    private boolean renderWorkerTooltip(GuiGraphics g, int mouseX, int mouseY) {
        NECraftingUiState state = currentState();
        int visibleSlots = visibleWorkerSlots(state);
        if (visibleSlots <= 0) {
            return false;
        }

        int startX = workerSlotsStartX(visibleSlots);
        for (int i = 0; i < visibleSlots; i++) {
            int x = startX + i * (WORKER_SLOT_SIZE + WORKER_SLOT_GAP);
            if (!isMouseIn(x, WORKER_SLOT_Y, WORKER_SLOT_SIZE, WORKER_SLOT_SIZE, mouseX, mouseY)) {
                continue;
            }

            ItemStack stack = workerOutputAt(state, i);
            if (!stack.isEmpty() && minecraft != null) {
                List<Component> lines = Screen.getTooltipFromItem(minecraft, stack);
                g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
            } else {
                renderSimpleTooltip(g, mouseX, mouseY, "Worker Slot " + (i + 1), "No active output");
            }
            return true;
        }
        return false;
    }

    private void renderParallelTierTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!isMouseIn(TIER_TOOLTIP_X, TIER_TOOLTIP_Y, TIER_TOOLTIP_W, TIER_TOOLTIP_H, mouseX, mouseY)) {
            return;
        }

        NECraftingUiState state = currentState();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Parallel Core Tiers"));
        lines.add(Component.literal("FT4: " + countTier(state, 1) + " cores, " + parallelPerCore(1, state.overclocked())
                + " parallel/core"));
        lines.add(Component.literal("FT6: " + countTier(state, 2) + " cores, " + parallelPerCore(2, state.overclocked())
                + " parallel/core"));
        lines.add(Component.literal("FT9: " + countTier(state, 3) + " cores, " + parallelPerCore(3, state.overclocked())
                + " parallel/core"));
        int unknown =
                Math.max(0, state.parallelCount() - state.parallelCoreTiers().size());
        if (unknown > 0) {
            lines.add(Component.literal("Unreported cores: " + unknown));
        }
        lines.add(Component.literal("Effective parallel: " + fmt(state.effectiveParallel())));
        g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private NECraftingUiState currentState() {
        return hasCraftingState ? craftingState : NECraftingUiState.empty(menu.getMachinePos());
    }

    private void drawPair(GuiGraphics g, String label, String value, int x, int y, int valueColor) {
        Component labelText = Component.literal(label + ": ");
        int left = leftPos + x;
        int top = topPos + y;
        g.drawString(font, labelText, left, top, TEXT_MUTED, false);
        g.drawString(font, Component.literal(value), left + font.width(labelText), top, valueColor, false);
    }

    private void drawRightLocalString(GuiGraphics g, Component text, int rightX, int y, int color) {
        g.drawString(font, text, leftPos + rightX - font.width(text), topPos + y, color, false);
    }

    private static IGuiTexture buttonTexture() {
        return new GuiTextureGroup(new ColorRectAndBorderTexture(0xFFF1F3F6, 0xFF8A96A8, 1.0F));
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static int threadBarMax(NECraftingUiState state) {
        return Math.max(state.availableThreads(), Math.max(state.threadCount(), state.runningThreadCount()));
    }

    private static int visibleWorkerSlots(NECraftingUiState state) {
        int slots = Math.max(state.workerCount(), state.workerCraftOutputs().size());
        return Math.min(MAX_VISIBLE_WORKER_SLOTS, Math.max(0, slots));
    }

    private static int workerSlotsStartX(int visibleSlots) {
        int totalW = visibleSlots * WORKER_SLOT_SIZE + (visibleSlots - 1) * WORKER_SLOT_GAP;
        return WORKER_PANEL_X + (WORKER_PANEL_W - totalW) / 2;
    }

    private static ItemStack workerOutputAt(NECraftingUiState state, int index) {
        if (index < 0 || index >= state.workerCraftOutputs().size()) {
            return ItemStack.EMPTY;
        }
        return state.workerCraftOutputs().get(index);
    }

    private static String workerHeader(NECraftingUiState state) {
        if (state.workerCount() > MAX_VISIBLE_WORKER_SLOTS) {
            return "showing " + MAX_VISIBLE_WORKER_SLOTS + " / " + fmt(state.workerCount());
        }
        return fmt(state.workerCount()) + " workers";
    }

    private static String parallelTierSummary(NECraftingUiState state) {
        return "Parallel tiers: FT4 x" + countTier(state, 1) + "   FT6 x" + countTier(state, 2) + "   FT9 x"
                + countTier(state, 3);
    }

    private static int countTier(NECraftingUiState state, int tier) {
        int count = 0;
        for (int value : state.parallelCoreTiers()) {
            if (value == tier) {
                count++;
            }
        }
        return count;
    }

    private static int parallelPerCore(int tier, boolean overclocked) {
        return switch (tier) {
            case 3 -> overclocked ? 384 : 256;
            case 2 -> overclocked ? 96 : 72;
            default -> overclocked ? 32 : 24;
        };
    }

    private static int energyColor(long energyUsage) {
        double ratio = percent(energyUsage, MAX_ENERGY_USAGE);
        if (ratio >= 0.9D) {
            return TEXT_ERROR;
        }
        if (ratio >= 0.5D) {
            return TEXT_WARNING;
        }
        return TEXT_SUCCESS;
    }

    private void renderSimpleTooltip(GuiGraphics g, int mouseX, int mouseY, String title, String value) {
        g.renderTooltip(
                font, List.of(Component.literal(title), Component.literal(value)), Optional.empty(), mouseX, mouseY);
    }

    public NECraftingControllerMenu getMenu() {
        return menu;
    }
}
