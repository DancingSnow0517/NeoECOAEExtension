package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.gui.theme.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

/** AE2 report cells with the ECO report's seven visible rows. */
final class ECOCraftConfirmTableRenderer extends AbstractTableRenderer<CraftingPlanSummaryEntry> {
    private final Predicate<AEKey> cycleParticipant;
    private final Predicate<AEKey> fuzzyPlanningItem;

    ECOCraftConfirmTableRenderer(AEBaseScreen<?> screen, int x, int y,
            Predicate<AEKey> cycleParticipant, Predicate<AEKey> fuzzyPlanningItem) {
        super(screen, x, y, 7);
        this.cycleParticipant = cycleParticipant;
        this.fuzzyPlanningItem = fuzzyPlanningItem;
    }

    @Override protected List<Component> getEntryDescription(CraftingPlanSummaryEntry entry) {
        List<Component> lines = new ArrayList<>(3);
        if (entry.getStoredAmount() > 0) lines.add(GuiText.FromStorage.text(
            entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.SLOT)));
        if (entry.getMissingAmount() > 0) lines.add(GuiText.Missing.text(
            entry.getWhat().formatAmount(entry.getMissingAmount(), AmountFormat.SLOT)));
        if (entry.getCraftAmount() > 0) lines.add(GuiText.ToCraft.text(
            entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.SLOT)));
        return lines;
    }

    @Override protected AEKey getEntryStack(CraftingPlanSummaryEntry entry) { return entry.getWhat(); }

    @Override protected List<Component> getEntryTooltip(CraftingPlanSummaryEntry entry) {
        List<Component> lines = AEKeyRendering.getTooltip(entry.getWhat());
        if (entry.getStoredAmount() > 0) lines.add(GuiText.FromStorage.text(
            entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.FULL)));
        if (entry.getMissingAmount() > 0) lines.add(GuiText.Missing.text(
            entry.getWhat().formatAmount(entry.getMissingAmount(), AmountFormat.FULL)));
        if (entry.getCraftAmount() > 0) lines.add(GuiText.ToCraft.text(
            entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.FULL)));
        return lines;
    }

    @Override protected int getEntryOverlayColor(CraftingPlanSummaryEntry entry) {
        if (entry.getMissingAmount() > 0) return Palette.OVERLAY_MISSING;
        if (cycleParticipant.test(entry.getWhat())) return Palette.OVERLAY_CYCLE;
        if (fuzzyPlanningItem.test(entry.getWhat())) return Palette.OVERLAY_FUZZY_PLANNING;
        return 0;
    }
}
