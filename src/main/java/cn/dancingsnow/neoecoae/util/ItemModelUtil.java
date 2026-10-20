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
        // Imported item model IDs retain their existing energy family name; their textures use appflux/fe.
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
            textures.add(prov.modLoc("item/eco_cells/" + housing));
            textures.add(prov.modLoc("item/eco_cells/cell_status_light"));
            prov.generated(ctx::get, textures.toArray(net.minecraft.resources.ResourceLocation[]::new));
        };
    }

    /**
     * Icon model for cells whose housing lives under {@code item/eco_cells/<modDirectory>}.
     * Layer order is fixed: housing, tier light, status light, then any extra
     * overlays. The status light therefore always lands on tint index 2, which is the index
     * {@code NEItemColors} tints, for every cell regardless of how many extra overlays it has.
     */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> compatCellModel(
        String modDirectory,
        String housing,
        String size,
        String... overlays
    ) {
        String level = switch (size) {
            case "16m", "l4" -> "l4";
            case "64m", "l6" -> "l6";
            case "256m", "4g", "l9" -> "l9";
            default -> throw new IllegalArgumentException("Unknown cell size: " + size);
        };
        return (ctx, prov) -> {
            List<net.minecraft.resources.ResourceLocation> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + housing));
            textures.add(prov.modLoc("item/eco_cells/cell_light_" + level));
            textures.add(prov.modLoc("item/eco_cells/cell_status_light"));
            for (String overlay : overlays) {
                textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + overlay));
            }
            prov.generated(ctx::get, textures.toArray(net.minecraft.resources.ResourceLocation[]::new));
        };
    }

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> compatHousingModel(
        String modDirectory,
        String housing,
        String... overlays
    ) {
        return (ctx, prov) -> {
            List<net.minecraft.resources.ResourceLocation> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + housing));
            for (String overlay : overlays) {
                textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + overlay));
            }
            prov.generated(ctx::get, textures.toArray(net.minecraft.resources.ResourceLocation[]::new));
        };
    }
}
