package cn.dancingsnow.neoecoae.util;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.network.chat.Component;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ComponentUtil {

    public static Component coloredNumberPair(long used, long total, boolean oneIsGreen) {
        return coloredPair(Tooltips.getAmount(used), Tooltips.getAmount(total), used, total, oneIsGreen);
    }

    public static Component coloredBytesPair(long used, long total, boolean oneIsGreen) {
        return coloredPair(Tooltips.getByteAmount(used), Tooltips.getByteAmount(total), used, total, oneIsGreen);
    }

    private static Component coloredPair(Tooltips.Amount amount, Tooltips.Amount amount1, long used, long total, boolean oneIsGreen) {
        float ratio = ECOHostStyles.ratio(used, total);
        return Component.empty()
            .append(Component.literal(amount.digit() + amount.unit()).withStyle(Tooltips.colorFromRatio(ratio, oneIsGreen)))
            .append(Component.literal(" / ").withColor(0x696d88))
            .append(Component.literal(amount1.digit() + amount1.unit()).withStyle(Tooltips.NUMBER_TEXT));
    }
}
