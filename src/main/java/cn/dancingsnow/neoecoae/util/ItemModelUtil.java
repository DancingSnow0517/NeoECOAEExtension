package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemModelUtil {
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> cellModel(String type, String size) {
        String assetType = type.equals("fe") ? "energy" : type;
        return importedCellModel("eco_%s_cell_%s".formatted(assetType, size));
    }

    /** Uses one of the imported, family-specific item models in {@code models/item/cell}. */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> importedCellModel(String model) {
        return (ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("item/cell/" + model));
    }

    /**
     * Icon model for a cell whose housing is its own texture and which has no tier, so no level
     * light is drawn. The status light layer stays, keeping the AE2 status colour readout.
     */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> infiniteCellModel(String housing) {
        return (ctx, prov) -> {
            List<net.minecraft.resources.ResourceLocation> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/" + housing));
            textures.add(prov.modLoc("item/eco_cell_status_light"));
            prov.generated(ctx::get, textures.toArray(net.minecraft.resources.ResourceLocation[]::new));
        };
    }
}
