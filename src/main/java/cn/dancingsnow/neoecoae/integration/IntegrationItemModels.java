package cn.dancingsnow.neoecoae.integration;

import cn.dancingsnow.neoecoae.client.item.ECOStorageCellStateTintSource;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateItemModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Item model generators for optional cell integrations (26.1 replacement for main's {@code ItemModelUtil}). Datagen only. */
public final class IntegrationItemModels {
    private IntegrationItemModels() {
    }

    /** Uses one of the imported, family-specific item models in {@code models/item/cell}. */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> importedCellModel(String model) {
        return (ctx, prov) -> prov.createWithExistingModel(ctx.get(), prov.modLoc("item/cell/" + model));
    }

    /**
     * Icon model for cells whose housing lives under {@code item/eco_cells/<modDirectory>}.
     * Layer order is fixed: housing, tier light, status light, then any extra overlays; the status
     * light (layer 2) carries the storage cell state tint.
     */
    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> compatCellModel(
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
            List<Identifier> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + housing));
            textures.add(prov.modLoc("item/eco_cells/cell_light_" + level));
            textures.add(prov.modLoc("item/eco_cells/cell_status_light"));
            for (String overlay : overlays) {
                textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + overlay));
            }
            Identifier model = layered(ctx.get(), prov, textures);
            ItemTintSource[] tints = new ItemTintSource[textures.size()];
            for (int i = 0; i < tints.length; i++) {
                tints[i] = i == 2 ? ECOStorageCellStateTintSource.INSTANCE : new Constant(-1);
            }
            prov.itemModelOutput.accept(ctx.get(), ItemModelUtils.tintedModel(model, tints));
        };
    }

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> compatHousingModel(
        String modDirectory,
        String housing,
        String... overlays
    ) {
        return (ctx, prov) -> {
            List<Identifier> textures = new ArrayList<>();
            textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + housing));
            for (String overlay : overlays) {
                textures.add(prov.modLoc("item/eco_cells/" + modDirectory + "/" + overlay));
            }
            prov.itemModelOutput.accept(ctx.get(), ItemModelUtils.plainModel(layered(ctx.get(), prov, textures)));
        };
    }

    private static Identifier layered(Item item, RegistrateItemModelGenerator prov, List<Identifier> textures) {
        TextureSlot[] slots = new TextureSlot[textures.size()];
        TextureMapping mapping = new TextureMapping();
        for (int i = 0; i < slots.length; i++) {
            slots[i] = TextureSlot.create("layer" + i);
            mapping.put(slots[i], new net.minecraft.client.resources.model.sprite.Material(textures.get(i)));
        }
        ModelTemplate template = new ModelTemplate(
            Optional.of(Identifier.withDefaultNamespace("item/generated")), Optional.empty(), slots);
        return template.create(item, mapping, prov.modelOutput);
    }
}
