package cn.dancingsnow.neoecoae.client;

import appeng.client.gui.AEBaseScreen;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.Icon;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.util.ReadableNumberConverter;
import cn.dancingsnow.neoecoae.api.me.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.ECOCycleItemList;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.client.craftinggraph.ECOCraftingGraphScreen;
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
    private static final int CYCLE_STATUS_X = 237;
    private static final int CYCLE_STATUS_Y = 7;
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
        graph = new CraftingGraphButton(this::openGraph);
        widgets.add("graph", graph);
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        selectCPU.setMessage(getNextCpuButtonLabel());
        CraftingPlanSummary plan = menu.getPlan();
        boolean unrepresentable = isUnrepresentablePlan();
        boolean startable = plan != null && !plan.isSimulation() && !unrepresentable;
        start.active = !menu.hasNoCPU() && startable;
        selectCPU.active = startable;

        Component cpuDetails = Component.empty();
        Component planSummary = Component.translatable("gui.neoecoae.crafting_report.calculating")
            .withColor(AE2_TEXT_DARK);
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
        if (unrepresentable) {
            planSummary = Component.literal("理论计划（不可执行：数量超过 AE2 long 范围）")
                .withColor(0xFFAA3333);
            cpuDetails = Component.literal("开始按钮已禁用；请查看材料列表或合成图")
                .withColor(AE2_TEXT_DARK);
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, Component.empty());
        setTextContent("plan_summary", planSummary);
        setTextContent("cycle_status", Component.empty());
        setTextContent("cpu_status", cpuDetails);
        int size = unrepresentable ? bigIntegerMaterials().size() : plan != null ? plan.getEntries().size() : 0;
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
        if (isUnrepresentablePlan()) renderBigIntegerMaterials(graphics, scrollbar.getCurrentScroll());
        else if (plan != null) table.render(graphics, mouseX, mouseY, plan.getEntries(), scrollbar.getCurrentScroll());

        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            drawCyclePlanningStatus(graphics, mode.neoecoae$isCyclePlanningEnabled());
        }

        cycleItems.renderTooltip(graphics);
    }

    private void drawCyclePlanningStatus(GuiGraphics graphics, boolean enabled) {
        var texture = AppEng.makeId("textures/guis/states.png");
        int sourceX = enabled ? 16 : 32;
        // states.png is a 16px cell atlas; the requested icons are row 16, columns 2/3.
        graphics.blit(texture, CYCLE_STATUS_X, CYCLE_STATUS_Y,
            0, sourceX, 15 * 16, 16, 16, 256, 256);
        Component label = Component.translatable(enabled
                ? "gui.neoecoae.crafting_report.cycle_planning_enabled"
                : "gui.neoecoae.crafting_report.cycle_planning_disabled")
            .withColor(AE2_TEXT_DARK);
        graphics.drawString(font, label, CYCLE_STATUS_X + 18, CYCLE_STATUS_Y + 4,
            AE2_TEXT_DARK, false);
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
    private void start() {
        if (isUnrepresentablePlan()) return;
        menu.startJob();
    }

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

    private List<CraftingGraphSnapshot.MaterialNode> bigIntegerMaterials() {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) return List.of();
        return mode.neoecoae$getCraftingGraphSnapshot().nodes().stream()
            .filter(node -> node.requestedBigInteger().signum() > 0 || node.missingBigInteger().signum() > 0)
            .toList();
    }

    private boolean isUnrepresentablePlan() {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$getPlanningStatus() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
    }

    /** Renders the exact planner report when AE2 cannot construct its long-valued native plan. */
    private void renderBigIntegerMaterials(GuiGraphics graphics, int scroll) {
        List<CraftingGraphSnapshot.MaterialNode> materials = bigIntegerMaterials();
        int left = 9;
        int top = 27;
        int rowHeight = 18;
        int first = Math.max(0, scroll);
        int visible = 7;
        for (int index = first; index < Math.min(materials.size(), first + visible); index++) {
            var material = materials.get(index);
            int y = top + (index - first) * rowHeight;
            AEKeyRendering.drawInGui(minecraft, graphics, left + 2, y + 1, material.key());
            graphics.drawString(font, material.key().getDisplayName(), left + 22, y + 2, AE2_TEXT_DARK, false);
            String amount = "req " + material.exactRequested() + "  miss " + material.exactMissing();
            graphics.drawString(font, Component.literal(amount), left + 22, y + 10,
                material.missingBigInteger().signum() > 0 ? 0xFFAA3333 : AE2_TEXT_DARK, false);
        }
    }

    private static final class CraftingGraphButton extends IconButton {
        private CraftingGraphButton(Runnable onPress) {
            super(ignored -> onPress.run());
            setMessage(Component.translatable("gui.neoecoae.crafting_graph.open"));
        }

        @Override
        protected Icon getIcon() {
            return Icon.CRAFT_HAMMER;
        }
    }

    public static @Nullable Integer resolveInitialCycle(List<ECOCycleItemList.Entry> items,
            @Nullable Integer selectedComponentId) {
        if (selectedComponentId != null) return selectedComponentId;
        var componentIds = items.stream().map(ECOCycleItemList.Entry::componentId).filter(id -> id >= 0).distinct().toList();
        if (componentIds.size() == 1) return componentIds.getFirst();
        return null;
    }

}
