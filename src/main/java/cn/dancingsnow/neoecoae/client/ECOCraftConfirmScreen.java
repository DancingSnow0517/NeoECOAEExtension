package cn.dancingsnow.neoecoae.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
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
import cn.dancingsnow.neoecoae.client.craftinggraph.ECOCraftingGraphScreen;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.util.NEByteFormatter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** ECO-owned crafting report. Server menu and job execution remain AE2-native. */
public final class ECOCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
    // GTLCore rewrites every style path containing "craft_confirm.json".
    public static final String STYLE_PATH = "/screens/eco_planner_report.json";

    private static final int AE2_TEXT_DARK = 0x403E53;
    private static final int CYCLE_STATUS_X = 237;
    private static final int CYCLE_STATUS_Y = 7;
    private static final long GIGA_BYTE = 1_000_000_000L;
    private static final MathContext TIME_PRECISION = new MathContext(5, RoundingMode.HALF_UP);
    private static final long LARGE_CYCLE_INDICATOR_DELAY_NANOS = 500_000_000L;
    private static final int SOLVE_PROGRESS_X = 28;
    private static final int SOLVE_PROGRESS_Y = 21;
    private static final int SOLVE_PROGRESS_WIDTH = 140;

    private final ECOCraftConfirmTableRenderer table;
    private final ECOExactMaterialTableRenderer exactTable;
    private final ECOCycleItemListRenderer cycleItems;
    private final Button start;
    private final Button selectCPU;
    private final Button graph;
    private final Scrollbar scrollbar;
    private final Scrollbar cycleScrollbar;
    private @Nullable Integer selectedCycleComponentId;
    private @Nullable CraftingGraphSnapshot materialSnapshot;
    private @Nullable PlanningStatus materialStatus;
    private boolean materialHasCycleItems;
    private boolean useExactMaterialTable;
    private List<CraftingGraphSnapshot.MaterialNode> exactMaterials = List.of();
    private final long openedNanos = System.nanoTime();

    public ECOCraftConfirmScreen(CraftConfirmMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        table = new ECOCraftConfirmTableRenderer(this, 9, 27, this::isCycleParticipant);
        exactTable = new ECOExactMaterialTableRenderer(this, 9, 27, this::isCycleParticipant);
        cycleItems = new ECOCycleItemListRenderer(this, 237, 27);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.DEFAULT);
        cycleScrollbar = widgets.addScrollBar("cycleScrollbar", Scrollbar.DEFAULT);
        start = widgets.addButton("start", GuiText.Start.text(), this::start);
        start.active = false;
        selectCPU = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        selectCPU.active = false;
        widgets.addButton("cancel", GuiText.Cancel.text(), menu::goBack);
        graph = addToLeftToolbar(new CraftingGraphButton(this::openGraph));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        selectCPU.setMessage(getNextCpuButtonLabel());
        CraftingPlanSummary plan = menu.getPlan();
        boolean blockedUnrepresentable = isBlockedUnrepresentablePlan();
        boolean missingCraftAvailable =
                (Object) menu instanceof ECOCraftConfirmMenuMode mode && mode.neoecoae$isMissingCraftAvailable();
        boolean startable = plan != null && (!plan.isSimulation() || missingCraftAvailable) && !blockedUnrepresentable;
        start.active = !menu.hasNoCPU() && startable;
        selectCPU.active = startable;

        Component cpuDetails = Component.empty();
        Component planSummary = Component.translatable("gui.neoecoae.crafting_report.calculating")
                .withStyle(style -> style.withColor(AE2_TEXT_DARK));
        if (plan == null && showLargeCycleIndicator()) {
            planSummary = Component.translatable("gui.neoecoae.crafting_report.solving_large_cycle")
                    .withStyle(style -> style.withColor(AE2_TEXT_DARK));
        }
        if (plan != null) {
            BigInteger exactUsedBytes = BigInteger.valueOf(Math.max(0L, plan.getUsedBytes()));
            if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
                exactUsedBytes = HostText.craftingPlanBytes(mode.neoecoae$getTheoreticalBytes(), plan.getUsedBytes());
                String usedBytes = HostText.ae2Amount(exactUsedBytes);
                long calculationNanos = mode.neoecoae$getCalculationNanos();
                if (calculationNanos < 1_000_000L) {
                    var byteSummary = Component.translatable(
                                    "gui.neoecoae.crafting_report.bytes_only",
                                    HostText.expandedStorageBytes(exactUsedBytes))
                            .withStyle(style -> style.withColor(AE2_TEXT_DARK));
                    if (exactUsedBytes.compareTo(BigInteger.valueOf(GIGA_BYTE)) >= 0) {
                        byteSummary.append(Component.literal(" (" + usedBytes + " B)"));
                    }
                    planSummary = byteSummary;
                } else {
                    planSummary = Component.literal(formatMillis(calculationNanos) + " ms")
                            .append(Component.translatable("gui.neoecoae.crafting_report.bytes", usedBytes))
                            .withStyle(style -> style.withColor(AE2_TEXT_DARK));
                }
            } else {
                String usedBytes = ReadableNumberConverter.format(plan.getUsedBytes(), 4);
                planSummary = Component.translatable("gui.neoecoae.crafting_report.bytes_only", usedBytes)
                        .withStyle(style -> style.withColor(AE2_TEXT_DARK));
            }
            if (plan.isSimulation()) {
                cpuDetails = GuiText.PartialPlan.text();
            } else if (menu.getCpuAvailableBytes() > 0) {
                cpuDetails = GuiText.ConfirmCraftCpuStatus.text(
                        Component.literal(NEByteFormatter.formatCpuStorage(menu.getCpuAvailableBytes())),
                        Component.literal(NEByteFormatter.formatCpuCoProcessors(menu.getCpuCoProcessors())));
            } else {
                cpuDetails = GuiText.ConfirmCraftNoCpu.text();
            }
        }
        if (blockedUnrepresentable) {
            String unrepresentableBytes = (Object) menu instanceof ECOCraftConfirmMenuMode mode
                    ? HostText.ae2Amount(mode.neoecoae$getTheoreticalBytes())
                    : ReadableNumberConverter.format(plan.getUsedBytes(), 4);
            planSummary = Component.translatable("gui.neoecoae.crafting_report.bytes_only", unrepresentableBytes)
                    .withStyle(style -> style.withColor(AE2_TEXT_DARK))
                    .append(Component.literal("（数量超出范围）").withStyle(style -> style.withColor(0xFFAA3333)));
            cpuDetails = Component.literal("开始按钮已禁用；请查看材料列表或合成图").withStyle(style -> style.withColor(AE2_TEXT_DARK));
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, Component.empty());
        setTextContent("plan_summary", planSummary);
        setTextContent("cycle_status", Component.empty());
        setTextContent("cpu_status", cpuDetails);
        int size = shouldUseExactMaterialTable()
                ? exactMaterials.size()
                : plan != null ? plan.getEntries().size() : 0;
        scrollbar.setRange(0, table.getScrollableRows(size), 1);
        int cycleItemCount = (Object) menu instanceof ECOCraftConfirmMenuMode mode
                ? mode.neoecoae$getCycleItems().size()
                : 0;
        cycleScrollbar.setRange(0, cycleItems.getScrollableRows(cycleItemCount), 1);
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode
                && selectedCycleComponentId != null
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

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            cycleItems.render(
                    graphics,
                    mouseX,
                    mouseY,
                    mode.neoecoae$getCycleItems(),
                    cycleScrollbar.getCurrentScroll(),
                    selectedCycleComponentId);
        }

        CraftingPlanSummary plan = menu.getPlan();
        if (plan == null && showLargeCycleIndicator()) {
            long elapsedMillis = (System.nanoTime() - openedNanos) / 1_000_000L;
            int fill = 1 + (int) (elapsedMillis % 2_000L) * (SOLVE_PROGRESS_WIDTH - 2) / 1_999;
            graphics.fill(
                    SOLVE_PROGRESS_X,
                    SOLVE_PROGRESS_Y,
                    SOLVE_PROGRESS_X + SOLVE_PROGRESS_WIDTH,
                    SOLVE_PROGRESS_Y + 4,
                    0xFF6A6A72);
            graphics.fill(
                    SOLVE_PROGRESS_X + 1,
                    SOLVE_PROGRESS_Y + 1,
                    SOLVE_PROGRESS_X + 1 + fill,
                    SOLVE_PROGRESS_Y + 3,
                    0xFF3D9B62);
        }
        if (shouldUseExactMaterialTable()) {
            exactTable.render(graphics, mouseX, mouseY, exactMaterials, scrollbar.getCurrentScroll());
        } else if (plan != null)
            table.render(graphics, mouseX, mouseY, plan.getEntries(), scrollbar.getCurrentScroll());

        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            drawCyclePlanningStatus(
                    graphics,
                    mode.neoecoae$isCyclePlanningEnabled(),
                    !mode.neoecoae$getCraftingGraphSnapshot().cycleGroups().isEmpty());
        }

        cycleItems.renderTooltip(graphics);
    }

    private boolean showLargeCycleIndicator() {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
                && mode.neoecoae$isCyclePlanningEnabled()
                && System.nanoTime() - openedNanos >= LARGE_CYCLE_INDICATOR_DELAY_NANOS;
    }

    private void drawCyclePlanningStatus(GuiGraphics graphics, boolean enabled, boolean cycleDetected) {
        var texture = AppEng.makeId("textures/guis/states.png");
        int sourceX = cycleDetected && enabled ? 16 : 32;
        // states.png is a 16px cell atlas; the requested icons are row 16, columns 2/3.
        graphics.blit(texture, CYCLE_STATUS_X, CYCLE_STATUS_Y, 0, sourceX, 15 * 16, 16, 16, 256, 256);
        String labelKey = !cycleDetected
                ? "gui.neoecoae.crafting_report.cycle_not_detected"
                : enabled
                        ? "gui.neoecoae.crafting_report.cycle_planning_enabled"
                        : "gui.neoecoae.crafting_report.cycle_planning_disabled";
        Component label = Component.translatable(labelKey).withStyle(style -> style.withColor(AE2_TEXT_DARK));
        graphics.drawString(font, label, CYCLE_STATUS_X + 18, CYCLE_STATUS_Y + 4, AE2_TEXT_DARK, false);
    }

    @Override
    @Nullable public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = cycleItems.getHoveredStack();
        if (hovered == null)
            hovered = shouldUseExactMaterialTable() ? exactTable.getHoveredStack() : table.getHoveredStack();
        return hovered != null ? hovered : super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            ECOCycleItemList.Entry entry = cycleItems.entryAt(
                    mouseX, mouseY, mode.neoecoae$getCycleItems(), cycleScrollbar.getCurrentScroll());
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            start();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectNextCpu() {
        menu.cycleSelectedCPU(!isHandlingRightClick());
    }

    private void start() {
        if (isBlockedUnrepresentablePlan()) return;
        menu.startJob();
    }

    private void openGraph() {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) return;
        CraftingGraphSnapshot snapshot = mode.neoecoae$getCraftingGraphSnapshot();
        if (snapshot.rootNodeId() < 0 && snapshot.cycleGroups().isEmpty()) return;

        Integer initialCycle = selectedCycleComponentId;
        if (initialCycle == null && snapshot.cycleGroups().size() == 1) {
            initialCycle = snapshot.cycleGroups().get(0).componentId();
        }
        @Nullable appeng.api.stacks.AEKey focusedMaterial = null;
        var items = mode.neoecoae$getCycleItems();
        if (initialCycle != null) {
            Integer cycleToFocus = initialCycle;
            focusedMaterial = items.stream()
                    .filter(entry -> entry.componentId() == cycleToFocus)
                    .findFirst()
                    .map(ECOCycleItemList.Entry::what)
                    .orElse(null);
        }
        minecraft.setScreen(new ECOCraftingGraphScreen(this, snapshot, initialCycle, focusedMaterial));
    }

    private boolean hasUnrepresentableDiagnostic() {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
                && mode.neoecoae$getPlanningStatus() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
    }

    private boolean isBlockedUnrepresentablePlan() {
        CraftingPlanSummary plan = menu.getPlan();
        return hasUnrepresentableDiagnostic() && (plan == null || plan.isSimulation());
    }

    private boolean shouldUseExactMaterialTable() {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) return false;
        CraftingGraphSnapshot snapshot = mode.neoecoae$getCraftingGraphSnapshot();
        PlanningStatus status = mode.neoecoae$getPlanningStatus();
        boolean hasCycleItems = !mode.neoecoae$getCycleItems().isEmpty();
        // The snapshot is immutable. Parse and sort its exact amounts only when synchronized data changes.
        if (snapshot != materialSnapshot || status != materialStatus || hasCycleItems != materialHasCycleItems) {
            materialSnapshot = snapshot;
            materialStatus = status;
            materialHasCycleItems = hasCycleItems;
            useExactMaterialTable =
                    ECOExactMaterialTableRenderer.shouldUseExactMaterials(status, snapshot.nodes(), hasCycleItems);
            exactMaterials =
                    useExactMaterialTable ? ECOExactMaterialTableRenderer.sortMaterials(snapshot.nodes()) : List.of();
        }
        return useExactMaterialTable;
    }

    private boolean isCycleParticipant(AEKey key) {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
                && mode.neoecoae$getCycleItems().stream()
                        .anyMatch(entry -> entry.what().equals(key));
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

    public static @Nullable Integer resolveInitialCycle(
            List<ECOCycleItemList.Entry> items, @Nullable Integer selectedComponentId) {
        if (selectedComponentId != null) return selectedComponentId;
        var componentIds = items.stream()
                .map(ECOCycleItemList.Entry::componentId)
                .filter(id -> id >= 0)
                .distinct()
                .toList();
        if (componentIds.size() == 1) return componentIds.get(0);
        return null;
    }
}
