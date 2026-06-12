package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.item.Item;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemModelUtil {
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> cellModel(String type, String size) {
        return (ctx, prov) -> prov.generated(
            ctx::get,
            prov.modLoc("item/eco_%s_cell_housing".formatted(type)),
            prov.modLoc("item/eco_cell_light_" + size)
        );
    }
}
