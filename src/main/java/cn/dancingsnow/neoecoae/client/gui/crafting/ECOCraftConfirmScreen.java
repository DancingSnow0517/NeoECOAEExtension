package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.client.gui.graph.ECOCraftingGraphScreen;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.integration.jei.JeiBookmarkAccess;
import cn.dancingsnow.neoecoae.network.ECOForceCraftStartFlagC2SPacket;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** ECO-owned crafting report. Server menu and job execution remain AE2-native. */
public final class ECOCraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> {
    private static final int AE2_TEXT_DARK = 0x20212B;
    private static final ResourceLocation CRAFTING_WIDGETS_TEXTURE =
        NeoECOAE.id("textures/guis/crafting_widgets.png");
    private static final int CPU_TIER_TARGET_X = 38;
    private static final int CPU_TIER_TARGET_Y = 5;
    private static final int CPU_TIER_GLYPH_WIDTH = 13;
    private static final int CPU_TIER_GLYPH_HEIGHT = 18;
    private static final int CPU_TIER_GLYPH_V = 0;
    private static final int CYCLE_STATUS_X = 237;
    private static final int CYCLE_STATUS_Y = 7;
    private static final long LARGE_CYCLE_INDICATOR_DELAY_NANOS = 500_000_000L;

    private final ECOCraftConfirmTableRenderer table;
    private final ECOExactMaterialTableRenderer exactTable;
    private final ECOCycleItemListRenderer cycleItems;
    private final Button start;
    private final Button selectCPU;
    private final Button graph;
    private final Button bookmarkMissing;
    private final Scrollbar scrollbar;
    private final Scrollbar cycleScrollbar;
    private @Nullable Integer selectedCycleComponentId;
    private final long openedNanos = System.nanoTime();
    private boolean lastSubmissionForced;
    private String lastStartGate;
    private ECOCraftReportHeaderRenderer.Summary reportSummary = ECOCraftReportHeaderRenderer.pending(
        Component.translatable("gui.neoecoae.crafting_report.calculating"));

    public ECOCraftConfirmScreen(CraftConfirmMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        table = new ECOCraftConfirmTableRenderer(this, 9, 27, this::isCycleParticipant,
            this::isFuzzyPlanningItem);
        exactTable = new ECOExactMaterialTableRenderer(this, 9, 27, this::isCycleParticipant,
            this::isMissingStartupSeed, this::isEstimatedStartupSeed, this::isFuzzyPlanningItem);
        cycleItems = new ECOCycleItemListRenderer(this, 237, 27);
        scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        cycleScrollbar = widgets.addScrollBar("cycleScrollbar", Scrollbar.BIG);
        start = widgets.addButton("start", GuiText.Start.text(), this::start);
        start.active = false;
        selectCPU = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        selectCPU.active = false;
        widgets.addButton("cancel", GuiText.Cancel.text(), menu::goBack);
        bookmarkMissing = widgets.addButton("bookmarkMissing",
            Component.translatable("gui.neoecoae.crafting_report.bookmark_missing"), this::bookmarkMissing);
        bookmarkMissing.active = false;
        graph = addToLeftToolbar(new CraftingGraphButton(this::openGraph));
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();

        var errorResult = menu.submitError.result();
        if (errorResult != null && errorResult.errorCode() != null) {
            switchToScreen(new ECOCraftErrorScreen(
                this,
                errorResult.errorCode(),
                errorResult.errorDetail()));
            return;
        }

        selectCPU.setMessage(getNextCpuButtonLabel());
        CraftingPlanSummary plan = menu.getPlan();
        boolean unrepresentable = isUnrepresentablePlan();
        boolean forceStart = Screen.hasShiftDown() && plan != null && plan.isSimulation();
        boolean bigOrder = usesBigOrderSubmission(forceStart);
        boolean allowed = !((Object) menu instanceof ECOCraftConfirmMenuMode mode)
            || mode.neoecoae$getPlanningStatus() == null
            || mode.neoecoae$getPlanningStatus() == PlanningStatus.SUCCESS
            || mode.neoecoae$getPlanningStatus() == PlanningStatus.MISSING_ITEMS || unrepresentable;
        boolean startable = plan != null && (!plan.isSimulation() || allowed && (forceStart || bigOrder));
        start.active = startable && (bigOrder
            ? ((ECOCraftConfirmMenuMode) (Object) menu).neoecoae$bigOrderCpuAvailable() : !menu.hasNoCPU());
        selectCPU.active = startable || forceStart;
        start.setMessage(forceStart
            ? Component.translatable("gui.neoecoae.force_start") : GuiText.Start.text());
        start.setTooltip(bigOrder && !((ECOCraftConfirmMenuMode) (Object) menu).neoecoae$bigOrderCpuAvailable()
            ? net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("tooltip.neoecoae.big_order.requires_cpu"))
            : forceStart
                ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("tooltip.neoecoae.force_start"))
                : null);

        Component cpuDetails = Component.empty();
        boolean unknownBytes = false;
        if (plan != null) {
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
        if (unrepresentable) {
            cpuDetails = Component.translatable(((ECOCraftConfirmMenuMode) (Object) menu).neoecoae$bigOrderCpuAvailable()
                ? "gui.neoecoae.big_order.single_task" : "gui.neoecoae.big_order.requires_cpu")
                .withColor(AE2_TEXT_DARK);
        }

        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode
                && mode.neoecoae$getPlanningStatus() == PlanningStatus.CYCLE_UNRESOLVED) {
            boolean retainedMaterials = !mode.neoecoae$getCraftingGraphSnapshot().nodes().isEmpty()
                || plan != null && !plan.getEntries().isEmpty();
            boolean seedEstimate = mode.neoecoae$getCycleItems().stream()
                .anyMatch(ECOCycleItemList.Entry::seedShortfallIsEstimate);
            cpuDetails = Component.translatable(seedEstimate
                    ? "gui.neoecoae.crafting_report.seed_requirement_unknown" : retainedMaterials
                    ? "gui.neoecoae.crafting_report.search_incomplete"
                    : "gui.neoecoae.crafting_report.planning_failed")
                .withColor(0x604100);
            selectCPU.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(mode.neoecoae$getPlanningDiagnostic())));
            if (plan != null && !retainedMaterials) {
                unknownBytes = true;
            }
        } else if (isDiagnosticShell()) {
            var mode = (ECOCraftConfirmMenuMode) (Object) menu;
            cpuDetails = Component.translatable("gui.neoecoae.crafting_report.planning_failed")
                .withColor(0x752121);
            unknownBytes = true;
            selectCPU.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(mode.neoecoae$getPlanningStatus() + "\n" + mode.neoecoae$getPlanningDiagnostic())));
        } else {
            selectCPU.setTooltip(null);
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, Component.empty());
        setTextContent("complex_tree_hint", (Object) menu instanceof ECOCraftConfirmMenuMode mode
                && mode.neoecoae$getPlanningStatus() == PlanningStatus.CYCLE_UNRESOLVED
            ? Component.translatable(mode.neoecoae$getCycleItems().stream()
                    .anyMatch(ECOCycleItemList.Entry::seedShortfallIsEstimate)
                    ? "gui.neoecoae.crafting_report.seed_requirement_unknown"
                    : "gui.neoecoae.crafting_report.complex_tree_hint").withColor(0xFFCC66)
            : Component.empty());
        setTextContent("cycle_status", Component.empty());
        setTextContent("cpu_status", cpuDetails);
        boolean exactMaterialTable = shouldUseExactMaterialTable();
        int size = exactMaterialTable ? exactMaterials().size()
            : plan != null ? plan.getEntries().size() : 0;
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
        bookmarkMissing.active = JeiBookmarkAccess.isAvailable()
            && exactMaterials().stream().anyMatch(node -> node.missingBigInteger().signum() > 0);
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode && !mode.neoecoae$diagnosticsReady()) {
            start.active = false;
            graph.active = false;
        }
        updateReportSummary(plan, unknownBytes);
        String startGate = start.active ? "READY"
            : plan == null ? "WAITING_FOR_PLAN"
            : (Object) menu instanceof ECOCraftConfirmMenuMode mode && !mode.neoecoae$diagnosticsReady()
                ? "WAITING_FOR_DIAGNOSTICS"
            : !startable ? "PLAN_NOT_STARTABLE"
            : bigOrder ? "NO_ELIGIBLE_ECO_CPU" : "NO_MATCHING_CPU";
        if (!startGate.equals(lastStartGate)) {
            lastStartGate = startGate;
            org.slf4j.LoggerFactory.getLogger("neoecoae").info(
                "[craft-confirm] Client start gate: container={}, reason={}, status={}, simulation={}, "
                    + "noCPU={}, bigOrder={}, forced={}",
                menu.containerId, startGate,
                (Object) menu instanceof ECOCraftConfirmMenuMode mode ? mode.neoecoae$getPlanningStatus() : null,
                plan == null ? null : plan.isSimulation(), menu.hasNoCPU(), bigOrder, forceStart);
        }
    }

    private void updateReportSummary(@Nullable CraftingPlanSummary plan, boolean unknownBytes) {
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode && !mode.neoecoae$diagnosticsReady()) {
            reportSummary = ECOCraftReportHeaderRenderer.pending(
                Component.translatable("gui.neoecoae.crafting_report.loading_details"));
        } else if (plan == null) {
            reportSummary = ECOCraftReportHeaderRenderer.pending(
                Component.translatable("gui.neoecoae.crafting_report.calculating"));
        } else {
            BigInteger usedBytes = BigInteger.valueOf(Math.max(0L, plan.getUsedBytes()));
            long nanos = 0L;
            if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
                nanos = mode.neoecoae$getCalculationNanos();
                if (mode.neoecoae$getTheoreticalBytes().signum() > 0) {
                    usedBytes = mode.neoecoae$getTheoreticalBytes();
                }
            }
            reportSummary = ECOCraftReportHeaderRenderer.completed(nanos, unknownBytes ? null : usedBytes,
                text -> font.width(text));
        }
    }

    private Component getNextCpuButtonLabel() {
        if (menu.hasNoCPU()) return GuiText.NoCraftingCPUs.text();
        Component cpuName = menu.cpuName == null ? GuiText.Automatic.text() : menu.cpuName;
        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        drawCpuTierGlyph(graphics);
        ECOCraftReportHeaderRenderer.render(graphics, font, offsetX, offsetY, reportSummary,
            menu.getPlan() == null && showLargeCycleIndicator()
                ? (System.nanoTime() - openedNanos) / 1_000_000L : null);
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            cycleItems.render(graphics, mouseX, mouseY, mode.neoecoae$getCycleItems(),
                cycleScrollbar.getCurrentScroll(), selectedCycleComponentId);
        }

        CraftingPlanSummary plan = menu.getPlan();
        if (shouldUseExactMaterialTable()) {
            exactTable.render(graphics, mouseX, mouseY, exactMaterials(), scrollbar.getCurrentScroll());
        }
        else if (plan != null) table.render(graphics, mouseX, mouseY, plan.getEntries(), scrollbar.getCurrentScroll());

        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            drawCyclePlanningStatus(graphics, mode.neoecoae$isCyclePlanningEnabled(),
                !mode.neoecoae$getCraftingGraphSnapshot().cycleGroups().isEmpty(),
                mode.neoecoae$getPlanningStatus());
        }

        cycleItems.renderTooltip(graphics);
        int localMouseX = mouseX - offsetX;
        int localMouseY = mouseY - offsetY;
        if (ECOCraftReportHeaderRenderer.isHovered(localMouseX, localMouseY)) {
            drawTooltipWithHeader(graphics, localMouseX, localMouseY, reportSummary.tooltip());
        }
    }

    private void drawCpuTierGlyph(GuiGraphics graphics) {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) return;
        int sourceX = switch (mode.neoecoae$getSelectedCpuTier()) {
            case 4 -> 0;
            case 6 -> 13;
            case 9 -> 26;
            default -> -1;
        };
        if (sourceX < 0) return;
        graphics.blit(CRAFTING_WIDGETS_TEXTURE,
            CPU_TIER_TARGET_X, CPU_TIER_TARGET_Y, 0,
            sourceX, CPU_TIER_GLYPH_V,
            CPU_TIER_GLYPH_WIDTH, CPU_TIER_GLYPH_HEIGHT,
            288, 256);
    }

    private boolean showLargeCycleIndicator() {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$isCyclePlanningEnabled()
            && System.nanoTime() - openedNanos >= LARGE_CYCLE_INDICATOR_DELAY_NANOS;
    }

    private void drawCyclePlanningStatus(GuiGraphics graphics, boolean enabled, boolean cycleDetected,
                                           @Nullable PlanningStatus status) {
        var icon = cycleDetected && enabled
            ? ECOIcon.CYCLE_ENABLED
            : ECOIcon.CYCLE_DISABLED;
        icon.blit(graphics, CYCLE_STATUS_X, CYCLE_STATUS_Y, 0);
        String labelKey = !cycleDetected
            ? status == PlanningStatus.CYCLE_UNRESOLVED
                ? "gui.neoecoae.crafting_report.cycle_not_detected_preview"
                : "gui.neoecoae.crafting_report.cycle_not_detected"
            : enabled
                ? "gui.neoecoae.crafting_report.cycle_planning_enabled"
                : "gui.neoecoae.crafting_report.cycle_planning_disabled";
        Component label = Component.translatable(labelKey)
            .withColor(AE2_TEXT_DARK);
        graphics.drawString(font, label, CYCLE_STATUS_X + 18, CYCLE_STATUS_Y + 4,
            AE2_TEXT_DARK, false);
    }

    @Override @Nullable public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = cycleItems.getHoveredStack();
        if (hovered == null) hovered = shouldUseExactMaterialTable()
            ? exactTable.getHoveredStack() : table.getHoveredStack();
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
        if (!start.active) return;
        CraftingPlanSummary plan = menu.getPlan();
        boolean forceStart = Screen.hasShiftDown() && plan != null && plan.isSimulation();
        submit(forceStart);
    }

    /** Retry the same request, including its force flag and ECO execution path. */
    void retrySubmission() {
        submit(lastSubmissionForced);
    }

    private boolean usesBigOrderSubmission(boolean forceStart) {
        // Ordinary shortages use the selected CPU and the shared force-start protocol. A missing
        // cycle has no executable phase schedule yet and must be replanned by an ECO parent order.
        return isUnrepresentablePlan() || forceStart && (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$getPlanningStatus() == PlanningStatus.MISSING_ITEMS
            && !mode.neoecoae$getCraftingGraphSnapshot().cycleGroups().isEmpty();
    }

    private void submit(boolean forceStart) {
        lastSubmissionForced = forceStart;
        org.slf4j.LoggerFactory.getLogger("neoecoae").info(
            "[big-order-submit] Client start: container={}, forced={}, bigOrder={}, status={}",
            menu.containerId, forceStart, usesBigOrderSubmission(forceStart),
            (Object) menu instanceof ECOCraftConfirmMenuMode mode ? mode.neoecoae$getPlanningStatus() : null);
        if (usesBigOrderSubmission(forceStart)) {
            PacketDistributor.sendToServer(new cn.dancingsnow.neoecoae.network.ECOBigOrderStartC2SPacket(
                menu.containerId, forceStart));
            return;
        }
        PacketDistributor.sendToServer(new ECOForceCraftStartFlagC2SPacket(menu.containerId, forceStart));
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

    private void bookmarkMissing() {
        if (!bookmarkMissing.active) return;
        JeiBookmarkAccess.addMissingToBookmarks(exactMaterials().stream()
            .filter(node -> node.missingBigInteger().signum() > 0)
            .map(CraftingGraphSnapshot.MaterialNode::key).toList());
    }

    private List<CraftingGraphSnapshot.MaterialNode> exactMaterials() {
        if (!((Object) menu instanceof ECOCraftConfirmMenuMode mode)) return List.of();
        if (isDiagnosticShell()) return mode.neoecoae$getCraftingGraphSnapshot().nodes();
        return ECOExactMaterialTableRenderer.sortMaterials(mode.neoecoae$getCraftingGraphSnapshot().nodes());
    }

    /**
     * Returns the AE key rendered under the given absolute screen coordinates.
     *
     * <p>The report uses custom table renderers instead of vanilla slots, so EMI cannot discover these
     * stacks through its normal handled-screen fallback. The optional EMI integration calls this method
     * from its stack provider to expose the same item/ fluid entries to recipe and usage lookups.</p>
     */
    public @Nullable AEKey getECOStackAt(int mouseX, int mouseY) {
        int localX = mouseX - getGuiLeft();
        int localY = mouseY - getGuiTop();

        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode) {
            var cycleEntries = mode.neoecoae$getCycleItems();
            int cycleX = 237;
            int cycleY = 27;
            int cycleWidth = 67;
            int cycleHeight = 23;
            if (localX >= cycleX && localX < cycleX + cycleWidth && localY >= cycleY) {
                int row = (localY - cycleY) / cycleHeight;
                if (row < 7 && localY < cycleY + row * cycleHeight + cycleHeight - 1) {
                    int index = cycleScrollbar.getCurrentScroll() + row;
                    if (index >= 0 && index < cycleEntries.size()) return cycleEntries.get(index).what();
                }
            }
        }

        int tableX = 9;
        int tableY = 27;
        int cellWidth = 68;
        int cellHeight = 23;
        if (localX < tableX || localX >= tableX + 3 * cellWidth
                || localY < tableY || localY >= tableY + 7 * cellHeight) return null;
        int col = (localX - tableX) / cellWidth;
        int row = (localY - tableY) / cellHeight;
        int index = scrollbar.getCurrentScroll() * 3 + row * 3 + col;
        if (index < 0) return null;
        if (shouldUseExactMaterialTable()) {
            var entries = exactMaterials();
            return index < entries.size() ? entries.get(index).key() : null;
        }
        CraftingPlanSummary plan = menu.getPlan();
        if (plan == null || index >= plan.getEntries().size()) return null;
        return plan.getEntries().get(index).getWhat();
    }

    private boolean isDiagnosticShell() {
        var plan = menu.getPlan();
        return plan != null && (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && ECOExactMaterialTableRenderer.isDiagnosticShell(mode.neoecoae$getPlanningStatus(),
                plan.isSimulation(), plan.getEntries().isEmpty());
    }

    private boolean isUnrepresentablePlan() {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$getPlanningStatus() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE;
    }

    /**
     * A material-shortage result has authoritative graph quantities even without cycles. Unsupported attempts
     * attached to a native fallback remain explanation-only and keep the native confirmation table.
     */
    private boolean shouldUseExactMaterialTable() {
        if (isDiagnosticShell()) return true;
        if ((Object) menu instanceof ECOCraftConfirmMenuMode mode
                && ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(
                    mode.neoecoae$getPlanningStatus(), mode.neoecoae$getCraftingGraphSnapshot())) return true;
        if (isUnrepresentablePlan()) return true;
        if (!hasEcoCycleDiagnostics()) return false;
        PlanningStatus status = ((ECOCraftConfirmMenuMode) (Object) menu).neoecoae$getPlanningStatus();
        return status != PlanningStatus.MISSING_ITEMS
            && status != PlanningStatus.PARTIAL_UNSUPPORTED
            && status != PlanningStatus.UNSUPPORTED
            && status != PlanningStatus.INTERNAL_ERROR;
    }

    private boolean hasEcoCycleDiagnostics() {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && !mode.neoecoae$getCycleItems().isEmpty();
    }

    private boolean isCycleParticipant(AEKey key) {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$getCycleItems().stream().anyMatch(entry -> entry.what().equals(key));
    }

    private boolean isMissingStartupSeed(AEKey key) {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$getCycleItems().stream()
                .filter(entry -> entry.what().equals(key))
                .anyMatch(entry -> entry.exactMissing().signum() > 0
                    && entry.solveStatus() == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
                    && entry.exactSingleNetOutput().signum() == 0
                    && entry.exactTotalNetOutput().signum() == 0);
    }

    private boolean isEstimatedStartupSeed(AEKey key) {
        return (Object) menu instanceof ECOCraftConfirmMenuMode mode
            && mode.neoecoae$getCycleItems().stream()
                .anyMatch(entry -> entry.what().equals(key) && entry.seedShortfallIsEstimate());
    }

    private boolean isFuzzyPlanningItem(AEKey key) {
        if (!(key instanceof AEItemKey itemKey) || !((Object) menu instanceof ECOCraftConfirmMenuMode mode)) {
            return false;
        }
        return mode.neoecoae$getCraftingGraphSnapshot().fuzzyPlanningItemIds()
            .contains(BuiltInRegistries.ITEM.getKey(itemKey.getItem()));
    }

    private static final class CraftingGraphButton extends IconButton {
        private CraftingGraphButton(Runnable onPress) {
            super(ignored -> onPress.run());
            setMessage(Component.translatable("gui.neoecoae.crafting_graph.open"));
        }

        @Override
        protected Icon getIcon() {
            // IconButton requires this method; rendering uses our bundled atlas below.
            return null;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int yOffset = isHovered() ? 1 : 0;
            var background = isHovered()
                ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                : ECOIcon.TOOLBAR_BUTTON_BACKGROUND;
            background.blit(graphics, getX() - 1, getY() + yOffset, 2);
            ECOIcon.CRAFT_HAMMER.blit(
                graphics, getX(), getY() + 1 + yOffset, 3);
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
