package cn.dancingsnow.neoecoae.all;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.util.entry.RegistryEntry;

public class NECellTypes {

    public static final RegistryEntry<ECOCellType> ITEM = REGISTRATE
            .cellType(
                    "items",
                    () -> new ECOCellType(
                            NeoECOAE.id("items"), AEKeyType.items().getDescription()))
            .register();

    public static final RegistryEntry<ECOCellType> FLUID = REGISTRATE
            .cellType(
                    "fluids",
                    () -> new ECOCellType(
                            NeoECOAE.id("fluids"), AEKeyType.fluids().getDescription()))
            .register();

    public static void register() {}
}
