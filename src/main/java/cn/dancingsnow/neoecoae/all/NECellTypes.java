package cn.dancingsnow.neoecoae.all;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.util.entry.RegistryEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NECellTypes {

    public static final RegistryEntry<ECOCellType, ECOCellType> ITEM = REGISTRATE
        .cellType("items", () -> new ECOCellType(AEKeyType.items().getDescription()))
        .register();

    public static final RegistryEntry<ECOCellType, ECOCellType> FLUID = REGISTRATE
        .cellType("fluids", () -> new ECOCellType(AEKeyType.fluids().getDescription()))
        .register();

    public static void register() {
    }
}
