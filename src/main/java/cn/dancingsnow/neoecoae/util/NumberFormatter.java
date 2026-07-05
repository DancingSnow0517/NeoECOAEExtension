package cn.dancingsnow.neoecoae.util;

import appeng.core.localization.Tooltips;

public class NumberFormatter {
    public static Tooltips.Amount getAmount(long amount) {
        return Tooltips.getAmount(amount);
    }
}
