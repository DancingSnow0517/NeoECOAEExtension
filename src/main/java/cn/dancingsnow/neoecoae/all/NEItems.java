package cn.dancingsnow.neoecoae.all;

import appeng.api.ids.AETags;
import appeng.api.stacks.AEKeyType;
import appeng.core.definitions.AEItems;
import appeng.datagen.providers.tags.ConventionTags;
import appeng.items.materials.MaterialItem;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipeBuilder;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipeBuilder;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

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
        .tag(NETags.Items.ALUMINUM_RAW, Tags.Items.RAW_MATERIALS)
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_INGOT = REGISTRATE
        .item("aluminum_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
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
        .tag(NETags.Items.ALUMINUM_DUST, Tags.Items.DUSTS)
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
        .tag(NETags.Items.TUNGSTEN_RAW, Tags.Items.RAW_MATERIALS)
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
        .tag(NETags.Items.TUNGSTEN_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_DUST = REGISTRATE
        .item("tungsten_dust", MaterialItem::new)
        .tag(NETags.Items.TUNGSTEN_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.TUNGSTEN_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/tungsten_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_INGOT = REGISTRATE
        .item("aluminum_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_ALLOY_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
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
        .tag(NETags.Items.ALUMINUM_ALLOY_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_INGOT = REGISTRATE
        .item("black_tungsten_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK)
                .unlockedBy("has_black_tungsten_alloy_block", RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK))
                .save(prov);

            prov.smelting(DataIngredient.tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
        })
        .register();

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_DUST = REGISTRATE
        .item("black_tungsten_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.TUNGSTEN_DUST)
                .requires(NETags.Items.ALUMINUM_ALLOY_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .unlockedBy("has_aluminum_dust", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_DUST))
                .unlockedBy("has_tungsten_dust", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_DUST))
                .unlockedBy("has_certus_quartz_dust", RegistrateRecipeProvider.has(ConventionTags.CERTUS_QUARTZ_DUST))
                .save(prov);

            InscriberRecipeBuilder.inscribe(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/black_tungsten_alloy_dust"));
        })
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL = REGISTRATE
        .item("energized_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_crystal_block", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_CRYSTAL_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.ENERGIZED_CRYSTAL, Tags.Items.GEMS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL_DUST = REGISTRATE
        .item("energized_crystal_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.ENERGIZED_CRYSTAL, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/energized_crystal_dust"));
        })
        .tag(NETags.Items.ENERGIZED_CRYSTAL_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL = REGISTRATE
        .item("energized_fluix_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_fluix_crytal_block", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK))
                .save(prov, NeoECOAE.id("energized_fluix_crystal_from_block"));

            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/energized_fluix_crystal"),
                ctx.get(),
                1,
                TransformCircumstance.fluid(FluidTags.WATER),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_DUST),
                Ingredient.of(ConventionTags.FLUIX_CRYSTAL)
            );
        })
        .tag(NETags.Items.ENERGIZED_FLUIX_CRYSTAL, Tags.Items.GEMS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL_DUST = REGISTRATE
        .item("energized_fluix_crystal_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.ENERGIZED_FLUIX_CRYSTAL, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/energized_fluix_crystal_dust"));
        })
        .tag(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> CRYSTAL_INGOT = REGISTRATE
        .item("crystal_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/crystal_ingot"),
                ctx.get(),
                1,
                TransformCircumstance.EXPLOSION,
                Ingredient.of(ConventionTags.CERTUS_QUARTZ_DUST),
                Ingredient.of(ConventionTags.FLUIX_DUST),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_DUST),
                Ingredient.of(NETags.Items.CRYSTAL_INGOT_BASE)
            );
        })
        .register();


    public static final ItemEntry<MaterialItem> CRYSTAL_MATRIX = REGISTRATE
        .item("crystal_matrix", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("A A")
                .pattern(" A ")
                .pattern("A A")
                .define('A', NEItems.CRYSTAL_INGOT)
                .unlockedBy("has_crystal_ingot", RegistrateRecipeProvider.has(NEItems.CRYSTAL_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_SUPERCONDUCTIVE_INGOT = REGISTRATE
        .item("energized_superconductive_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/energized_superconductive_ingot"),
                ctx.get(),
                1,
                TransformCircumstance.EXPLOSION,
                Ingredient.of(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST),
                Ingredient.of(NETags.Items.ALUMINUM_DUST),
                Ingredient.of(ConventionTags.SILICON),
                Ingredient.of(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE)
            );
        })
        .register();

    public static final ItemEntry<MaterialItem> CRYOTHEUM = REGISTRATE
        .item("cryotheum", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(Items.ICE)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .requires(ConventionTags.SKY_STONE_DUST)
                .requires(Items.SNOWBALL)
                .requires(Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_DUST), 4)
                .unlockedBy("has_energized_cryztal_dust", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_CRYSTAL_DUST))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> CRYOTHEUM_CRYSTAL = REGISTRATE
        .item("cryotheum_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', ConventionTags.SKY_STONE_DUST)
                .define('B', NEItems.CRYOTHEUM)
                .unlockedBy("has_cryotheum", RegistrateRecipeProvider.has(NEItems.CRYOTHEUM))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR_PRESS = REGISTRATE
        .item("superconducting_processor_press", MaterialItem::new)
        .tag(ConventionTags.INSCRIBER_PRESSES)
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR_PRINT = REGISTRATE
        .item("superconducting_processor_print", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, ctx.get(), 1)
                .setMode(InscriberProcessType.PRESS)
                .setTop(Ingredient.of(NEItems.SUPERCONDUCTING_PROCESSOR_PRESS))
                .save(prov, NeoECOAE.id("inscriber/superconducting_processor_print"));
        })
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR = REGISTRATE
        .item("superconducting_processor", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(Tags.Items.DUSTS_REDSTONE), ctx.get(), 1)
                .setMode(InscriberProcessType.INSCRIBE)
                .setTop(Ingredient.of(AEItems.SILICON_PRINT))
                .setBottom(Ingredient.of(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT))
                .save(prov, NeoECOAE.id("inscriber/superconducting_processor"));
        })
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
