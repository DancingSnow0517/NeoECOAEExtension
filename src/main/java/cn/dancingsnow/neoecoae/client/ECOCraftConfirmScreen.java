package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.util.ReadableNumberConverter;
import cn.dancingsnow.neoecoae.api.me.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.ECOCycleItemList;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.client.craftinggraph.ECOCraftingGraphScreen;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** ECO-owned crafting report. Server menu and job execution remain AE2-native. */
public final class ECOCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
    private static final int AE2_TEXT_DARK = 0x403E53;
    private static final long GIGA_BYTE = 1_000_000_000L;
    private static final MathContext TIME_PRECISION = new MathContext(5, RoundingMode.HALF_UP);

    private final ECOCraftConfirmTableRenderer table;
    private final ECOCycleItemListRenderer cycleItems;
    private final Button start;
    private final Button selectCPU;
    private final Button graph;
    private final Scrollbar scrollbar;
    private final Scrollbar cycleScrollbar;
    private @Nullable Integer selectedCycleComponentId;

    public ECOCraftConfirmScreen(CraftConfirmMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        table = new ECOCraftConfirmTableRenderer(this, 9, 27);
        cycleItems = new ECOCycleItemListRenderer(this, 237, 27);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        cycleScrollbar = widgets.addScrollBar("cycleScrollbar", Scrollbar.BIG);
        start = widgets.addButton("start", GuiText.Start.text(), this::start);
        start.active = false;
        selectCPU = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        selectCPU.active = false;
        widgets.addButton("cancel", GuiText.Cancel.text(), menu::goBack);
        graph = widgets.addButton("graph", Component.translatable("gui.neoecoae.crafting_graph.open"), this::openGraph);
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        var errorResult = menu.submitError.result();

        selectCPU.setMessage(getNextCpuButtonLabel());
        CraftingPlanSummary plan = menu.getPlan();
        boolean startable = plan != null && !plan.isSimulation();
        start.active = !menu.hasNoCPU() && startable;
        selectCPU.active = startable;

        Component cpuDetails = Component.empty();
        Component planSummary = Component.translatable("gui.neoecoae.crafting_report.calculating")
            .withColor(AE2_TEXT_DARK);
        Component cycleStatus = Component.empty();
        if (errorResult != null && errorResult.errorCode() != null) {
            cycleStatus = Component.literal(errorResult.errorCode().name());
        }
        if (plan != null) {
            String usedBytes = ReadableNumberConverter.format(plan.getUsedBytes(), 4);
            if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
                long calculationNanos = mode.neoecoae$getCalculationNanos();
                if (calculationNanos < 1_000_000L) {
                    var byteSummary = Component.translatable(
                        "gui.neoecoae.crafting_report.bytes_only", NumberFormat.getInstance().format(plan.getUsedBytes()))
                        .withColor(AE2_TEXT_DARK);
                    if (plan.getUsedBytes() >= GIGA_BYTE) {
                        byteSummary.append(Component.literal(" (" + usedBytes + " B)"));
                    }
                    planSummary = byteSummary;
                } else {
                    planSummary = Component.literal(formatMillis(calculationNanos) + " ms")
                        .append(Component.translatable("gui.neoecoae.crafting_report.bytes", usedBytes))
                        .withColor(AE2_TEXT_DARK);
                }
                if (mode.neoecoae$getPlanningStatus() == PlanningStatus.CYCLE_UNSUPPORTED
                        || mode.neoecoae$getPlanningStatus() == PlanningStatus.CYCLE_UNRESOLVED
                        || mode.neoecoae$getPlanningStatus() == PlanningStatus.PARTIAL) {
                    cycleStatus = Component.translatable("gui.neoecoae.crafting_report.cycle_unsupported")
                        .withColor(AE2_TEXT_DARK);
                }
            } else {
                planSummary = Component.translatable("gui.neoecoae.crafting_report.bytes_only", usedBytes)
                    .withColor(AE2_TEXT_DARK);
            }
            if (plan.isSimulation()) {
                cpuDetails = GuiText.PartialPlan.text();
            } else if (menu.getCpuAvailableBytes() > 0) {
                cpuDetails = GuiText.ConfirmCraftCpuStatus.text(menu.getCpuAvailableBytes(), menu.getCpuCoProcessors());
            } else {
                cpuDetails = GuiText.ConfirmCraftNoCpu.text();
            }
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, Component.empty());
        setTextContent("plan_summary", planSummary);
        setTextContent("cycle_status", cycleStatus);
        setTextContent("cpu_status", cpuDetails);
        int size = plan != null ? plan.getEntries().size() : 0;
        scrollbar.setRange(0, table.getScrollableRows(size), 1);
        int cycleItemCount = (Object) menu instanceof ECOCraftConfirmMenuMode mode
            ? mode.neoecoae$getCycleItems().size() : 0;
        cycleScrollbar.setRange(0, cycleItems.getScrollableRows(cycleItemCount), 1);
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode && selectedCycleComponentId != null
                && mode.neoecoae$getCycleItems().stream()
                    .noneMatch(entry -> entry.componentId() == selectedCycleComponentId)) {
            selectedCycleComponentId = null;
        }
        graph.active = (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && (!mode.neoecoae$getCraftingGraphSnapshot().cycleGroups().isEmpty()
                || mode.neoecoae$getCraftingGraphSnapshot().rootNodeId() >= 0);
    }

    private static String formatMillis(long nanos) {
        if (nanos == 0) {
            return "0.0000";
        }
        BigDecimal millis = BigDecimal.valueOf(nanos, 6).round(TIME_PRECISION);
        int integerDigits = millis.precision() - millis.scale();
        int displayScale = Math.max(0, TIME_PRECISION.getPrecision() - integerDigits);
        return millis.setScale(displayScale, RoundingMode.HALF_UP).toPlainString();
    }

    private Component getNextCpuButtonLabel() {
        if (menu.hasNoCPU()) return GuiText.NoCraftingCPUs.text();
        Component cpuName = menu.cpuName == null ? GuiText.Automatic.text() : menu.cpuName;
        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            cycleItems.render(graphics, mouseX, mouseY, mode.neoecoae$getCycleItems(),
                cycleScrollbar.getCurrentScroll(), selectedCycleComponentId);
        }

        CraftingPlanSummary plan = menu.getPlan();
        if (plan != null) table.render(graphics, mouseX, mouseY, plan.getEntries(), scrollbar.getCurrentScroll());

        cycleItems.renderTooltip(graphics);
    }

    @Override @Nullable public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = cycleItems.getHoveredStack();
        if (hovered == null) hovered = table.getHoveredStack();
        return hovered != null ? hovered : super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            ECOCycleItemList.Entry entry = cycleItems.entryAt(mouseX, mouseY, mode.neoecoae$getCycleItems(),
                cycleScrollbar.getCurrentScroll());
            if (entry != null) {
                int componentId = entry.componentId();
                if (componentId < 0 || selectedCycleComponentId != null && componentId == selectedCycleComponentId) {
                    selectedCycleComponentId = null;
                } else {
                    selectedCycleComponentId = componentId;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            start();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectNextCpu() { menu.cycleSelectedCPU(!isHandlingRightClick()); }
    private void start() { menu.startJob(); }

    private void openGraph() {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) return;
        CraftingGraphSnapshot snapshot = mode.neoecoae$getCraftingGraphSnapshot();
        if (snapshot.rootNodeId() < 0 && snapshot.cycleGroups().isEmpty()) return;

        Integer initialCycle = selectedCycleComponentId;
        if (initialCycle == null && snapshot.cycleGroups().size() == 1) {
            initialCycle = snapshot.cycleGroups().getFirst().componentId();
        }
        @Nullable appeng.api.stacks.AEKey focusedMaterial = null;
        var items = mode.neoecoae$getCycleItems();
        if (initialCycle != null) {
            Integer cycleToFocus = initialCycle;
            focusedMaterial = items.stream().filter(entry -> entry.componentId() == cycleToFocus).findFirst()
                .map(ECOCycleItemList.Entry::what).orElse(null);
        }
        minecraft.setScreen(new ECOCraftingGraphScreen(this, snapshot, initialCycle, focusedMaterial));
    }

    public static @Nullable Integer resolveInitialCycle(List<ECOCycleItemList.Entry> items,
            @Nullable Integer selectedComponentId) {
        if (selectedComponentId != null) return selectedComponentId;
        var componentIds = items.stream().map(ECOCycleItemList.Entry::componentId).filter(id -> id >= 0).distinct().toList();
        if (componentIds.size() == 1) return componentIds.getFirst();
        return null;
    }

}
