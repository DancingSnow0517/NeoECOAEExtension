package cn.dancingsnow.neoecoae.integration.appmek;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.util.entry.RegistryEntry;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;

public class NEAppMekCellTypes {

    public static final RegistryEntry<ECOCellType> MEKANISM = REGISTRATE
            .cellType("mekanism", () -> new ECOCellType(NeoECOAE.id("mekanism"), MekanismKeyType.TYPE.getDescription()))
            .register();

    public static void register() {}
}
