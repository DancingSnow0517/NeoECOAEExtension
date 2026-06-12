package cn.dancingsnow.neoecoae.all;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NECellTypes {

    public static final NECellTypeEntry ITEM = REGISTRATE.cellType("items")
        .desc(AEKeyType.items().getDescription().copy().withColor(0xf89737))
        .typeCount(315)
        .register();

    public static final NECellTypeEntry FLUID = REGISTRATE.cellType("fluids")
        .desc(AEKeyType.fluids().getDescription().copy().withColor(0x9bc9fe))
        .typeCount(25)
        .register();

    public static void register() {
    }
}
