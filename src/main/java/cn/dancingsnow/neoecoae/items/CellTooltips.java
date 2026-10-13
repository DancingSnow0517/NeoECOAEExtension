package cn.dancingsnow.neoecoae.items;

import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Tooltip lines shared by ECO storage cell items and their Jade providers.
 */
public final class CellTooltips {

    private CellTooltips() {
    }

    public static Component infiniteMemberHint() {
        return Component.translatable("tooltip.neoecoae.storage.infinite_member")
            .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    public static Component uuidLine(UUID id) {
        return Component.literal("UUID: " + id).withStyle(ChatFormatting.AQUA);
    }

    /**
     * {@link Long#MAX_VALUE} is the saturation sentinel for an unbounded capacity, so render it as
     * an infinity symbol instead of the raw number.
     */
    public static Component bytesUsed(long used, long total) {
        if (total != Long.MAX_VALUE) {
            return Tooltips.bytesUsed(used, total);
        }
        return Tooltips.of(
            GuiText.BytesUsed,
            Tooltips.of(
                Tooltips.ofUnformattedNumberWithRatioColor(used, 0.0, false),
                Tooltips.of(" "),
                Tooltips.of(GuiText.Of),
                Tooltips.of(" "),
                Component.literal("∞").withStyle(Tooltips.NUMBER_TEXT)
            )
        );
    }

    /**
     * An infinite type capacity has no meaningful total, so show the stored count against infinity.
     */
    public static Component typesUsed(long stored, long total, boolean infinite) {
        if (!infinite) {
            return Tooltips.typesUsed(stored, total);
        }
        return Tooltips.of(
            Tooltips.ofUnformattedNumberWithRatioColor(stored, 0, false),
            Tooltips.of(" "),
            Tooltips.of(GuiText.Of),
            Tooltips.of(" "),
            Component.literal("∞").withStyle(Tooltips.NUMBER_TEXT),
            Tooltips.of(" "),
            Tooltips.of(GuiText.Types)
        );
    }
}
