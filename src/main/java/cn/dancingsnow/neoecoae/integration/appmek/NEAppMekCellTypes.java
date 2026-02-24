package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.util.entry.RegistryEntry;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppMekCellTypes {

    public static final RegistryEntry<ECOCellType, ECOCellType> MEKANISM = REGISTRATE
        .cellType("mekanism", () -> new ECOCellType(MekanismKeyType.TYPE.getDescription()))
        .register();

    public static void register() {}
}
