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
        return (ctx, prov) -> prov.generated(
            ctx::get,
            prov.modLoc("item/eco_%s_cell_housing".formatted(type)),
            prov.modLoc("item/eco_cell_light_" + size),
            prov.modLoc("item/eco_cell_status_light")
        );
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

    /**
     * Icon model for the cells that share the {@code eco_cell_compat} housing set (Omni, MEGA and
     * Lightning matrices). Layer order is fixed: housing, tier light, status light, then any extra
     * overlays. The status light therefore always lands on tint index 2, which is the index
     * {@code NEItemColors} tints, for every cell regardless of how many extra overlays it has.
     */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> compatCellModel(
        String housing,
        String size,
        String... overlays
    ) {
        return (ctx, prov) -> {
            List<net.minecraft.resources.ResourceLocation> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cell_compat/" + housing));
            textures.add(prov.modLoc("item/eco_cell_light_" + size));
            textures.add(prov.modLoc("item/eco_cell_status_light"));
            for (String overlay : overlays) {
                textures.add(prov.modLoc("item/eco_cell_compat/" + overlay));
            }
            prov.generated(ctx::get, textures.toArray(net.minecraft.resources.ResourceLocation[]::new));
        };
    }

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> compatHousingModel(
        String housing,
        String... overlays
    ) {
        return (ctx, prov) -> {
            List<net.minecraft.resources.ResourceLocation> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cell_compat/" + housing));
            for (String overlay : overlays) {
                textures.add(prov.modLoc("item/eco_cell_compat/" + overlay));
            }
            prov.generated(ctx::get, textures.toArray(net.minecraft.resources.ResourceLocation[]::new));
        };
    }
}
