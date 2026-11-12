package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard.Glyph;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingTooltipCard;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/** Report content uses the CPU progress page's card typography, palette, spacing and native tooltip frame. */
final class ECOCraftReportTooltips {
    private static final String CARD = "gui.neoecoae.crafting_report.card.";

    private ECOCraftReportTooltips() {}

    record Material(AEKey key, BigInteger stored, BigInteger missing, BigInteger craft,
                    @Nullable BigInteger requested, boolean startupSeed, boolean estimatedSeed) {}

    static CraftingListCard.Builder pending(Component status) {
        return pending(status, false);
    }

    static CraftingListCard.Builder pending(Component status, boolean liteCrafting) {
        var card = statisticsHeader(null).divider().wrapped(null, status, CraftingTooltipCard.MUTED);
        return card;
    }

    static CraftingListCard.Builder statistics(long nanos, @Nullable BigInteger usedBytes, boolean complete,
            @Nullable CraftingGraphSnapshot.Summary summary, boolean exact) {
        return statistics(nanos, usedBytes, complete, summary, exact, false);
    }

    static CraftingListCard.Builder statistics(long nanos, @Nullable BigInteger usedBytes, boolean complete,
            @Nullable CraftingGraphSnapshot.Summary summary, boolean exact, boolean liteCrafting) {
        var card = statisticsHeader(summary).divider();
        BigDecimal millis = BigDecimal.valueOf(Math.max(0, nanos), 6);
        String elapsed = exact ? DisplayNumbers.grouped(millis)
            : DisplayNumbers.grouped(millis.setScale(3, RoundingMode.HALF_UP).toPlainString());
        card.row(null, CraftingTooltipCard.MUTED, text("elapsed"), Component.literal(elapsed + " ms"), CraftingTooltipCard.PRIMARY);
        Component byteLabel = text(complete ? "bytes" : "bytes_partial");
        if (!complete && usedBytes != null && usedBytes.signum() <= 0) usedBytes = null;
        if (exact && usedBytes != null) {
            card.section(byteLabel).wrapped(null, Component.literal(DisplayNumbers.grouped(usedBytes) + " B"), CraftingTooltipCard.CYAN);
        } else {
            card.largeValue(byteLabel, usedBytes == null ? text("unknown").getString()
                : DisplayNumbers.compactTooltip(usedBytes) + " B", CraftingTooltipCard.CYAN);
        }
        if (summary != null) {
            card.row(null, CraftingTooltipCard.MUTED, text("patterns"),
                    Component.literal(DisplayNumbers.grouped(summary.patternNodes())), CraftingTooltipCard.PRIMARY)
                .row(null, CraftingTooltipCard.MUTED, text("cycles"), Component.literal(DisplayNumbers.grouped(summary.cycleGroups())),
                    summary.cycleGroups() > 0 ? CraftingTooltipCard.CYAN : CraftingTooltipCard.PRIMARY);
        }
        if (!liteCrafting && !complete) {
            card.divider().wrapped(Glyph.INFO, text("incomplete"), CraftingTooltipCard.WARN)
                .wrapped(null, text("incomplete_hint"), CraftingTooltipCard.MUTED);
        }
        return card;
    }

    private static CraftingListCard.Builder statisticsHeader(@Nullable CraftingGraphSnapshot.Summary summary) {
        boolean cycles = summary != null && summary.cycleGroups() > 0;
        return CraftingListCard.builder().header(Glyph.CHIP, text("title"),
            cycles ? Component.translatable("gui.neoecoae.crafting_report.cycle_planning_enabled") : Component.empty(),
            CraftingTooltipCard.CYAN, cycles ? Glyph.NODES : null);
    }

    static CraftingListCard.Builder material(Material material, boolean exact) {
        var key = material.key();
        var keyTooltip = AEKeyRendering.getTooltip(key);
        Component name = keyTooltip.isEmpty() ? key.getDisplayName() : keyTooltip.get(0);
        Component source = keyTooltip.size() > 1 ? keyTooltip.get(keyTooltip.size() - 1) : Component.empty();
        String kind = key instanceof AEFluidKey ? "fluid_request" : key instanceof AEItemKey ? "item_request" : "resource_request";
        var card = CraftingListCard.builder()
            .iconHeader((graphics, x, y) -> AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, key),
                name, source)
            .divider().section(text(kind));
        amountRow(card, text("available"), key, material.stored(), CraftingTooltipCard.PRIMARY, exact);
        if (material.missing().signum() > 0) {
            Component missingLabel = material.estimatedSeed() ? text("missing_estimate")
                : material.startupSeed() ? Component.translatable("gui.neoecoae.crafting_report.missing_startup_seed") : text("missing");
            amountRow(card, missingLabel, key, material.missing(), CraftingTooltipCard.WARN, exact);
        }
        if (material.craft().signum() > 0) {
            amountRow(card, text("to_craft"), key, material.craft(), CraftingTooltipCard.PRIMARY, exact);
        }
        if (material.requested() != null) {
            card.divider();
            amountRow(card, text("requested"), key, material.requested(), CraftingTooltipCard.ACCENT, exact);
        }
        if (material.stored().signum() == 0 && material.missing().signum() == 0 && material.craft().signum() == 0) {
            card.wrapped(Glyph.INFO, Component.translatable("gui.neoecoae.crafting_report.quantity_unknown"), CraftingTooltipCard.MUTED);
        }
        if (material.estimatedSeed()) {
            card.wrapped(Glyph.INFO,
                Component.translatable("gui.neoecoae.crafting_report.seed_shortfall_estimate_hint"), CraftingTooltipCard.WARN);
        }
        for (int index = 1; index < keyTooltip.size() - 1; index++) {
            card.wrapped(Glyph.BOOK, keyTooltip.get(index), CraftingTooltipCard.MUTED);
        }
        return card.divider().hint(Glyph.INFO, text(exact ? "exact_shown" : "exact_hint"));
    }

    private static void amountRow(CraftingListCard.Builder card, Component label, AEKey key, BigInteger amount,
            int color, boolean exact) {
        String unit = key.getUnitSymbol();
        String value = BigNumberFormatter.formatTooltipAmount(amount, key.getAmountPerUnit(), exact)
            + (unit == null || unit.isBlank() ? "" : " " + unit);
        if (exact) card.section(label).wrapped(null, Component.literal(value), color);
        else card.largeValue(label, value, color);
    }

    private static MutableComponent text(String key) { return Component.translatable(CARD + key); }

}
