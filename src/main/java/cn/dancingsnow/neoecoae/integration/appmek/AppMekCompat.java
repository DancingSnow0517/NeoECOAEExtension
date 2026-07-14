package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.api.stacks.AEKeyType;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;

/**
 * Safe accessor for Applied Mekanistics API types.
 * <p>
 * All methods in this class will throw {@link NoClassDefFoundError}
 * if Applied Mekanistics is not installed. Callers must guard access
 * with {@code ModList.get().isLoaded("appmek")} or use the
 * {@link cn.dancingsnow.neoecoae.api.integration.Integration @Integration}
 * mechanism.
 * </p>
 */
public final class AppMekCompat {

    private AppMekCompat() {}

    /**
     * Returns the Mekanism chemical {@link AEKeyType} registered by
     * Applied Mekanistics.
     */
    public static AEKeyType getChemicalKeyType() {
        return MekanismKeyType.TYPE;
    }
}
