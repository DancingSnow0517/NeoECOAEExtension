package cn.dancingsnow.neoecoae.all;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.datagen.providers.tags.ConventionTags;
import appeng.decorative.solid.CertusQuartzClusterBlock;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipeBuilder;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.*;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationTransmitter;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingPatternBus;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingVent;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidInputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidOutputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.BlockStateUtil;
import cn.dancingsnow.neoecoae.util.LootTableUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.Tags;

import java.util.Locale;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@SuppressWarnings("CodeBlock2Expr")
public class NEBlocks {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final BlockEntry<Block> ALUMINUM_ORE = REGISTRATE
        .block("aluminum_ore", Block::new)
        .initialProperties(() -> Blocks.IRON_ORE)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, NETags.Blocks.ALUMINUM_ORE, Tags.Blocks.ORES)
        .loot((prov, block) -> prov.add(block, prov.createOreDrop(block, NEItems.RAW_ALUMINUM_ORE.get())))
        .item()
        .tag(NETags.Items.ALUMINUM_ORE, Tags.Items.ORES)
        .build()
        .register();

    public static final BlockEntry<Block> RAW_ALUMINUM_BLOCK = REGISTRATE
        .block("raw_aluminum_block", Block::new)
        .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, NETags.Blocks.RAW_ALUMINUM_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NETags.Items.ALUMINUM_RAW)
                .unlockedBy("has_raw_aluminum_ore", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_RAW))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<Block> ALUMINUM_BLOCK = REGISTRATE
        .block("aluminum_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, NETags.Blocks.ALUMINUM_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .unlockedBy("has_aluminum_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.ALUMINUM_STORAGE_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<Block> TUNGSTEN_ORE = REGISTRATE
        .block("tungsten_ore", Block::new)
        .initialProperties(() -> Blocks.IRON_ORE)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_DIAMOND_TOOL, NETags.Blocks.TUNGSTEN_ORE, Tags.Blocks.ORES)
        .loot((prov, block) -> prov.add(block, prov.createOreDrop(block, NEItems.RAW_TUNGSTEN_ORE.get())))
        .item()
        .tag(NETags.Items.TUNGSTEN_ORE, Tags.Items.ORES)
        .build()
        .register();

    public static final BlockEntry<Block> RAW_TUNGSTEN_BLOCK = REGISTRATE
        .block("raw_tungsten_block", Block::new)
        .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_DIAMOND_TOOL, NETags.Blocks.RAW_TUNGSTEN_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NETags.Items.TUNGSTEN_RAW)
                .unlockedBy("has_raw_tungsten_ore", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_RAW))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<Block> TUNGSTEN_BLOCK = REGISTRATE
        .block("tungsten_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, NETags.Blocks.TUNGSTEN_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .unlockedBy("has_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.TUNGSTEN_STORAGE_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<Block> ALUMINUM_ALLOY_BLOCK = REGISTRATE
        .block("aluminum_alloy_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, NETags.Blocks.ALUMINUM_ALLOY_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NETags.Items.ALUMINUM_ALLOY_INGOT)
                .unlockedBy("has_aluminum_alloy_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<CasingBlock> ALUMINUM_ALLOY_CASING = REGISTRATE
        .block("aluminum_alloy_casing", CasingBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL)
        .blockstate((ctx, prov) -> {
            BlockModelBuilder model = prov.models().withExistingParent(ctx.getName(), prov.modLoc("block/casing_base"))
                .texture("base", prov.modLoc("block/" + ctx.getName()))
                .texture("particle", prov.modLoc("block/" + ctx.getName()));
            prov.simpleBlock(ctx.get(), model);
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', NETags.Items.ALUMINUM_ALLOY_INGOT)
                .define('B', AEBlocks.QUARTZ_VIBRANT_GLASS)
                .define('C', NEItems.CRYSTAL_INGOT)
                .unlockedBy("has_aluminum_alloy_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .unlockedBy("has_quartz_vibrant_glass", RegistrateRecipeProvider.has(AEBlocks.QUARTZ_VIBRANT_GLASS))
                .unlockedBy("has_crystal_ingot", RegistrateRecipeProvider.has(NEItems.CRYSTAL_INGOT))
                .save(prov);
        })
        .simpleItem()
        .register();

    public static final BlockEntry<Block> BLACK_TUNGSTEN_ALLOY_BLOCK = REGISTRATE
        .block("black_tungsten_alloy_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, NETags.Blocks.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                .unlockedBy("has_black_tungsten_alloy_ingot", RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<CasingBlock> BLACK_TUNGSTEN_ALLOY_CASING = REGISTRATE
        .block("black_tungsten_alloy_casing", CasingBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL)
        .blockstate((ctx, prov) -> {
            BlockModelBuilder model = prov.models().withExistingParent(ctx.getName(), prov.modLoc("block/casing_base"))
                .texture("base", prov.modLoc("block/" + ctx.getName()))
                .texture("particle", prov.modLoc("block/" + ctx.getName()));
            prov.simpleBlock(ctx.get(), model);
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                .define('B', AEBlocks.QUARTZ_VIBRANT_GLASS)
                .define('C', NEItems.CRYSTAL_INGOT)
                .unlockedBy("has_black_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                .unlockedBy("has_quartz_vibrant_glass", RegistrateRecipeProvider.has(AEBlocks.QUARTZ_VIBRANT_GLASS))
                .unlockedBy("has_crystal_ingot", RegistrateRecipeProvider.has(NEItems.CRYSTAL_INGOT))
                .save(prov);
        })
        .simpleItem()
        .register();

    public static final BlockEntry<Block> ENERGIZED_CRYSTAL_BLOCK = REGISTRATE
        .block("energized_crystal_block", Block::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, NETags.Blocks.ENERGIZED_CRYSTAL_STORAGE_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AA")
                .pattern("AA")
                .define('A', NETags.Items.ENERGIZED_CRYSTAL)
                .unlockedBy("has_energized_crystal", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_CRYSTAL))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.ENERGIZED_CRYSTAL_BLOCK, Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<Block> ENERGIZED_SUPERCONDUCTIVE_BLOCK = REGISTRATE
        .block("energized_superconductive_block", Block::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT)
                .unlockedBy("has_energized_superconductive_ingot", RegistrateRecipeProvider.has(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT))
                .save(prov);
        })
        .item()
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<BuddingEnergizedCrystalBlock> FLAWLESS_BUDDING_ENERGIZED_CRYSTAL = REGISTRATE
        .block("flawless_budding_energized_crystal", BuddingEnergizedCrystalBlock::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .properties(p -> p.randomTicks().mapColor(DyeColor.CYAN))
        .loot((prov, block) -> {
            prov.add(block, prov.createSingleItemTable(NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL));
        })
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.BUDDING_BLOCKS)
        .item()
        .tag(Tags.Items.BUDDING_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<BuddingEnergizedCrystalBlock> FLAWED_BUDDING_ENERGIZED_CRYSTAL = REGISTRATE
        .block("flawed_budding_energized_crystal", BuddingEnergizedCrystalBlock::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .properties(p -> p.randomTicks().mapColor(DyeColor.CYAN))
        .loot((prov, block) -> {
            prov.add(block, prov.createSingleItemTableWithSilkTouch(block, NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL));
        })
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/flawed_budding_energized_crystal"),
                ctx.get(),
                1,
                TransformCircumstance.fluid(FluidTags.WATER),
                Ingredient.of(NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL)
            );
        })
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.BUDDING_BLOCKS)
        .item()
        .tag(Tags.Items.BUDDING_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<BuddingEnergizedCrystalBlock> CHIPPED_BUDDING_ENERGIZED_CRYSTAL = REGISTRATE
        .block("chipped_budding_energized_crystal", BuddingEnergizedCrystalBlock::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .properties(p -> p.randomTicks().mapColor(DyeColor.CYAN))
        .loot((prov, block) -> {
            prov.add(block, prov.createSingleItemTableWithSilkTouch(block, NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL));
        })
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/chipped_budding_energized_crystal"),
                ctx.get(),
                1,
                TransformCircumstance.fluid(FluidTags.WATER),
                Ingredient.of(NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL)
            );
        })
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.BUDDING_BLOCKS)
        .item()
        .tag(Tags.Items.BUDDING_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<BuddingEnergizedCrystalBlock> DAMAGED_BUDDING_ENERGIZED_CRYSTAL = REGISTRATE
        .block("damaged_budding_energized_crystal", BuddingEnergizedCrystalBlock::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .properties(p -> p.randomTicks().mapColor(DyeColor.CYAN))
        .loot((prov, block) -> {
            prov.add(block, prov.createSingleItemTableWithSilkTouch(block, NEBlocks.ENERGIZED_CRYSTAL_BLOCK));
        })
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/damaged_budding_energized_crystal"),
                ctx.get(),
                1,
                TransformCircumstance.fluid(FluidTags.WATER),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_BLOCK),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL)
            );
        })
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.BUDDING_BLOCKS)
        .item()
        .tag(Tags.Items.BUDDING_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<CertusQuartzClusterBlock> SMALL_ENERGIZED_CRYSTAL_BUD = REGISTRATE
        .block("small_energized_crystal_bud", p -> new CertusQuartzClusterBlock(3, 4, p))
        .initialProperties(() -> Blocks.AMETHYST_CLUSTER)
        .properties(p -> p.sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(s -> 1))
        .blockstate((ctx, prov) -> {
            BlockModelBuilder model = prov.models().cross(ctx.getName(), prov.modLoc("block/" + ctx.getName())).renderType("cutout");
            prov.directionalBlock(ctx.get(), model);
        })
        .loot(LootTableUtil::energizedBud)
        .tag(Tags.Blocks.CLUSTERS, BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
        .tag(Tags.Items.CLUSTERS)
        .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("block/" + ctx.getName())))
        .build()
        .register();

    public static final BlockEntry<CertusQuartzClusterBlock> MEDIUM_ENERGIZED_CRYSTAL_BUD = REGISTRATE
        .block("medium_energized_crystal_bud", p -> new CertusQuartzClusterBlock(4, 3, p))
        .initialProperties(() -> Blocks.AMETHYST_CLUSTER)
        .properties(p -> p.sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(s -> 2))
        .blockstate((ctx, prov) -> {
            BlockModelBuilder model = prov.models().cross(ctx.getName(), prov.modLoc("block/" + ctx.getName())).renderType("cutout");
            prov.directionalBlock(ctx.get(), model);
        })
        .loot(LootTableUtil::energizedBud)
        .tag(Tags.Blocks.CLUSTERS, BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
        .tag(Tags.Items.CLUSTERS)
        .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("block/" + ctx.getName())))
        .build()
        .register();

    public static final BlockEntry<CertusQuartzClusterBlock> LARGE_ENERGIZED_CRYSTAL_BUD = REGISTRATE
        .block("large_energized_crystal_bud", p -> new CertusQuartzClusterBlock(5, 3, p))
        .initialProperties(() -> Blocks.AMETHYST_CLUSTER)
        .properties(p -> p.sound(SoundType.LARGE_AMETHYST_BUD).lightLevel(s -> 3))
        .blockstate((ctx, prov) -> {
            BlockModelBuilder model = prov.models().cross(ctx.getName(), prov.modLoc("block/" + ctx.getName())).renderType("cutout");
            prov.directionalBlock(ctx.get(), model);
        })
        .loot(LootTableUtil::energizedBud)
        .tag(Tags.Blocks.CLUSTERS, BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
        .tag(Tags.Items.CLUSTERS)
        .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("block/" + ctx.getName())))
        .build()
        .register();

    public static final BlockEntry<CertusQuartzClusterBlock> ENERGIZED_CRYSTAL_CLUSTER = REGISTRATE
        .block("energized_crystal_cluster", p -> new CertusQuartzClusterBlock(7, 3, p))
        .initialProperties(() -> Blocks.AMETHYST_CLUSTER)
        .properties(p -> p.sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 4))
        .blockstate((ctx, prov) -> {
            BlockModelBuilder model = prov.models().cross(ctx.getName(), prov.modLoc("block/" + ctx.getName())).renderType("cutout");
            prov.directionalBlock(ctx.get(), model);
        })
        .loot(LootTableUtil::energizedCluster)
        .tag(Tags.Blocks.CLUSTERS, BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
        .tag(Tags.Items.CLUSTERS)
        .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("block/" + ctx.getName())))
        .build()
        .register();

    public static final BlockEntry<Block> ENERGIZED_FLUIX_CRYSTAL_BLOCK = REGISTRATE
        .block("energized_fluix_crystal_block", Block::new)
        .initialProperties(() -> Blocks.QUARTZ_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, NETags.Blocks.ENERGIZED_FLUIX_CRYSTAL_BLOCK, Tags.Blocks.STORAGE_BLOCKS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AA")
                .pattern("AA")
                .define('A', NETags.Items.ENERGIZED_FLUIX_CRYSTAL)
                .unlockedBy("has_energized_fluix_crystal", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_FLUIX_CRYSTAL))
                .save(prov);
        })
        .item()
        .tag(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
        .build()
        .register();

    public static final BlockEntry<ECOIntegratedWorkingStation> INTEGRATED_WORKING_STATION = REGISTRATE
        .block("integrated_working_station", ECOIntegratedWorkingStation::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/integrated_working_station"));
            ModelFile modelFileWorking = prov.models().getExistingFile(prov.modLoc("block/integrated_working_station_on"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    boolean working = s.getValue(ECOIntegratedWorkingStation.WORKING);
                    return ConfiguredModel.builder()
                        .modelFile(working ? modelFileWorking : modelFile)
                        .rotationY(((int) s.getValue(ECOIntegratedWorkingStation.FACING).toYRot() + 180) % 360)
                        .build();
                });
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABC")
                .pattern("DEF")
                .pattern("GBH")
                .define('A', AEBlocks.MOLECULAR_ASSEMBLER)
                .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
                .define('C', AEBlocks.CRAFTING_STORAGE_256K)
                .define('D', AEBlocks.CELL_WORKBENCH)
                .define('E', NEBlocks.ALUMINUM_ALLOY_CASING)
                .define('F', AEBlocks.CONDENSER)
                .define('G', AEBlocks.INSCRIBER)
                .define('H', AEBlocks.CHARGER)
                .unlockedBy("has_superconducting_processor", RegistrateRecipeProvider.has(NEItems.SUPERCONDUCTING_PROCESSOR))
                .unlockedBy("has_aluminum_alloy_casing", RegistrateRecipeProvider.has(NEBlocks.ALUMINUM_ALLOY_CASING))
                .save(prov);
        })
        .item()
        .properties(p -> p.rarity(Rarity.RARE))
        .model((ctx, prov) -> {
            prov.withExistingParent(ctx.getName(), prov.modLoc("block/integrated_working_station"));
        })
        .build()
        .lang("ECO Integrated Working Station")
        .register();

    // ************************************ //
    // ********** Storage System ********** //
    // ************************************ //

    //region Storage System
    public static final BlockEntry<ECOStorageSystemBlock> STORAGE_SYSTEM_L4 = createStorageSystem("l4", Rarity.UNCOMMON);
    public static final BlockEntry<ECOStorageSystemBlock> STORAGE_SYSTEM_L6 = createStorageSystem("l6", Rarity.RARE);
    public static final BlockEntry<ECOStorageSystemBlock> STORAGE_SYSTEM_L9 = createStorageSystem("l9", Rarity.EPIC);

    public static final BlockEntry<ECOMachineInterface<NEStorageCluster>> STORAGE_INTERFACE = REGISTRATE
        .block("storage_interface", ECOMachineInterface<NEStorageCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            prov.simpleBlock(ctx.get(), prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName())));
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', NEBlocks.STORAGE_CASING)
                .define('B', AEItems.LOGIC_PROCESSOR)
                .define('C', AEItems.SINGULARITY)
                .define('D', AEBlocks.INTERFACE)
                    .unlockedBy("has_storage_casing", RegistrateRecipeProvider.has(NEBlocks.STORAGE_CASING))
                .save(prov);
        })
        .register();

    public static final BlockEntry<ECOEnergyCellBlock> ENERGY_CELL_L4 = REGISTRATE
        .block("energy_cell_l4", ECOEnergyCellBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, provider) -> {
            provider.getVariantBuilder(ctx.get())
                .forAllStatesExcept(state -> {
                    int level = state.getValue(ECOEnergyCellBlock.LEVEL);
                    return ConfiguredModel.builder()
                        .modelFile(provider.models().getExistingFile(provider.modLoc("block/storage_energy_cell/cell_l4_%d".formatted(level))))
                        .rotationY(((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .build();
                }, ECOEnergyCellBlock.FORMED);
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', AEBlocks.DENSE_ENERGY_CELL)
                .define('B', NEBlocks.STORAGE_CASING)
                .unlockedBy("has_storage_casing", RegistrateRecipeProvider.has(NEBlocks.STORAGE_CASING))
                .save(prov);
        })
        .item()
        .properties(p -> p.rarity(Rarity.UNCOMMON))
        .model((ctx, provider) -> {
            provider.withExistingParent(ctx.getName(), provider.modLoc("block/storage_energy_cell/cell_l4_4"));
        })
        .build()
        .lang("ECO - LT4 High Density Energy Cell")
        .register();

    public static final BlockEntry<ECOEnergyCellBlock> ENERGY_CELL_L6 = REGISTRATE
        .block("energy_cell_l6", ECOEnergyCellBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, provider) -> {
            provider.getVariantBuilder(ctx.get())
                .forAllStatesExcept(state -> {
                    int level = state.getValue(ECOEnergyCellBlock.LEVEL);
                    return ConfiguredModel.builder()
                        .modelFile(provider.models().getExistingFile(provider.modLoc("block/storage_energy_cell/cell_l6_%d".formatted(level))))
                        .rotationY(((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .build();
                }, ECOEnergyCellBlock.FORMED);
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                    .pattern("AAA")
                    .pattern("ABA")
                    .pattern("AAA")
                    .define('A', NEBlocks.ENERGY_CELL_L4)
                    .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
                    .unlockedBy("has_energy_cell_l4", RegistrateRecipeProvider.has(NEBlocks.ENERGY_CELL_L4))
                    .save(prov);
        })
        .item()
        .properties(p -> p.rarity(Rarity.RARE))
        .model((ctx, provider) -> {
            provider.withExistingParent(ctx.getName(), provider.modLoc("block/storage_energy_cell/cell_l6_4"));
        })
        .build()
        .lang("ECO - LT6 High Density Energy Cell")
        .register();

    public static final BlockEntry<ECOEnergyCellBlock> ENERGY_CELL_L9 = REGISTRATE
        .block("energy_cell_l9", ECOEnergyCellBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, provider) -> {
            provider.getVariantBuilder(ctx.get())
                .forAllStatesExcept(state -> {
                    int level = state.getValue(ECOEnergyCellBlock.LEVEL);
                    return ConfiguredModel.builder()
                        .modelFile(provider.models().getExistingFile(provider.modLoc("block/storage_energy_cell/cell_l9_%d".formatted(level))))
                        .rotationY(((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .build();
                }, ECOEnergyCellBlock.FORMED);
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                    .pattern("AAA")
                    .pattern("ABA")
                    .pattern("AAA")
                    .define('A', NEBlocks.ENERGY_CELL_L6)
                    .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
                    .unlockedBy("has_energy_cell_l6", RegistrateRecipeProvider.has(NEBlocks.ENERGY_CELL_L6))
                    .save(prov);
        })
        .item()
        .properties(p -> p.rarity(Rarity.EPIC))
        .model((ctx, provider) -> {
            provider.withExistingParent(ctx.getName(), provider.modLoc("block/storage_energy_cell/cell_l9_4"));
        })
        .build()
        .lang("ECO - LT9 High Density Energy Cell")
        .register();

    public static final BlockEntry<ECODriveBlock> ECO_DRIVE = REGISTRATE
        .block("eco_drive", ECODriveBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, provider) -> {
            provider.getVariantBuilder(ctx.get())
                .forAllStatesExcept(state -> ConfiguredModel.builder()
                    .modelFile(new ModelFile.UncheckedModelFile(provider.modLoc("block/builtin/eco_drive")))
                    .rotationY(((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                    .build(), ECODriveBlock.FORMED, ECODriveBlock.HAS_CELL);
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ADA")
                .pattern("BCB")
                .pattern("ADA")
                .define('A', NEBlocks.STORAGE_CASING)
                .define('B', ConventionTags.SMART_DENSE_CABLE)
                .define('C', AEBlocks.DRIVE)
                .define('D', AEItems.LOGIC_PROCESSOR)
                .unlockedBy("has_storage_casing", RegistrateRecipeProvider.has(NEBlocks.STORAGE_CASING))
                .save(prov);
        })
        .item()
        .model((ctx, provider) -> {
            provider.withExistingParent(ctx.getName(), provider.modLoc("block/eco_drive_empty"));
        })
        .build()
        .lang("ECO - LD Storage Matrix Drive")
        .register();

    public static final BlockEntry<ECOStorageVentBlock> STORAGE_VENT = REGISTRATE
        .block("storage_vent", ECOStorageVentBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()));
            prov.getVariantBuilder(ctx.get())
                .forAllStatesExcept(s ->
                        ConfiguredModel.builder()
                            .modelFile(modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build(),
                    ECOStorageVentBlock.FORMED
                );
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', NEBlocks.STORAGE_CASING)
                .define('B', Blocks.BLUE_ICE)
                .define('C', AEBlocks.QUARTZ_BLOCK)
                .unlockedBy("has_storage_casing", RegistrateRecipeProvider.has(NEBlocks.STORAGE_CASING))
                .save(prov);
        })
        .simpleItem()
        .lang("Storage System Heat Sink")
        .register();

    public static final BlockEntry<ECOMachineCasing<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .block("storage_casing", ECOMachineCasing<NEStorageCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', NEBlocks.ALUMINUM_ALLOY_CASING)
                .define('B', AEBlocks.SKY_STONE_BLOCK)
                .define('C', AEItems.LOGIC_PROCESSOR)
                .define('D', NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_aluminum_alloy_casing", RegistrateRecipeProvider.has(NEBlocks.ALUMINUM_ALLOY_CASING))
                .save(prov);
        })
        .blockstate(BlockStateUtil::simpleExistingBlockState)
        .simpleItem()
        .register();
    //endregion

    // **************************************** //
    // ********** Computation System ********** //
    // **************************************** //

    //region Computation System
    public static final BlockEntry<ECOComputationSystem> COMPUTATION_SYSTEM_L4 = createComputationSystem(
        "l4",
        Rarity.UNCOMMON
    );

    public static final BlockEntry<ECOComputationSystem> COMPUTATION_SYSTEM_L6 = createComputationSystem(
        "l6",
        Rarity.RARE
    );

    public static final BlockEntry<ECOComputationSystem> COMPUTATION_SYSTEM_L9 = createComputationSystem(
        "l9",
        Rarity.EPIC
    );

    public static final BlockEntry<ECOComputationThreadingCore> COMPUTATION_THREADING_CORE_L4 = createComputationThreadingCore(
        "l4",
        ECOTier.L4,
        Rarity.UNCOMMON
    );

    public static final BlockEntry<ECOComputationThreadingCore> COMPUTATION_THREADING_CORE_L6 = createComputationThreadingCore(
        "l6",
        ECOTier.L6,
        Rarity.RARE
    );

    public static final BlockEntry<ECOComputationThreadingCore> COMPUTATION_THREADING_CORE_L9 = createComputationThreadingCore(
        "l9",
        ECOTier.L9,
        Rarity.EPIC
    );

    public static final BlockEntry<ECOComputationParallelCore> COMPUTATION_PARALLEL_CORE_L4 = createComputationParallelCore(
        "l4",
        ECOTier.L4,
        Rarity.UNCOMMON
    );

    public static final BlockEntry<ECOComputationParallelCore> COMPUTATION_PARALLEL_CORE_L6 = createComputationParallelCore(
        "l6",
        ECOTier.L6,
        Rarity.RARE
    );

    public static final BlockEntry<ECOComputationParallelCore> COMPUTATION_PARALLEL_CORE_L9 = createComputationParallelCore(
        "l9",
        ECOTier.L9,
        Rarity.EPIC
    );

    public static final BlockEntry<ECOComputationCoolingController> COMPUTATION_COOLING_CONTROLLER_L4 = createComputationCoolingController(
        "l4",
        Rarity.UNCOMMON
    );

    public static final BlockEntry<ECOComputationCoolingController> COMPUTATION_COOLING_CONTROLLER_L6 = createComputationCoolingController(
        "l6",
        Rarity.RARE
    );

    public static final BlockEntry<ECOComputationCoolingController> COMPUTATION_COOLING_CONTROLLER_L9 = createComputationCoolingController(
        "l9",
        Rarity.EPIC
    );

    public static final BlockEntry<ECOMachineInterface<NEComputationCluster>> COMPUTATION_INTERFACE = REGISTRATE
        .block("computation_interface", ECOMachineInterface<NEComputationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .blockstate(BlockStateUtil::simpleExistingBlockState)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', NEBlocks.COMPUTATION_CASING)
                .define('B', AEItems.CALCULATION_PROCESSOR)
                .define('C', AEItems.SINGULARITY)
                .define('D', AEBlocks.INTERFACE)
                .unlockedBy("has_computation_casing", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_CASING))
                .save(prov);
        })
        .register();

    public static final BlockEntry<ECOComputationTransmitter> COMPUTATION_TRANSMITTER = REGISTRATE
        .block("computation_transmitter", ECOComputationTransmitter::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    ModelFile modelFile;
                    if (s.getValue(ECOComputationTransmitter.FORMED)) {
                        modelFile = prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName() + "_formed"));
                    } else {
                        modelFile = prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()));
                    }
                    return ConfiguredModel.builder()
                        .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .modelFile(modelFile)
                        .build();
                });
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("AEA")
                .define('A', NEBlocks.COMPUTATION_CASING)
                .define('B', NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT)
                .define('C', ConventionTags.COVERED_DENSE_CABLE)
                .define('D', AEBlocks.INTERFACE)
                .define('E', AEItems.CALCULATION_PROCESSOR)
                .unlockedBy("has_computation_casing", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_CASING))
                .unlockedBy("has_energized_superconductive_ingot", RegistrateRecipeProvider.has(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT))
                .save(prov);
        })
        .lang("ECO - CI Superconductive Transmitting Bus")
        .register();

    public static final BlockEntry<ECOComputationDrive> COMPUTATION_DRIVE = REGISTRATE
        .block("computation_drive", ECOComputationDrive::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {
            ModelFile modelFileEmpty = prov.models().getExistingFile(prov.modLoc("block/computation_drive_empty"));
            ModelFile modelFileFull = prov.models().getExistingFile(prov.modLoc("block/computation_drive_full"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    Boolean formed = s.getValue(ECOComputationDrive.FORMED);
                    return ConfiguredModel.builder()
                        .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .modelFile(formed ? modelFileFull : modelFileEmpty)
                        .build();
                });
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("AEA")
                .define('A', NEBlocks.COMPUTATION_CASING)
                .define('B', AEBlocks.CRAFTING_MONITOR)
                .define('C', AEBlocks.CRAFTING_UNIT)
                .define('D', AEBlocks.PATTERN_PROVIDER)
                .define('E', AEItems.CALCULATION_PROCESSOR)
                .unlockedBy("has_computation_casing", RegistrateRecipeProvider.has(NEBlocks.COMPUTATION_CASING))
                .save(prov);
        })
        .item()
        .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_drive_empty")))
        .build()
        .lang("ECO - CD Crystal Matrix Drive")
        .register();

    public static final BlockEntry<ECOMachineCasing<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .block("computation_casing", ECOMachineCasing<NEComputationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', NEBlocks.ALUMINUM_ALLOY_CASING)
                .define('B', AEBlocks.SKY_STONE_BLOCK)
                .define('C', AEItems.CALCULATION_PROCESSOR)
                .define('D', NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_aluminum_alloy_casing", RegistrateRecipeProvider.has(NEBlocks.ALUMINUM_ALLOY_CASING))
                .save(prov);
        })
        .blockstate((ctx, prov) -> {
            prov.simpleBlock(ctx.get(), prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName())));
        })
        .simpleItem()
        .register();
    //endregion

    // ************************************* //
    // ********** Crafting System ********** //
    // ************************************* //

    //region Crafting System
    public static final BlockEntry<ECOCraftingSystem> CRAFTING_SYSTEM_L4 = createCraftingSystem("l4", Rarity.UNCOMMON);
    public static final BlockEntry<ECOCraftingSystem> CRAFTING_SYSTEM_L6 = createCraftingSystem("l6", Rarity.RARE);
    public static final BlockEntry<ECOCraftingSystem> CRAFTING_SYSTEM_L9 = createCraftingSystem("l9", Rarity.EPIC);

    public static final BlockEntry<ECOMachineInterface<NECraftingCluster>> CRAFTING_INTERFACE = REGISTRATE
        .block("crafting_interface", ECOMachineInterface<NECraftingCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', NEBlocks.CRAFTING_CASING)
                .define('B', AEItems.ENGINEERING_PROCESSOR)
                .define('C', AEItems.SINGULARITY)
                .define('D', AEBlocks.INTERFACE)
                .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
                .save(prov);
        })
        .blockstate(BlockStateUtil::simpleExistingBlockState)
        .simpleItem()
        .register();

    public static final BlockEntry<ECOCraftingParallelCore> CRAFTING_PARALLEL_CORE_L4 = createParallelCore(
        "l4",
        ECOTier.L4,
        Rarity.UNCOMMON
    );
    public static final BlockEntry<ECOCraftingParallelCore> CRAFTING_PARALLEL_CORE_L6 = createParallelCore(
        "l6",
        ECOTier.L6,
        Rarity.RARE
    );
    public static final BlockEntry<ECOCraftingParallelCore> CRAFTING_PARALLEL_CORE_L9 = createParallelCore(
        "l9",
        ECOTier.L9,
        Rarity.EPIC
    );

    public static final BlockEntry<ECOCraftingWorker> CRAFTING_WORKER = REGISTRATE
        .block("crafting_worker", ECOCraftingWorker::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("AEA")
                .define('A', AEBlocks.CRAFTING_STORAGE_256K)
                .define('B', AEBlocks.INTERFACE)
                .define('C', AEBlocks.CONTROLLER)
                .define('D', NEBlocks.CRAFTING_CASING)
                .define('E', NEBlocks.CRAFTING_VENT)
                .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
                .unlockedBy("has_crafting_vent", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_VENT))
                .save(prov);
        })
        .item()
        .properties(p -> p.rarity(Rarity.RARE))
        .build()
        .lang("ECO - FX Worker")
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_worker"));
            ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_worker_formed"));
            ModelFile modelFileWorking = prov.models().getExistingFile(prov.modLoc("block/crafting_worker_working"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    Direction facing = s.getValue(ECOCraftingWorker.FACING);
                    boolean formed = s.getValue(ECOCraftingWorker.FORMED);
                    boolean working = s.getValue(ECOCraftingWorker.WORKING);
                    ConfiguredModel.Builder<?> builder = ConfiguredModel.builder()
                        .rotationY((int) ((facing.toYRot() + 180) % 360));
                    if (working) {
                        builder.modelFile(modelFileWorking);
                    } else if (formed) {
                        builder.modelFile(modelFileFormed);
                    } else {
                        builder.modelFile(modelFile);
                    }
                    return builder.build();
                });
        })
        .register();

    public static final BlockEntry<ECOCraftingPatternBus> CRAFTING_PATTERN_BUS = REGISTRATE
        .block("crafting_pattern_bus", ECOCraftingPatternBus::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ADA")
                .define('A', NEBlocks.CRAFTING_CASING)
                .define('B', AEBlocks.PATTERN_PROVIDER)
                .define('C', AEBlocks.INTERFACE)
                .define('D', AEItems.ENGINEERING_PROCESSOR)
                .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
                .save(prov);
        })
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_pattern_bus"));
            ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_pattern_bus_formed"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    boolean formed = s.getValue(ECOCraftingPatternBus.FORMED);
                    return ConfiguredModel.builder()
                        .modelFile(formed ? modelFileFormed : modelFile)
                        .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .build();
                });
        })
        .item()
        .properties(p -> p.rarity(Rarity.RARE))
        .build()
        .lang("ECO - FD Smart Pattern Bus")
        .register();

    public static final BlockEntry<ECOFluidInputHatchBlock> INPUT_HATCH = REGISTRATE
        .block("input_hatch", ECOFluidInputHatchBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate(BlockStateUtil::simpleExistingBlockState)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("ACA")
                .pattern("ABA")
                .define('A', NEBlocks.CRAFTING_CASING)
                .define('B', AEParts.IMPORT_BUS)
                .define('C', AEBlocks.INTERFACE)
                .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
                .save(prov);
        })
        .simpleItem()
        .lang("ECO Fluid Input Hatch")
        .register();

    public static final BlockEntry<ECOFluidOutputHatchBlock> OUTPUT_HATCH = REGISTRATE
        .block("output_hatch", ECOFluidOutputHatchBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate(BlockStateUtil::simpleExistingBlockState)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("ACA")
                .pattern("ABA")
                .define('A', NEBlocks.CRAFTING_CASING)
                .define('B', AEParts.EXPORT_BUS)
                .define('C', AEBlocks.INTERFACE)
                .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
                .save(prov);
        })
        .simpleItem()
        .lang("ECO Fluid Output Hatch")
        .register();

    public static final BlockEntry<ECOCraftingVent> CRAFTING_VENT = REGISTRATE
        .block("crafting_vent", ECOCraftingVent::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_vent"));
            ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_vent_formed"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(
                    s -> {
                        boolean formed = s.getValue(ECOCraftingVent.FORMED);
                        return ConfiguredModel.builder()
                            .modelFile(formed ? modelFileFormed : modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build();
                    }
                );
        })
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', NEBlocks.CRAFTING_CASING)
                .define('B', Blocks.BLUE_ICE)
                .define('C', AEBlocks.QUARTZ_BLOCK)
                .unlockedBy("has_crafting_casing", RegistrateRecipeProvider.has(NEBlocks.CRAFTING_CASING))
                .save(prov);
        })
        .register();

    public static final BlockEntry<ECOMachineCasing<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .block("crafting_casing", ECOMachineCasing<NECraftingCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("CDC")
                .pattern("ABA")
                .define('A', NEBlocks.BLACK_TUNGSTEN_ALLOY_CASING)
                .define('B', AEBlocks.SKY_STONE_BLOCK)
                .define('C', AEItems.ENGINEERING_PROCESSOR)
                .define('D', NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_black_tungsten_alloy_casing", RegistrateRecipeProvider.has(NEBlocks.BLACK_TUNGSTEN_ALLOY_CASING))
                .save(prov);
        })
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_casing"));
            ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_casing_formed"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> ConfiguredModel.builder()
                    .modelFile(s.getValue(ECOMachineCasing.FORMED) ? modelFileFormed : modelFile)
                    .build());
        })
        .simpleItem()
        .register();
    //endregion

    private static BlockEntry<ECOStorageSystemBlock> createStorageSystem(String level, Rarity rarity) {
        return REGISTRATE
            .block("storage_system_" + level, ECOStorageSystemBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_off"));
                ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOStorageSystemBlock.FORMED) ? formedModel : modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build()
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/storage_controller/controller_" + level + "_off"));
            })
            .build()
            .lang("ECO - %s Extensible Storage Subsystem Controller".formatted(level.toUpperCase(Locale.ROOT)))
            .register();
    }

    private static BlockEntry<ECOCraftingParallelCore> createParallelCore(String level, IECOTier tier, Rarity rarity) {
        return REGISTRATE
            .block("crafting_parallel_core_" + level, p -> new ECOCraftingParallelCore(p, tier))
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_core/parallel_core_" + level));
                ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_core/parallel_core_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStatesExcept(
                        s -> {
                            Boolean formed = s.getValue(ECOCraftingParallelCore.FORMED);
                            return ConfiguredModel.builder()
                                .modelFile(formed ? modelFileFormed : modelFile)
                                .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                                .build();
                        }
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/crafting_core/parallel_core_" + level));
            })
            .build()
            .lang("ECO - %s Parallel Core"
                .formatted(level.toUpperCase(Locale.ROOT)).replace("L", "FT")
            )
            .register();
    }

    private static BlockEntry<ECOCraftingSystem> createCraftingSystem(String level, Rarity rarity) {
        return REGISTRATE
            .block("crafting_system_" + level, ECOCraftingSystem::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_" + level + "_off"));
                ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOStorageSystemBlock.FORMED) ? formedModel : modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build()
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/crafting_controller/controller_" + level + "_off"));
            })
            .build()
            .lang("ECO - %s Extensible Crafting Controller".formatted(
                level.toUpperCase(Locale.ROOT)).replace("L", "F"
            ))
            .register();
    }

    private static BlockEntry<ECOComputationSystem> createComputationSystem(String level, Rarity rarity) {
        return REGISTRATE
            .block("computation_system_" + level, ECOComputationSystem::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_" + level + "_off"));
                ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOStorageSystemBlock.FORMED) ? formedModel : modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build()
                    );
            })
            .item()
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_controller/controller_" + level + "_off"));
            })
            .properties(p -> p.rarity(rarity))
            .build()
            .lang("ECO - %s Extensible Computation Subsystem Controller".formatted(
                level.toUpperCase(Locale.ROOT)).replace("L", "C"
            ))
            .register();
    }

    private static BlockEntry<ECOComputationParallelCore> createComputationParallelCore(String level, IECOTier tier, Rarity rarity) {
        return REGISTRATE
            .block("computation_parallel_core_" + level, p -> new ECOComputationParallelCore(p, tier))
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/computation_core/parallel_core_" + level));
                ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/computation_core/parallel_core_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOStorageSystemBlock.FORMED) ? modelFileFormed : modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build()
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_core/parallel_core_" + level)))
            .build()
            .lang("ECO - %s Parallel Core"
                .formatted(level.toUpperCase(Locale.ROOT)).replace("L", "CT")
            )
            .register();
    }

    private static BlockEntry<ECOComputationThreadingCore> createComputationThreadingCore(String level, IECOTier tier, Rarity rarity) {
        return REGISTRATE
            .block("computation_threading_core_" + level, p -> new ECOComputationThreadingCore(p, tier))
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/computation_core/threading_core_" + level));
                ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/computation_core/threading_core_" + level + "_formed"));
                ModelFile modelFileWorking = prov.models().getExistingFile(prov.modLoc("block/computation_core/threading_core_" + level + "_working"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s -> {
                        boolean formed = s.getValue(ECOComputationThreadingCore.FORMED);
                        boolean working = s.getValue(ECOComputationThreadingCore.WORKING);
                        ConfiguredModel.Builder<?> builder = ConfiguredModel.builder()
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360);
                        if (working) {
                            builder.modelFile(modelFileWorking);
                        } else {
                            if (formed) {
                                builder.modelFile(modelFileFormed);
                            } else {
                                builder.modelFile(modelFile);
                            }
                        }
                        return builder.build();
                    });
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_core/threading_core_" + level)))
            .build()
            .lang("ECO - %sA Threading Core"
                .formatted(level.toUpperCase(Locale.ROOT)).replace("L", "CM")
            )
            .register();
    }

    private static BlockEntry<ECOComputationCoolingController> createComputationCoolingController(String level, Rarity rarity) {
        return REGISTRATE
            .block("computation_cooling_controller_" + level, ECOComputationCoolingController::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/computation_cooling_controller/controller_" + level + "_off"));
                ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/computation_cooling_controller/controller_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s -> {
                        boolean formed = s.getValue(ECOComputationThreadingCore.FORMED);
                        ConfiguredModel.Builder<?> builder = ConfiguredModel.builder();
                        if (formed) {
                            builder.modelFile(modelFileFormed)
                                .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 270) % 360);
                        } else {
                            builder.modelFile(modelFile)
                                .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360);
                        }
                        return builder.build();
                    });
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_cooling_controller/controller_" + level + "_off")))
            .build()
            .lang("Cooling System Controller - %s".formatted(level.toUpperCase(Locale.ROOT).replace("L", "C")))
            .register();
    }

    public static void register() {

    }
}
