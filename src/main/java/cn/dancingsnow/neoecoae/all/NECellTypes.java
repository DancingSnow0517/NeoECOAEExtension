package cn.dancingsnow.neoecoae.all;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.network.chat.Component;

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

    public static final RegistryEntry<ECOCellType> CHEMICAL = REGISTRATE
            .cellType(
                    "chemicals",
                    () -> new ECOCellType(
                            NeoECOAE.id("chemicals"), Component.translatable("cell_type.neoecoae.chemical")))
            .register();

    public static final RegistryEntry<ECOCellType> FE = REGISTRATE
            .cellType(
                    "flux",
                    () -> new ECOCellType(NeoECOAE.id("flux"), Component.translatable("cell_type.neoecoae.flux")))
            .register();

    public static final RegistryEntry<ECOCellType> MANA = REGISTRATE
            .cellType(
                    "mana",
                    () -> new ECOCellType(NeoECOAE.id("mana"), Component.translatable("cell_type.neoecoae.mana")))
            .register();

    public static final RegistryEntry<ECOCellType> SOURCE = REGISTRATE
            .cellType(
                    "source",
                    () -> new ECOCellType(NeoECOAE.id("source"), Component.translatable("cell_type.neoecoae.source")))
            .register();

    public static void register() {}
}
