package cn.dancingsnow.neoecoae.compat.arsenergistique;

import appeng.api.stacks.AEKeyType;
import gripe._90.arseng.me.key.SourceKeyType;

/**
 * Safe accessor for Ars Energistique API types.
 *
 * <p>Only call from the {@code arseng} optional integration boundary.
 */
public final class ArsEnergistiqueCompat {
    private ArsEnergistiqueCompat() {}

    public static AEKeyType getSourceKeyType() {
        return SourceKeyType.TYPE;
    }
}
