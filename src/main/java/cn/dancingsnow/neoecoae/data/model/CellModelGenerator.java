package cn.dancingsnow.neoecoae.data.model;

import cn.dancingsnow.neoecoae.registration.provider.NECellModelProvider;

public class CellModelGenerator {

    public static void accept(NECellModelProvider provider) {
        provider.cellModel("storage_cell_l4_item");
        provider.cellModel("storage_cell_l6_item");
        provider.cellModel("storage_cell_l9_item");

        provider.cellModel("storage_cell_l4_fluid");
        provider.cellModel("storage_cell_l6_fluid");
        provider.cellModel("storage_cell_l9_fluid");
    }
}
