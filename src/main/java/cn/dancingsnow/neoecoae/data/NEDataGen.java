package cn.dancingsnow.neoecoae.data;

import cn.dancingsnow.neoecoae.data.model.CellModelGenerator;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEDataGen {
    public static void configureDataGen() {
        REGISTRATE.addDataGenerator(NEProviderTypes.CELL_MODEL, CellModelGenerator::accept);
    }
}
