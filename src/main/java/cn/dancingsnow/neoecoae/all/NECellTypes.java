package cn.dancingsnow.neoecoae.all;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NECellTypes {

    static {
        REGISTRATE.addLang("cell_type", NeoECOAE.id("bulk_item"), "Bulk Item");
    }

    public static final NECellTypeEntry ITEM = REGISTRATE.cellType("items")
        .desc(AEKeyType.items().getDescription().copy().withColor(0xf89737))
        .typeCount(315)
        .register();

    public static final NECellTypeEntry BULK_ITEM = REGISTRATE.cellType("bulk_item")
        .desc(Component.translatable("cell_type.neoecoae.bulk_item").withColor(0xe7a34b))
        .typeCount(1)
        .register();

    public static final NECellTypeEntry FLUID = REGISTRATE.cellType("fluids")
        .desc(AEKeyType.fluids().getDescription().copy().withColor(0x9bc9fe))
        .typeCount(25)
        .register();

    public static void register() {
    }
}
