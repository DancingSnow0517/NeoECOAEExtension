package cn.dancingsnow.neoecoae.integration.appflux;

import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppFluxCellTypes {

    public static final NECellTypeEntry FLUX = REGISTRATE.cellType("flux")
        .desc(FluxKeyType.TYPE.getDescription().copy().withColor(0xdd504c))
        .typeCount(1)
        .register();

    public static void register() {
    }
}
