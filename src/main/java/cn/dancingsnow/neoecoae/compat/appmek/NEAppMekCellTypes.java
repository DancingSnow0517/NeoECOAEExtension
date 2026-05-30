package cn.dancingsnow.neoecoae.compat.appmek;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.util.entry.RegistryEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/**
 * Registers ECO cell types for Applied Mekanistics chemical storage.
 */
public class NEAppMekCellTypes {

    public static final RegistryEntry<ECOCellType> CHEMICAL = REGISTRATE
            .cellType("chemicals", () -> new ECOCellType(
                    NeoECOAE.id("chemicals"),
                    AppMekCompat.getChemicalKeyType().getDescription()))
            .register();

    public static void register() {
    }
}
