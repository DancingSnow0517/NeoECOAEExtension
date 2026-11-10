package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2-style 3x7 material table backed by the planner's exact amounts.
 *
 * <p>The native confirmation table can only consume long-valued entries. This renderer keeps the
 * same layout and interaction for an explanatory plan whose amounts are represented by BigInteger.
 */
final class ECOExactMaterialTableRenderer extends AbstractTableRenderer<CraftingGraphSnapshot.MaterialNode> {
    /** Prepared once per synchronized report, shared by rendering, scrolling and recipe-viewer lookups. */
    static final class MaterialData {
        private CraftingGraphSnapshot snapshot;
        private List<ECOCycleItemList.Entry> cycles;
        private List<CraftingGraphSnapshot.MaterialNode> sorted = List.of();
        private List<AEKey> missing = List.of();
        private Set<AEKey> participants = Set.of();
        private Set<AEKey> missingSeeds = Set.of();
        private Set<AEKey> estimatedSeeds = Set.of();
        private Set<Integer> componentIds = Set.of();
        private Set<ResourceLocation> fuzzyItems = Set.of();
        private boolean materialDetails;
        private boolean completeByteEstimate;

        void update(CraftingGraphSnapshot snapshot, List<ECOCycleItemList.Entry> cycles) {
            // Network diagnostics replace both DTOs. Never hash/compare their full contents in a frame.
            if (this.snapshot == snapshot && this.cycles == cycles) return;
            this.snapshot = snapshot;
            this.cycles = cycles;
            Map<Integer, Set<AEKey>> requiredSeeds = new HashMap<>();
            for (var cycle : snapshot.cycleGroups()) {
                Set<AEKey> seeds = new HashSet<>();
                cycle.requiredSeed().forEach(seed -> seeds.add(seed.key()));
                requiredSeeds.put(cycle.componentId(), seeds);
            }
            participants = new HashSet<>();
            missingSeeds = new HashSet<>();
            estimatedSeeds = new HashSet<>();
            componentIds = new HashSet<>();
            for (var entry : cycles) {
                participants.add(entry.what());
                componentIds.add(entry.componentId());
                if (entry.seedShortfallIsEstimate()) estimatedSeeds.add(entry.what());
                if (entry.exactMissing().signum() > 0
                        && entry.solveStatus() == CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT
                        && entry.exactTotalNetOutput().signum() >= 0
                        && requiredSeeds.getOrDefault(entry.componentId(), Set.of()).contains(entry.what())) {
                    missingSeeds.add(entry.what());
                }
            }
            fuzzyItems = new HashSet<>(snapshot.fuzzyPlanningItemIds());
            sorted = sortMaterials(snapshot.nodes(), missingSeeds::contains);
            missing = sorted.stream().filter(node -> node.missingBigInteger().signum() > 0)
                .map(CraftingGraphSnapshot.MaterialNode::key).toList();
            materialDetails = ECOExactMaterialTableRenderer.hasMaterialDetails(snapshot);
            completeByteEstimate = ECOCraftReportHeaderRenderer.hasCompleteByteEstimate(snapshot);
        }

        List<CraftingGraphSnapshot.MaterialNode> sorted() { return sorted; }
        List<AEKey> missing() { return missing; }
        boolean isCycleParticipant(AEKey key) { return participants.contains(key); }
        boolean isMissingStartupSeed(AEKey key) { return missingSeeds.contains(key); }
        boolean isEstimatedStartupSeed(AEKey key) { return estimatedSeeds.contains(key); }
        boolean hasEstimatedStartupSeed() { return !estimatedSeeds.isEmpty(); }
        boolean containsComponent(int componentId) { return componentIds.contains(componentId); }
        boolean isFuzzyItem(ResourceLocation itemId) { return fuzzyItems.contains(itemId); }
        boolean hasMaterialDetails() { return materialDetails; }
        boolean hasCompleteByteEstimate() { return completeByteEstimate; }
    }

    /** Only empty simulation shells may replace the native fallback's material table. */
    static boolean isDiagnosticShell(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus status,
            boolean simulation, boolean empty) {
        if (status == null || !simulation || !empty) return false;
        return switch (status) {
            case PARTIAL_UNSUPPORTED, UNSUPPORTED, INTERNAL_ERROR, CYCLE_UNSUPPORTED, AMOUNT_OVERFLOW -> true;
            default -> false;
        };
    }

    /** Missing ECO plans use the same quantities as the graph, including acyclic plans. */
    static boolean hasMissingMaterialSnapshot(
            cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus status,
            CraftingGraphSnapshot snapshot) {
        return (status == cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.MISSING_ITEMS
                || status == cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.CYCLE_UNRESOLVED)
            && !snapshot.nodes().isEmpty();
    }

    /** The requested root alone is not a material calculation or a zero-byte estimate. */
    static boolean hasMaterialDetails(CraftingGraphSnapshot snapshot) {
        return snapshot.nodes().stream().anyMatch(node -> node.fromInventoryBigInteger().signum() > 0
            || node.toCraftBigInteger().signum() > 0 || node.missingBigInteger().signum() > 0);
    }

    private static final int MISSING_OVERLAY = 0x1AFF0000;
    private static final int CYCLE_OVERLAY = 0x1AB86BFF;
    private static final int FUZZY_PLANNING_OVERLAY = 0x264CAF70;
    private final Predicate<AEKey> cycleParticipant;
    private final Predicate<AEKey> missingStartupSeed;
    private final Predicate<AEKey> estimatedStartupSeed;
    private final Predicate<AEKey> fuzzyPlanningItem;

    ECOExactMaterialTableRenderer(AEBaseScreen<?> screen, int x, int y,
            Predicate<AEKey> cycleParticipant, Predicate<AEKey> missingStartupSeed,
            Predicate<AEKey> estimatedStartupSeed, Predicate<AEKey> fuzzyPlanningItem) {
        super(screen, x, y, 7);
        this.cycleParticipant = cycleParticipant;
        this.missingStartupSeed = missingStartupSeed;
        this.estimatedStartupSeed = estimatedStartupSeed;
        this.fuzzyPlanningItem = fuzzyPlanningItem;
    }

    @Override
    protected List<Component> getEntryDescription(CraftingGraphSnapshot.MaterialNode entry) {
        return describe(entry, AmountFormat.SLOT);
    }

    List<Component> describe(CraftingGraphSnapshot.MaterialNode entry, AmountFormat format) {
        List<Component> lines = new ArrayList<>(3);
        BigInteger stored = entry.fromInventoryBigInteger();
        BigInteger missing = entry.missingBigInteger();
        BigInteger craft = entry.toCraftBigInteger();
        if (stored.signum() > 0) {
            lines.add(GuiText.FromStorage.text(formatAmount(entry.key(), stored, format)));
        }
        if (missing.signum() > 0) {
            lines.add(missingDescription(entry.key(), missing, format));
        }
        if (craft.signum() > 0) {
            lines.add(GuiText.ToCraft.text(formatAmount(entry.key(), craft, format)));
        }
        if (craft.signum() == 0 && entry.requestedBigInteger().compareTo(stored.add(missing)) > 0) {
            lines.add(Component.translatable("gui.neoecoae.crafting_graph.details.requested",
                formatAmount(entry.key(), entry.requestedBigInteger(), format)));
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable("gui.neoecoae.crafting_report.quantity_unknown"));
        }
        return lines;
    }

    @Override
    protected AEKey getEntryStack(CraftingGraphSnapshot.MaterialNode entry) {
        return entry.key();
    }

    @Override
    protected List<Component> getEntryTooltip(CraftingGraphSnapshot.MaterialNode entry) {
        ((ECOCraftConfirmScreen) screen).queueMaterialTooltip(new ECOCraftReportTooltips.Material(entry.key(),
            entry.fromInventoryBigInteger(), entry.missingBigInteger(), entry.toCraftBigInteger(), entry.requestedBigInteger(),
            isMissingStartupSeed(entry.key()), isEstimatedStartupSeed(entry.key())));
        // AE2 draws text tooltips inside its translated foreground. The report draws this card after that pose is restored.
        return List.of();
    }

    @Override
    protected int getEntryOverlayColor(CraftingGraphSnapshot.MaterialNode entry) {
        if (entry.missingBigInteger().signum() > 0) return MISSING_OVERLAY;
        if (cycleParticipant.test(entry.key())) return CYCLE_OVERLAY;
        if (fuzzyPlanningItem.test(entry.key())) return FUZZY_PLANNING_OVERLAY;
        return 0;
    }

    static List<CraftingGraphSnapshot.MaterialNode> sortMaterials(List<CraftingGraphSnapshot.MaterialNode> nodes,
            Predicate<AEKey> missingStartupSeed) {
        return nodes.stream()
            .map(node -> new MaterialOrder(node, missingStartupSeed.test(node.key()),
                node.missingBigInteger(), node.toCraftBigInteger(), node.fromInventoryBigInteger()))
            .filter(order -> order.missing().signum() > 0 || order.craft().signum() > 0
                || order.stored().signum() > 0 || order.node().requestedBigInteger().signum() > 0)
            .sorted(Comparator.comparing(MaterialOrder::startupSeed).reversed()
                .thenComparing(MaterialOrder::missing, Comparator.reverseOrder())
                .thenComparing(MaterialOrder::craft, Comparator.reverseOrder())
                .thenComparing(MaterialOrder::stored, Comparator.reverseOrder())
                .thenComparing(order -> order.node().key().toString()))
            .map(MaterialOrder::node)
            .toList();
    }

    private record MaterialOrder(CraftingGraphSnapshot.MaterialNode node, boolean startupSeed,
            BigInteger missing, BigInteger craft, BigInteger stored) {}

    private boolean isMissingStartupSeed(AEKey key) {
        return missingStartupSeed != null && missingStartupSeed.test(key);
    }

    private boolean isEstimatedStartupSeed(AEKey key) {
        return estimatedStartupSeed != null && estimatedStartupSeed.test(key);
    }

    private Component missingDescription(AEKey key, BigInteger missing, AmountFormat format) {
        String amount = formatAmount(key, missing, format);
        if (isEstimatedStartupSeed(key)) {
            return Component.translatable("gui.neoecoae.crafting_report.seed_shortfall_estimate", amount);
        }
        return isMissingStartupSeed(key)
            ? Component.translatable("gui.neoecoae.crafting_report.missing_startup_seed").append(": " + amount)
            : GuiText.Missing.text(amount);
    }

    static String formatAmount(AEKey key, BigInteger amount, AmountFormat format) {
        if (amount.signum() < 0) {
            amount = amount.negate();
        }
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return DisplayNumbers.grouped(key.formatAmount(amount.longValue(), format));
        }
        int amountPerUnit = Math.max(1, key.getAmountPerUnit());
        String formatted = BigNumberFormatter.format(amount, amountPerUnit, format == AmountFormat.FULL);
        if (format == AmountFormat.FULL) {
            String unit = key.getUnitSymbol();
            return unit == null ? formatted : formatted + " " + unit;
        }
        return formatted;
    }
}
