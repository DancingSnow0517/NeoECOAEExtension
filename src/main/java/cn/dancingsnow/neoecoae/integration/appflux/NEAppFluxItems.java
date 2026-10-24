package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.appflux.item.ECOFeStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppFluxItems {

    public static final ItemEntry<MaterialItem> ECO_FE_CELL_HOUSING = REGISTRATE
        .item("eco_fe_cell_housing", MaterialItem::new)

        .lang("ECO Storage Matrix Housing (FE)")
        .model(ItemModelUtil.importedCellModel("eco_energy_cell_housing"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_16M = REGISTRATE
        .item("eco_fe_storage_cell_16m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L4,
            NEAppFluxCellTypes.FLUX
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))

        .lang("ECO - LE4 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "16m"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_64M = REGISTRATE
        .item("eco_fe_storage_cell_64m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L6,
            NEAppFluxCellTypes.FLUX
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))

        .lang("ECO - LE6 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "64m"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_256M = REGISTRATE
        .item("eco_fe_storage_cell_256m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L9,
            NEAppFluxCellTypes.FLUX
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))

        .lang("ECO - LE9 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "256m"))
        .register();

    public static void register() {
    }
}
