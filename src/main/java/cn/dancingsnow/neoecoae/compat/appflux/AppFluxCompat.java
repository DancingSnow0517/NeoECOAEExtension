package cn.dancingsnow.neoecoae.compat.appflux;

import appeng.api.stacks.AEKeyType;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;

/**
 * Safe accessor for AppliedFlux API types.
 *
 * <p>Only call from the {@code appflux} optional integration boundary.
 */
public final class AppFluxCompat {
    private AppFluxCompat() {}

    public static AEKeyType getFluxKeyType() {
        return FluxKeyType.TYPE;
    }
}
