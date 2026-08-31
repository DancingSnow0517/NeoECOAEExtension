package cn.dancingsnow.neoecoae.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

/** AE2 report cells with the ECO report's seven visible rows. */
final class ECOCraftConfirmTableRenderer extends AbstractTableRenderer<CraftingPlanSummaryEntry> {
    private static final int MISSING_OVERLAY = 0x1AFF0000;
    private static final int DISABLED_CYCLE_OVERLAY = 0x1AB86BFF;
    private final Predicate<AEKey> disabledCycleRequirement;

    ECOCraftConfirmTableRenderer(AEBaseScreen<?> screen, int x, int y,
            Predicate<AEKey> disabledCycleRequirement) {
        super(screen, x, y, 7);
        this.disabledCycleRequirement = disabledCycleRequirement;
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
        if (entry.getMissingAmount() <= 0) return 0;
        return disabledCycleRequirement.test(entry.getWhat()) ? DISABLED_CYCLE_OVERLAY : MISSING_OVERLAY;
    }
}
