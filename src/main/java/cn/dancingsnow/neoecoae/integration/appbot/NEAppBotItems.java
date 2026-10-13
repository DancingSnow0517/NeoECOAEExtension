package cn.dancingsnow.neoecoae.integration.appbot;

import appbot.ae2.ManaKeyType;
import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import vazkii.botania.common.crafting.ManaInfusionRecipe;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppBotItems {

    public static final ItemEntry<MaterialItem> ECO_MANA_CELL_HOUSING = REGISTRATE
        .item("eco_mana_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput appBotInstalled = prov.withConditions(new ModLoadedCondition(ModIds.APPBOT));
            ManaInfusionRecipe recipe = new ManaInfusionRecipe(
                new ItemStack(ctx.get()),
                Ingredient.of(NEItems.ECO_ITEM_CELL_HOUSING),
                100000,
                null,
                null
            );
            appBotInstalled.accept(ctx.getId().withPrefix("mana_infusion/"), recipe, null);
        })
        .lang("ECO Storage Matrix Housing (Mana)")
        .model(ItemModelUtil.importedCellModel("eco_mana_cell_housing"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_16M = IntegrationCells
        .cell("eco_mana_storage_cell_16m", Size.L4, (p, tier) -> new ECOStorageCellItem(
            p,
            tier,
            ManaKeyType.TYPE,
            NEAppBotCellTypes.MANA
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPBOT, ECO_MANA_CELL_HOUSING, Size.L4))
        .lang("ECO - LE4 Storage Matrix (Mana)")
        .model(ItemModelUtil.cellModel("mana", Size.L4.suffix()))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_64M = IntegrationCells
        .cell("eco_mana_storage_cell_64m", Size.L6, (p, tier) -> new ECOStorageCellItem(
            p,
            tier,
            ManaKeyType.TYPE,
            NEAppBotCellTypes.MANA
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPBOT, ECO_MANA_CELL_HOUSING, Size.L6))
        .lang("ECO - LE6 Storage Matrix (Mana)")
        .model(ItemModelUtil.cellModel("mana", Size.L6.suffix()))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_256M = IntegrationCells
        .cell("eco_mana_storage_cell_256m", Size.L9, (p, tier) -> new ECOStorageCellItem(
            p,
            tier,
            ManaKeyType.TYPE,
            NEAppBotCellTypes.MANA
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPBOT, ECO_MANA_CELL_HOUSING, Size.L9))
        .lang("ECO - LE9 Storage Matrix (Mana)")
        .model(ItemModelUtil.cellModel("mana", Size.L9.suffix()))
        .register();

    public static void register() {

    }
}
