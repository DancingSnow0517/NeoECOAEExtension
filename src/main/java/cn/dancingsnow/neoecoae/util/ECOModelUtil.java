package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.providers.generators.RegistrateItemModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ECOModelUtil {
    public static final ModelTemplate CASING = createTemplate("casing_base", TextureSlot.TEXTURE);

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> cellModel(String type, String size) {
        return (ctx, prov) -> prov.generateWithTemplate(
            ctx.get(),
            ModelTemplates.TWO_LAYERED_ITEM,
            TextureMapping.layered(
                prov.modItemTexture("eco_%s_cell_housing".formatted(type)),
                prov.modItemTexture("eco_cell_light_" + size)
            )
        );
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> casing() {
        return (ctx, prov) -> {
            prov.generateWithTemplate(
                ctx.get(),
                CASING,
                TextureMapping.singleSlot(TextureSlot.TEXTURE, new Material(ctx.getId().withPrefix("block/")))
            );
        };
    }

    private static ModelTemplate createTemplate(String id, TextureSlot... slots) {
        return new ModelTemplate(Optional.of(NeoECOAE.id(id).withPrefix("block/")), Optional.empty(), slots);
    }
}
