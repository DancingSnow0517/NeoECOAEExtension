package cn.dancingsnow.neoecoae.client.model.data;

import appeng.client.render.model.AEModelData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class ECODriveModelData {
    public static final ModelProperty<ItemStack> CELL = new ModelProperty<>();

    public static ModelData create(ItemStack cell) {
        return ModelData.builder()
            .with(CELL, cell)
            .with(AEModelData.SKIP_CACHE, true)
            .build();
    }
}
