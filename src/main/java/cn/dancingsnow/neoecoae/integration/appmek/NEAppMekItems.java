package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.items.materials.MaterialItem;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOAETypeCounts;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
public class NEAppMekItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<MaterialItem> ECO_CHEMICAL_CELL_HOUSING = REGISTRATE
        .item("eco_chemical_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput appmekInstalled = prov.withConditions(new ModLoadedCondition("appmek"));
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_black_tungsten_alloy", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .save(appmekInstalled);
        })
        .lang("ECO Storage Matrix Housing (Chemical)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_CHEMICAL_CELL_16M = REGISTRATE
        .item("eco_chemical_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            MekanismKeyType.TYPE
        ))
        .recipe((ctx, prov) -> {
            RecipeOutput appmekInstalled = prov.withConditions(new ModLoadedCondition("appmek"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(appmekInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_16M.asStack()));
            appmekInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Chemical)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_CHEMICAL_CELL_64M = REGISTRATE
        .item("eco_chemical_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            MekanismKeyType.TYPE
        ))
        .recipe((ctx, prov) -> {
            RecipeOutput appmekInstalled = prov.withConditions(new ModLoadedCondition("appmek"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(appmekInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_64M.asStack()));
            appmekInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Chemical)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_CHEMICAL_CELL_256M = REGISTRATE
        .item("eco_chemical_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            MekanismKeyType.TYPE
        ))
        .recipe((ctx, prov) -> {
            RecipeOutput appmekInstalled = prov.withConditions(new ModLoadedCondition("appmek"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(appmekInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_256M.asStack()));
            appmekInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (Chemical)")
        .register();

    public static void register() {
        ECOAETypeCounts.register(MekanismKeyType.TYPE, 25);
    }
}
