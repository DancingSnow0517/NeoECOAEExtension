package cn.dancingsnow.neoecoae.util;

import appeng.core.localization.Tooltips;

import static appeng.core.localization.Tooltips.DECIMAL_NUMS;
import static appeng.core.localization.Tooltips.units;

public class NumberFormatter {
    public static Tooltips.Amount getAmount(long amount) {
        if (amount < 1000) {
            return new Tooltips.Amount(String.valueOf(amount), "");
        } else {
            int i = 0;
            while (i < DECIMAL_NUMS.length && amount / DECIMAL_NUMS[i] >= 1000) {
                i++;
            }
            return new Tooltips.Amount(Tooltips.getAmount(amount, DECIMAL_NUMS[i]), units[i]);
        }
    }
}
