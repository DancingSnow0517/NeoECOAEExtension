package cn.dancingsnow.neoecoae.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

/**
 * AE2-style 3x7 material table backed by the planner's exact amounts.
 *
 * <p>The native confirmation table can only consume long-valued entries. This renderer keeps the
 * same layout and interaction for an explanatory plan whose amounts are represented by BigInteger.
 */
final class ECOExactMaterialTableRenderer extends AbstractTableRenderer<CraftingGraphSnapshot.MaterialNode> {
    private static final BigDecimal THOUSAND_DECIMAL = BigDecimal.valueOf(1000);
    private static final String[] SI_SUFFIXES = {"", "K", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q"};
    private static final int MISSING_OVERLAY = 0x1AFF0000;
    private static final int DISABLED_CYCLE_OVERLAY = 0x1AB86BFF;
    private final Predicate<AEKey> disabledCycleRequirement;

    ECOExactMaterialTableRenderer(AEBaseScreen<?> screen, int x, int y,
            Predicate<AEKey> disabledCycleRequirement) {
        super(screen, x, y, 7);
        this.disabledCycleRequirement = disabledCycleRequirement;
    }

    @Override
    protected List<Component> getEntryDescription(CraftingGraphSnapshot.MaterialNode entry) {
        List<Component> lines = new ArrayList<>(3);
        BigInteger stored = entry.fromInventoryBigInteger();
        BigInteger missing = entry.missingBigInteger();
        BigInteger craft = entry.toCraftBigInteger();
        if (stored.signum() > 0) {
            lines.add(GuiText.FromStorage.text(formatAmount(entry.key(), stored, AmountFormat.SLOT)));
        }
        if (missing.signum() > 0) {
            lines.add(entry.status() == CraftingGraphSnapshot.MaterialStatus.CYCLE
                ? Component.literal("缺少启动种子数量：" + formatAmount(entry.key(), missing, AmountFormat.SLOT))
                : GuiText.Missing.text(formatAmount(entry.key(), missing, AmountFormat.SLOT)));
        }
        if (craft.signum() > 0) {
            lines.add(GuiText.ToCraft.text(formatAmount(entry.key(), craft, AmountFormat.SLOT)));
        }
        return lines;
    }

    @Override
    protected AEKey getEntryStack(CraftingGraphSnapshot.MaterialNode entry) {
        return entry.key();
    }

    @Override
    protected List<Component> getEntryTooltip(CraftingGraphSnapshot.MaterialNode entry) {
        List<Component> lines = AEKeyRendering.getTooltip(entry.key());
        BigInteger stored = entry.fromInventoryBigInteger();
        BigInteger missing = entry.missingBigInteger();
        BigInteger craft = entry.toCraftBigInteger();
        if (stored.signum() > 0) {
            lines.add(GuiText.FromStorage.text(formatAmount(entry.key(), stored, AmountFormat.FULL)));
        }
        if (missing.signum() > 0) {
            lines.add(entry.status() == CraftingGraphSnapshot.MaterialStatus.CYCLE
                ? Component.literal("缺少启动种子数量：" + formatAmount(entry.key(), missing, AmountFormat.FULL))
                : GuiText.Missing.text(formatAmount(entry.key(), missing, AmountFormat.FULL)));
        }
        if (craft.signum() > 0) {
            lines.add(GuiText.ToCraft.text(formatAmount(entry.key(), craft, AmountFormat.FULL)));
        }
        lines.add(Component.translatable("gui.neoecoae.crafting_report.requested_exact", entry.exactRequested()));
        return lines;
    }

    @Override
    protected int getEntryOverlayColor(CraftingGraphSnapshot.MaterialNode entry) {
        if (entry.missingBigInteger().signum() <= 0) return 0;
        if (entry.status() == CraftingGraphSnapshot.MaterialStatus.CYCLE) return MISSING_OVERLAY;
        return disabledCycleRequirement.test(entry.key()) ? DISABLED_CYCLE_OVERLAY : MISSING_OVERLAY;
    }

    static List<CraftingGraphSnapshot.MaterialNode> sortMaterials(List<CraftingGraphSnapshot.MaterialNode> nodes) {
        return nodes.stream()
            .filter(ECOExactMaterialTableRenderer::hasAmount)
            .sorted(Comparator.comparing(CraftingGraphSnapshot.MaterialNode::missingBigInteger).reversed()
                .thenComparing(CraftingGraphSnapshot.MaterialNode::toCraftBigInteger, Comparator.reverseOrder())
                .thenComparing(CraftingGraphSnapshot.MaterialNode::fromInventoryBigInteger,
                    Comparator.reverseOrder())
                .thenComparing(node -> node.key().toString()))
            .toList();
    }

    private static boolean hasAmount(CraftingGraphSnapshot.MaterialNode node) {
        return node.requestedBigInteger().signum() > 0
            || node.fromInventoryBigInteger().signum() > 0
            || node.toCraftBigInteger().signum() > 0
            || node.missingBigInteger().signum() > 0;
    }

    private static String formatAmount(AEKey key, BigInteger amount, AmountFormat format) {
        if (amount.signum() < 0) {
            amount = amount.negate();
        }
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return key.formatAmount(amount.longValue(), format);
        }
        int amountPerUnit = Math.max(1, key.getAmountPerUnit());
        BigDecimal displayAmount = new BigDecimal(amount)
            .divide(BigDecimal.valueOf(amountPerUnit), 6, RoundingMode.DOWN);
        if (format == AmountFormat.FULL) {
            String formatted = NumberFormat.getNumberInstance().format(displayAmount.stripTrailingZeros());
            String unit = key.getUnitSymbol();
            return unit == null ? formatted : formatted + " " + unit;
        }
        return compact(displayAmount, format == AmountFormat.SLOT_LARGE_FONT ? 3 : 4);
    }

    /** Mirrors AE2's decimal SI formatter while accepting an arbitrary-precision value. */
    private static String compact(BigDecimal value, int maxWidth) {
        if (value.signum() == 0) return "0";
        int suffix = 0;
        BigDecimal scaled = value;
        while (suffix < SI_SUFFIXES.length - 1 && scaled.compareTo(THOUSAND_DECIMAL) >= 0) {
            scaled = scaled.divide(THOUSAND_DECIMAL, 6, RoundingMode.DOWN);
            suffix++;
        }
        int decimals = Math.max(0, maxWidth - integerDigits(scaled) - (suffix == 0 ? 0 : 1) - 1);
        String result = scaled.setScale(Math.min(2, decimals), RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString() + SI_SUFFIXES[suffix];
        if (result.length() <= maxWidth) return result;
        return scaled.setScale(0, RoundingMode.DOWN).toPlainString() + SI_SUFFIXES[suffix];
    }

    private static int integerDigits(BigDecimal value) {
        return Math.max(1, value.precision() - value.scale());
    }
}
