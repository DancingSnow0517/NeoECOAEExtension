package cn.dancingsnow.neoecoae.integration.arsenergistique;


import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEArsEnergistiqueItems {

    public static final ItemEntry<MaterialItem> ECO_SOURCE_CELL_HOUSING = REGISTRATE
        .item("eco_source_cell_housing", MaterialItem::new)

        .lang("ECO Storage Matrix Housing (Source)")
        .model(ItemModelUtil.importedCellModel("eco_source_cell_housing"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_16M = REGISTRATE
        .item("eco_source_storage_cell_16m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L4,
            ExternalCellKeyTypes.byId("arseng", "source"),
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))

        .lang("ECO - LE4 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", "16m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_64M = REGISTRATE
        .item("eco_source_storage_cell_64m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L6,
            ExternalCellKeyTypes.byId("arseng", "source"),
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))

        .lang("ECO - LE6 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", "64m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_256M = REGISTRATE
        .item("eco_source_storage_cell_256m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L9,
            ExternalCellKeyTypes.byId("arseng", "source"),
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))

        .lang("ECO - LE9 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", "256m"))
        .register();

    public static void register() {

    }

}
