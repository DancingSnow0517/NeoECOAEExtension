package cn.dancingsnow.neoecoae.data.model;

import cn.dancingsnow.neoecoae.registration.provider.NECellModelProvider;

public class CellModelGenerator {

    public static void accept(NECellModelProvider provider) {
        // Drive cell geometry is imported in models/block/cell. Do not regenerate the old
        // shared-parent/LED models over those resources.
    }
}
