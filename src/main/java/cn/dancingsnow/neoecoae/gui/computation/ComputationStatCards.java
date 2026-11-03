package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingTooltipCard;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import java.math.BigDecimal;
import net.minecraft.network.chat.Component;

/** Statistic cards for the computation host's CPU storage, thread and parallel panels. */
final class ComputationStatCards {
    private static final String CARD = "gui.neoecoae.computation.ui.card.";
    private static final int PARALLEL = 0xFFE7A6F0;

    private ComputationStatCards() {}

    static ModularUITooltipComponent storage(long used, long total) {
        long safeUsed = Math.max(0L, used), safeTotal = Math.max(0L, total);
        long free = Math.max(0L, safeTotal - safeUsed);
        double ratio = ratio(safeUsed, safeTotal);
        return CraftingTooltipCard.builder(CraftingTooltipCard.Icon.NONE, Component.translatable(CARD + "storage_title"))
            .headerRight(CraftingTooltipCard.upper(Component.translatable(CARD + "used_percent", percent(ratio))), usageColor(ratio))
            .value(bytes(safeUsed), bytes(safeTotal))
            .segment(ratio, CraftingTooltipCard.ACCENT, false)
            .row(muted(CARD + "available"), Component.literal(bytes(free)), CraftingTooltipCard.OK)
            .build();
    }

    static ModularUITooltipComponent threads(long used, long total) {
        long safeUsed = Math.max(0L, used), safeTotal = Math.max(0L, total);
        boolean full = safeTotal > 0L && safeUsed >= safeTotal;
        String state = full ? "full" : safeUsed == 0L ? "idle" : "busy";
        int stateColor = full ? CraftingTooltipCard.WARN : safeUsed == 0L ? CraftingTooltipCard.OK : CraftingTooltipCard.CYAN;
        return CraftingTooltipCard.builder(CraftingTooltipCard.Icon.NONE, Component.translatable(CARD + "threads_title"))
            .headerRight(CraftingTooltipCard.upper(Component.translatable(CARD + state)), stateColor)
            .value(count(safeUsed), count(safeTotal))
            .segment(ratio(safeUsed, safeTotal), CraftingTooltipCard.CYAN, false)
            .row(muted(CARD + "free_threads"), Component.literal(count(Math.max(0L, safeTotal - safeUsed))),
                CraftingTooltipCard.OK)
            .build();
    }

    static ModularUITooltipComponent parallel(long parallel, long fullParallel, int multiplier, long threads) {
        long safeParallel = Math.max(0L, parallel);
        double built = fullParallel <= 0L ? 0.0D : ratio(safeParallel, fullParallel);
        return CraftingTooltipCard.builder(CraftingTooltipCard.Icon.NONE, Component.translatable(CARD + "parallel_title"))
            .headerRight(CraftingTooltipCard.upper(Component.translatable(CARD + "built", percent(built))),
                built >= 1.0D ? CraftingTooltipCard.OK : CraftingTooltipCard.WARN)
            .value(count(safeParallel), null)
            .valueColor(PARALLEL)
            .segment(built, PARALLEL, false)
            .dividedRow(muted(CARD + "multiplier"), Component.literal("×" + Math.max(1, multiplier)), CraftingTooltipCard.WARN)
            .row(muted(CARD + "threads"), Component.literal(count(Math.max(0L, threads))), CraftingTooltipCard.PRIMARY)
            .row(muted(CARD + "full_per_thread"), Component.empty(), CraftingTooltipCard.PRIMARY)
            .build();
    }

    /** Binary byte amount with SI-style unit names (KB…EB, no "i"), prefixed with ≈ when rounded. */
    static String bytes(long bytes) {
        long safe = Math.max(0L, bytes);
        String text = NEByteFormatter.format(safe);
        int unitPower = Math.min(6, safe <= 0L ? 0 : (63 - Long.numberOfLeadingZeros(safe)) / 10);
        BigDecimal exact = BigDecimal.valueOf(safe).divide(BigDecimal.valueOf(1L << (unitPower * 10)));
        return exact.stripTrailingZeros().scale() > 3 ? "≈" + text : text;
    }

    static String count(long value) {
        return NEByteFormatter.formatCpuCoProcessors(value);
    }

    private static Component muted(String key) {
        return Component.translatable(key).withStyle(style -> style.withColor(CraftingTooltipCard.MUTED));
    }

    private static double ratio(long used, long total) {
        if (used <= 0L || total <= 0L) return 0.0D;
        return used >= total ? 1.0D : used / (double) total;
    }

    private static String percent(double ratio) {
        if (ratio > 0.0D && ratio < 0.01D) return "<1%";
        return Math.round(ratio * 100.0D) + "%";
    }

    private static int usageColor(double ratio) {
        return ratio >= 0.9D ? CraftingTooltipCard.WARN : CraftingTooltipCard.OK;
    }
}
