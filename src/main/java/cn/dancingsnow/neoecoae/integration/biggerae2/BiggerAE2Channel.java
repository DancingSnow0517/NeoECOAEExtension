package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;

import java.util.function.Supplier;

/** The six upstream channels; only item, fluid and chemical receive the five-type expansion. */
enum BiggerAE2Channel {
    ITEM("item", "Item", "物品", null, 5, 0xf89737, () -> AEKeyType.items()),
    FLUID("fluid", "Fluid", "流体", null, 5, 0x9bc9fe, () -> AEKeyType.fluids()),
    CHEMICAL("chemical", "Chemical", "化学品", "appmek", 5, 0x37f89e,
        ExternalCellKeyTypes.byId("appmek", "chemical")),
    FE("fe", "FE", "FE", "appflux", 1, 0xdd504c,
        ExternalCellKeyTypes.byId("appflux", "flux")),
    EXPERIENCE("experience", "Experience", "经验", "appex", 1, 0x74d56d,
        ExternalCellKeyTypes.byId("appex", "experience")),
    SOURCE("source", "Source", "魔源", "arseng", 1, 0x9d60d1,
        ExternalCellKeyTypes.byId("arseng", "source"));

    final String id;
    final String displayName;
    final String chineseName;
    final String requiredMod;
    final int typeLimit;
    final int color;
    final Supplier<AEKeyType> keyType;

    BiggerAE2Channel(String id, String displayName, String chineseName, String requiredMod,
            int typeLimit, int color, Supplier<AEKeyType> keyType) {
        this.id = id;
        this.displayName = displayName;
        this.chineseName = chineseName;
        this.requiredMod = requiredMod;
        this.typeLimit = typeLimit;
        this.color = color;
        this.keyType = keyType;
    }

    String cellId(boolean singularity) {
        return "eco_" + (singularity ? "digital_singularity_" : "quantum_") + id + "_storage_cell";
    }

    String housingId() {
        return "neoecoae:eco_" + id + "_cell_housing";
    }

    static String componentId(boolean singularity) {
        return "bigger_ae2:" + (singularity ? "digital_singularity_cell_component" : "quantum_cell_component");
    }
}
