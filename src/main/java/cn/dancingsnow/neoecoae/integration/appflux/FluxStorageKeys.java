package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.api.stacks.AEKey;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import net.neoforged.fml.ModList;

public final class FluxStorageKeys {
    private FluxStorageKeys() {
    }

    public static boolean isEnergy(AEKey key) {
        return ModList.get() != null && ModList.get().isLoaded("appflux") && key.getType() == FluxKeyType.TYPE;
    }
}
