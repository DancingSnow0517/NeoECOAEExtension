package cn.dancingsnow.neoecoae.all;

import appeng.api.stacks.AEKeyType;
import appeng.datagen.providers.tags.ConventionTags;
import appeng.items.materials.MaterialItem;
import appeng.recipes.handlers.InscriberRecipeBuilder;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<MaterialItem> RAW_ALUMINUM_ORE = REGISTRATE
        .item("raw_aluminum_ore", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK)
                .unlockedBy("has_raw_aluminum_block", RegistrateRecipeProvider.has(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.ALUMINUM_RAW)
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_INGOT = REGISTRATE
        .item("aluminum_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_INGOT)
        .recipe((ctx, prov) -> {
            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_ORE), RecipeCategory.MISC, ctx, 0.8f);
            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_RAW), RecipeCategory.MISC, ctx, 0.8f);
            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_DUST), RecipeCategory.MISC, ctx, 0.8f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_ORE), RecipeCategory.MISC, ctx, 0.8f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_RAW), RecipeCategory.MISC, ctx, 0.8f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_DUST), RecipeCategory.MISC, ctx, 0.8f);

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.ALUMINUM_STORAGE_BLOCK)
                .unlockedBy("has_aluminum_block", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_STORAGE_BLOCK))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_DUST = REGISTRATE
        .item("aluminum_dust", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_DUST)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.ALUMINUM_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/aluminum_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> RAW_TUNGSTEN_ORE = REGISTRATE
        .item("raw_tungsten_ore", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_raw_tungsten_block", RegistrateRecipeProvider.has(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.TUNGSTEN_RAW)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_INGOT = REGISTRATE
        .item("tungsten_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            prov.smelting(DataIngredient.tag(NETags.Items.TUNGSTEN_ORE), RecipeCategory.MISC, ctx, 1.0f);
            prov.smelting(DataIngredient.tag(NETags.Items.TUNGSTEN_RAW), RecipeCategory.MISC, ctx, 1.0f);
            prov.smelting(DataIngredient.tag(NETags.Items.TUNGSTEN_DUST), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.TUNGSTEN_ORE), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.TUNGSTEN_RAW), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.TUNGSTEN_DUST), RecipeCategory.MISC, ctx, 1.0f);

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_tungsten_block", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.TUNGSTEN_INGOT)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_DUST = REGISTRATE
        .item("tungsten_dust", MaterialItem::new)
        .tag(NETags.Items.TUNGSTEN_DUST)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.TUNGSTEN_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/tungsten_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_INGOT = REGISTRATE
        .item("aluminum_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_ALLOY_INGOT)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK)
                .unlockedBy("has_aluminum_alloy_block", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK))
                .save(prov);

            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_DUST = REGISTRATE
        .item("aluminum_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.IRON_DUST)
                .requires(NETags.Items.ALUMINUM_DUST)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .unlockedBy("has_iron_dust", RegistrateRecipeProvider.has(NETags.Items.IRON_DUST))
                .unlockedBy("has_aluminum_dust", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_DUST))
                .unlockedBy("has_certus_quartz_dust", RegistrateRecipeProvider.has(ConventionTags.CERTUS_QUARTZ_DUST))
                .save(prov);

            InscriberRecipeBuilder.inscribe(NETags.Items.ALUMINUM_ALLOY_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/aluminum_alloy_dust"));
        })
        .tag(NETags.Items.ALUMINUM_ALLOY_DUST)
        .register();

    public static final ItemEntry<MaterialItem> ECO_CELL_HOUSING = REGISTRATE
        .item("eco_cell_housing", MaterialItem::new)
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_16M = REGISTRATE
        .item("eco_item_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.items()
        ))
        .lang("ECO - LE4 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_64M = REGISTRATE
        .item("eco_item_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.items()
        ))
        .lang("ECO - LE6 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_256M = REGISTRATE
        .item("eco_item_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.items()
        ))
        .lang("ECO - LE9 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_16M = REGISTRATE
        .item("eco_fluid_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.fluids()
        ))
        .lang("ECO - LE4 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_64M = REGISTRATE
        .item("eco_fluid_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.fluids()
        ))
        .lang("ECO - LE6 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_256M = REGISTRATE
        .item("eco_fluid_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.fluids()
        ))
        .lang("ECO - LE9 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L4 = createComputationCell(
        "l4",
        ECOTier.L4,
        Rarity.UNCOMMON
    );

    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L6 = createComputationCell(
        "l6",
        ECOTier.L6,
        Rarity.RARE
    );
    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L9 = createComputationCell(
        "l9",
        ECOTier.L9,
        Rarity.EPIC
    );

    private static ItemEntry<ECOComputationCellItem> createComputationCell(
        String tierString,
        IECOTier tier,
        Rarity rarity
    ) {
        return REGISTRATE
            .item("eco_computation_cell_" + tierString, p -> new ECOComputationCellItem(
                p.stacksTo(1).rarity(rarity),
                tier
            ))
            .lang("ECO - %s Flash Crystal Matrix".formatted(tierString.replace("l", "CE")))
            .model((ctx, prov) -> {
            })
            .register();
    }

    public static void register() {

    }
}
