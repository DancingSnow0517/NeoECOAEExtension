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

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> compatCellModel(
        String housing,
        String size,
        String... overlays
    ) {
        return (ctx, prov) -> {
            List<net.minecraft.resources.ResourceLocation> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cell_compat/" + housing));
            textures.add(prov.modLoc("item/eco_cell_light_" + size));
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
