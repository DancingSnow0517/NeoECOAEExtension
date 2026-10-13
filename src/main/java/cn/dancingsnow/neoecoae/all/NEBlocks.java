package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.BuddingEnergizedCrystalBlock;
import cn.dancingsnow.neoecoae.blocks.CasingBlock;
import cn.dancingsnow.neoecoae.blocks.ECOFluidHatchBlock;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.ECOMachineInterface;
import cn.dancingsnow.neoecoae.blocks.ECOParallelCoreBlock;
import cn.dancingsnow.neoecoae.blocks.ECOVentBlock;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;

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
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationNetworkSwitch;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationTransmitter;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingNetworkSwitch;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingPatternBus;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.BlockStateUtil;
import cn.dancingsnow.neoecoae.util.LootTableUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.providers.loot.RegistrateBlockLootTables;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
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
import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@SuppressWarnings("CodeBlock2Expr")
public class NEBlocks {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final BlockEntry<ECOCraftingNetworkSwitch> CRAFTING_NETWORK_SWITCH = networkSwitch(
        "crafting_network_switch",
        ECOCraftingNetworkSwitch::new,
        "ECO Crafting Subsystem Network Switch Module"
    );

    public static final BlockEntry<ECOCraftingNetworkSwitch> CRAFTING_HIGH_ENERGY_NETWORK_SWITCH = networkSwitch(
        "crafting_high_energy_network_switch",
        ECOCraftingNetworkSwitch::new,
        "ECO Crafting Subsystem High-Energy Network Switch Module"
    );

    public static final BlockEntry<ECOComputationNetworkSwitch> COMPUTATION_NETWORK_SWITCH = networkSwitch(
        "computation_network_switch",
        ECOComputationNetworkSwitch::new,
        "ECO Computation Subsystem Network Switch Module"
    );

    public static final BlockEntry<ECOComputationNetworkSwitch> COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH = networkSwitch(
        "computation_high_energy_network_switch",
        ECOComputationNetworkSwitch::new,
        "ECO Computation Subsystem High-Energy Network Switch Module"
    );

    private static <T extends NENetworkSwitchBlock<?>> BlockEntry<T> networkSwitch(
        String name,
        NonNullFunction<BlockBehaviour.Properties, T> factory,
        String englishName
    ) {
        return REGISTRATE.block(name, factory)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .recipe((ctx, prov) -> {
                if (name.equals("crafting_network_switch")) {
                    IntegratedWorkingStationRecipe.builder()
                        .require(AEItems.SINGULARITY, 2)
                        .require(AEItems.ENGINEERING_PROCESSOR, 16)
                        .require(NEItems.SUPERCONDUCTING_PROCESSOR, 8)
                        .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 16)
                        .require(NEBlocks.CRAFTING_CASING, 8)
                        .require(AEBlocks.MOLECULAR_ASSEMBLER, 4)
                        .require(AEBlocks.CRAFTING_STORAGE_256K, 2)
                        .energy(2_000_000)
                        .itemOutput(ctx.get())
                        .save(prov, ctx.getId().withPrefix("integrated_working_station/"));
                } else if (name.equals("computation_network_switch")) {
                    IntegratedWorkingStationRecipe.builder()
                        .require(AEItems.SINGULARITY, 2)
                        .require(AEItems.CALCULATION_PROCESSOR, 16)
                        .require(NEItems.SUPERCONDUCTING_PROCESSOR, 8)
                        .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 16)
                        .require(NEBlocks.COMPUTATION_CASING, 8)
                        .require(AEBlocks.CELL_WORKBENCH, 4)
                        .require(AEItems.CELL_COMPONENT_256K, 4)
                        .energy(2_000_000)
                        .itemOutput(ctx.get())
                        .save(prov, ctx.getId().withPrefix("integrated_working_station/"));
                } else {
                    boolean crafting = name.equals("crafting_high_energy_network_switch");
                    var recipe = IntegratedWorkingStationRecipe.builder()
                        .require(crafting ? NEBlocks.CRAFTING_NETWORK_SWITCH : NEBlocks.COMPUTATION_NETWORK_SWITCH, 2)
                        .require(AEItems.SINGULARITY, 8)
                        .require(NEItems.SUPERCONDUCTING_PROCESSOR, 32)
                        .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
                        .require(NEBlocks.BLACK_TUNGSTEN_ALLOY_BLOCK, 16)
                        .require(NEItems.ECO_CELL_COMPONENT_256M, 2);
                    if (crafting) {
                        recipe.require(NEItems.CRYSTAL_MATRIX, 4);
                    }
                    recipe.require(AEItems.CELL_COMPONENT_256K, 64)
                        .energy(8_000_000)
                        .itemOutput(ctx.get())
                        .save(prov, ctx.getId().withPrefix("integrated_working_station/"));
                }
            })
            .blockstate((ctx, prov) -> {
                ModelFile model = prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()));
                prov.getVariantBuilder(ctx.get())
                    .forAllStatesExcept(state -> ConfiguredModel.builder()
                        .modelFile(model)
                        .build(), NENetworkSwitchBlock.FORMED);
            })
            .loot((prov, block) -> prov.dropSelf(block))
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/" + ctx.getName())))
            .build()
            .lang(englishName)
            .register();
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

    public static final BlockEntry<Block> RAW_ALUMINUM_BLOCK = createStorageBlock(
        "raw_aluminum_block", Blocks.RAW_IRON_BLOCK, BlockTags.NEEDS_IRON_TOOL,
        NETags.Items.ALUMINUM_RAW, "has_raw_aluminum_ore",
        NETags.Blocks.RAW_ALUMINUM_STORAGE_BLOCK, NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK
    );

    public static final BlockEntry<Block> ALUMINUM_BLOCK = createStorageBlock(
        "aluminum_block", Blocks.IRON_BLOCK, BlockTags.NEEDS_IRON_TOOL,
        NETags.Items.ALUMINUM_INGOT, "has_aluminum_ingot",
        NETags.Blocks.ALUMINUM_STORAGE_BLOCK, NETags.Items.ALUMINUM_STORAGE_BLOCK
    );

    public static final BlockEntry<Block> TUNGSTEN_ORE = REGISTRATE
        .block("tungsten_ore", Block::new)
        .initialProperties(() -> Blocks.IRON_ORE)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_DIAMOND_TOOL, NETags.Blocks.TUNGSTEN_ORE, Tags.Blocks.ORES)
        .loot((prov, block) -> prov.add(block, prov.createOreDrop(block, NEItems.RAW_TUNGSTEN_ORE.get())))
        .item()
        .tag(NETags.Items.TUNGSTEN_ORE, Tags.Items.ORES)
        .build()
        .register();

    // Intentional progression: raw tungsten requires a higher mining tier than the refined storage block.
    public static final BlockEntry<Block> RAW_TUNGSTEN_BLOCK = createStorageBlock(
        "raw_tungsten_block", Blocks.RAW_IRON_BLOCK, BlockTags.NEEDS_DIAMOND_TOOL,
        NETags.Items.TUNGSTEN_RAW, "has_raw_tungsten_ore",
        NETags.Blocks.RAW_TUNGSTEN_STORAGE_BLOCK, NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK
    );

    public static final BlockEntry<Block> TUNGSTEN_BLOCK = createStorageBlock(
        "tungsten_block", Blocks.IRON_BLOCK, BlockTags.NEEDS_IRON_TOOL,
        NETags.Items.TUNGSTEN_INGOT, "has_tungsten_ingot",
        NETags.Blocks.TUNGSTEN_STORAGE_BLOCK, NETags.Items.TUNGSTEN_STORAGE_BLOCK
    );

    public static final BlockEntry<Block> ALUMINUM_ALLOY_BLOCK = createStorageBlock(
        "aluminum_alloy_block", Blocks.IRON_BLOCK, BlockTags.NEEDS_IRON_TOOL,
        NETags.Items.ALUMINUM_ALLOY_INGOT, "has_aluminum_alloy_ingot",
        NETags.Blocks.ALUMINUM_ALLOY_STORAGE_BLOCK, NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK
    );

    public static final BlockEntry<CasingBlock> ALUMINUM_ALLOY_CASING = createAlloyCasing(
        "aluminum_alloy_casing", NETags.Items.ALUMINUM_ALLOY_INGOT, "has_aluminum_alloy_ingot"
    );

    public static final BlockEntry<Block> BLACK_TUNGSTEN_ALLOY_BLOCK = createStorageBlock(
        "black_tungsten_alloy_block", Blocks.IRON_BLOCK, BlockTags.NEEDS_IRON_TOOL,
        NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, "has_black_tungsten_alloy_ingot",
        NETags.Blocks.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK, NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK
    );

    public static final BlockEntry<CasingBlock> BLACK_TUNGSTEN_ALLOY_CASING = createAlloyCasing(
        "black_tungsten_alloy_casing", NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, "has_black_tungsten_ingot"
    );

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

    public static final BlockEntry<BuddingEnergizedCrystalBlock> FLAWED_BUDDING_ENERGIZED_CRYSTAL = createBuddingBlock(
        "flawed_budding_energized_crystal", () -> NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL
    );

    public static final BlockEntry<BuddingEnergizedCrystalBlock> CHIPPED_BUDDING_ENERGIZED_CRYSTAL = createBuddingBlock(
        "chipped_budding_energized_crystal", () -> NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL
    );

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

    public static final BlockEntry<CertusQuartzClusterBlock> SMALL_ENERGIZED_CRYSTAL_BUD = createEnergizedCrystalCluster(
        "small_energized_crystal_bud", 3, 4, SoundType.SMALL_AMETHYST_BUD, 1, LootTableUtil::energizedBud
    );

    public static final BlockEntry<CertusQuartzClusterBlock> MEDIUM_ENERGIZED_CRYSTAL_BUD = createEnergizedCrystalCluster(
        "medium_energized_crystal_bud", 4, 3, SoundType.MEDIUM_AMETHYST_BUD, 2, LootTableUtil::energizedBud
    );

    public static final BlockEntry<CertusQuartzClusterBlock> LARGE_ENERGIZED_CRYSTAL_BUD = createEnergizedCrystalCluster(
        "large_energized_crystal_bud", 5, 3, SoundType.LARGE_AMETHYST_BUD, 3, LootTableUtil::energizedBud
    );

    public static final BlockEntry<CertusQuartzClusterBlock> ENERGIZED_CRYSTAL_CLUSTER = createEnergizedCrystalCluster(
        "energized_crystal_cluster", 7, 3, SoundType.AMETHYST_CLUSTER, 4, LootTableUtil::energizedCluster
    );

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
        .properties(p -> p.strength(5.0F, 6.0F).requiresCorrectToolForDrops())
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/integrated_working_station"));
            ModelFile modelFileWorking = prov.models().getExistingFile(prov.modLoc("block/integrated_working_station_on"));
            ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/large_integrated_working_station_off"));
            ModelFile formedWorkingModel = prov.models().getExistingFile(prov.modLoc("block/large_integrated_working_station_on"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    boolean working = s.getValue(ECOIntegratedWorkingStation.WORKING);
                    boolean formed = s.getValue(ECOIntegratedWorkingStation.FORMED);
                    return ConfiguredModel.builder()
                        .modelFile(formed ? (working ? formedWorkingModel : formedModel) : (working ? modelFileWorking : modelFile))
                        .rotationY(BlockStateUtil.yRotation(s.getValue(ECOIntegratedWorkingStation.FACING)))
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

    public static final BlockEntry<ECOMachineCasing<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_CASING = REGISTRATE
        .block("large_integrated_working_station_casing", ECOMachineCasing<NEIntegratedWorkingStationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.strength(5.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion())
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {})
        .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
            .require(NEBlocks.BLACK_TUNGSTEN_ALLOY_CASING, 4)
            .require(AEBlocks.SKY_STONE_BLOCK, 2)
            .require(AEItems.ENGINEERING_PROCESSOR, 2)
            .require(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
            .require(NEItems.ECO_CELL_COMPONENT_64M)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 8)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 8)
            .itemOutput(ctx.get())
            .energy(256_000)
            .save(prov))
        .item()
        .model((ctx, prov) -> {})
        .build()
        .lang("Large Integrated Working Station Casing")
        .register();

    public static final BlockEntry<ECOFluidHatchBlock<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH = REGISTRATE
        .block("large_integrated_working_station_input_hatch", ECOFluidHatchBlock<NEIntegratedWorkingStationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.strength(5.0F, 6.0F).requiresCorrectToolForDrops())
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {})
        .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
            .require(LARGE_INTEGRATED_WORKING_STATION_CASING, 4)
            .require(AEParts.IMPORT_BUS, 2)
            .require(AEBlocks.INTERFACE)
            .require(NEItems.ECO_FLUID_CELL_HOUSING)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 2)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 2)
            .itemOutput(ctx.get())
            .energy(128_000)
            .save(prov))
        .item()
        .model((ctx, prov) -> {})
        .build()
        .lang("Large Integrated Working Station Coolant Input Hatch")
        .register();

    public static final BlockEntry<ECOFluidHatchBlock<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH = REGISTRATE
        .block("large_integrated_working_station_output_hatch", ECOFluidHatchBlock<NEIntegratedWorkingStationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.strength(5.0F, 6.0F).requiresCorrectToolForDrops())
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {})
        .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
            .require(LARGE_INTEGRATED_WORKING_STATION_CASING, 4)
            .require(AEParts.EXPORT_BUS, 2)
            .require(AEBlocks.INTERFACE)
            .require(NEItems.ECO_FLUID_CELL_HOUSING)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 2)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 2)
            .itemOutput(ctx.get())
            .energy(128_000)
            .save(prov))
        .item()
        .model((ctx, prov) -> {})
        .build()
        .lang("Large Integrated Working Station Coolant Output Hatch")
        .register();

    public static final BlockEntry<ECOMachineInterface<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_INTERFACE = REGISTRATE
        .block("large_integrated_working_station_interface", ECOMachineInterface<NEIntegratedWorkingStationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.strength(5.0F, 6.0F).requiresCorrectToolForDrops())
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {})
        .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
            .require(LARGE_INTEGRATED_WORKING_STATION_CASING, 4)
            .require(AEBlocks.INTERFACE)
            .require(AEItems.SINGULARITY, 2)
            .require(NEItems.ECO_CELL_COMPONENT_64M)
            .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 4)
            .require(NEItems.SUPERCONDUCTING_PROCESSOR, 4)
            .itemOutput(ctx.get())
            .energy(256_000)
            .save(prov))
        .item()
        .model((ctx, prov) -> {})
        .build()
        .lang("Large Integrated Working Station Communication Interface")
        .register();

    // ************************************ //
    // ********** Storage System ********** //
    // ************************************ //

    //region Storage System
    public static final BlockEntry<ECOStorageSystemBlock> STORAGE_SYSTEM_L4 = createStorageSystem(
        "l4", Rarity.UNCOMMON, "ECO - L4 Extensible Storage Subsystem Controller"
    );

    public static final BlockEntry<ECOStorageSystemBlock> STORAGE_SYSTEM_L6 = createStorageSystem(
        "l6", Rarity.RARE, "ECO - L6 Extensible Storage Subsystem Controller"
    );

    public static final BlockEntry<ECOStorageSystemBlock> STORAGE_SYSTEM_L9 = createStorageSystem(
        "l9", Rarity.EPIC, "ECO - L9 Extensible Storage Subsystem Controller"
    );

    public static final BlockEntry<ECOMachineInterface<NEStorageCluster>> STORAGE_INTERFACE = REGISTRATE
        .block("storage_interface", ECOMachineInterface<NEStorageCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            // The three modes share the only storage-interface model currently shipped by the resource pack.
            ModelFile model = prov.models().getExistingFile(prov.modLoc("block/storage_interface"));
            prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
                return ConfiguredModel.builder().modelFile(model).build();
            });
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
                        .rotationY(BlockStateUtil.yRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))
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

    public static final BlockEntry<ECOEnergyCellBlock> ENERGY_CELL_L6 = createEnergyCell(
        "l6", "ECO - LT6 High Density Energy Cell", Rarity.RARE, ENERGY_CELL_L4, "has_energy_cell_l4"
    );

    public static final BlockEntry<ECOEnergyCellBlock> ENERGY_CELL_L9 = createEnergyCell(
        "l9", "ECO - LT9 High Density Energy Cell", Rarity.EPIC, ENERGY_CELL_L6, "has_energy_cell_l6"
    );

    public static final BlockEntry<ECODriveBlock> ECO_DRIVE = REGISTRATE
        .block("eco_drive", ECODriveBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, provider) -> {
            provider.getVariantBuilder(ctx.get())
                .forAllStatesExcept(state -> ConfiguredModel.builder()
                    .modelFile(new ModelFile.UncheckedModelFile(provider.modLoc("block/builtin/eco_drive")))
                    .rotationY(BlockStateUtil.yRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))
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

    public static final BlockEntry<ECOVentBlock<NEStorageCluster>> STORAGE_VENT = REGISTRATE
        .block("storage_vent", ECOVentBlock<NEStorageCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()));
            prov.getVariantBuilder(ctx.get())
                .forAllStatesExcept(s ->
                        ConfiguredModel.builder()
                            .modelFile(modelFile)
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                            .build(),
                    ECOVentBlock.FORMED
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
        "l4", Rarity.UNCOMMON, "ECO - C4 Extensible Computation Subsystem Controller"
    );

    public static final BlockEntry<ECOComputationSystem> COMPUTATION_SYSTEM_L6 = createComputationSystem(
        "l6", Rarity.RARE, "ECO - C6 Extensible Computation Subsystem Controller"
    );

    public static final BlockEntry<ECOComputationSystem> COMPUTATION_SYSTEM_L9 = createComputationSystem(
        "l9", Rarity.EPIC, "ECO - C9 Extensible Computation Subsystem Controller"
    );

    public static final BlockEntry<ECOComputationThreadingCore> COMPUTATION_THREADING_CORE_L4 = createComputationThreadingCore(
        "l4", ECOTier.L4, Rarity.UNCOMMON, "ECO - CM4A Threading Core"
    );

    public static final BlockEntry<ECOComputationThreadingCore> COMPUTATION_THREADING_CORE_L6 = createComputationThreadingCore(
        "l6", ECOTier.L6, Rarity.RARE, "ECO - CM6A Threading Core"
    );

    public static final BlockEntry<ECOComputationThreadingCore> COMPUTATION_THREADING_CORE_L9 = createComputationThreadingCore(
        "l9", ECOTier.L9, Rarity.EPIC, "ECO - CM9A Threading Core"
    );

    public static final BlockEntry<ECOParallelCoreBlock<NEComputationCluster>> COMPUTATION_PARALLEL_CORE_L4 = createParallelCore(
        ECOParallelCoreBlock.Kind.COMPUTATION, "l4", ECOTier.L4, Rarity.UNCOMMON, "ECO - CT4 Parallel Core"
    );

    public static final BlockEntry<ECOParallelCoreBlock<NEComputationCluster>> COMPUTATION_PARALLEL_CORE_L6 = createParallelCore(
        ECOParallelCoreBlock.Kind.COMPUTATION, "l6", ECOTier.L6, Rarity.RARE, "ECO - CT6 Parallel Core"
    );

    public static final BlockEntry<ECOParallelCoreBlock<NEComputationCluster>> COMPUTATION_PARALLEL_CORE_L9 = createParallelCore(
        ECOParallelCoreBlock.Kind.COMPUTATION, "l9", ECOTier.L9, Rarity.EPIC, "ECO - CT9 Parallel Core"
    );

    public static final BlockEntry<ECOComputationCoolingController> COMPUTATION_COOLING_CONTROLLER_L4 = createComputationCoolingController(
        "l4", Rarity.UNCOMMON, "Cooling System Controller - C4"
    );

    public static final BlockEntry<ECOComputationCoolingController> COMPUTATION_COOLING_CONTROLLER_L6 = createComputationCoolingController(
        "l6", Rarity.RARE, "Cooling System Controller - C6"
    );

    public static final BlockEntry<ECOComputationCoolingController> COMPUTATION_COOLING_CONTROLLER_L9 = createComputationCoolingController(
        "l9", Rarity.EPIC, "Cooling System Controller - C9"
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
                        .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
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
                        .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
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
    public static final BlockEntry<ECOCraftingSystem> CRAFTING_SYSTEM_L4 = createCraftingSystem(
        "l4", Rarity.UNCOMMON, "ECO - F4 Extensible Crafting Controller"
    );

    public static final BlockEntry<ECOCraftingSystem> CRAFTING_SYSTEM_L6 = createCraftingSystem(
        "l6", Rarity.RARE, "ECO - F6 Extensible Crafting Controller"
    );

    public static final BlockEntry<ECOCraftingSystem> CRAFTING_SYSTEM_L9 = createCraftingSystem(
        "l9", Rarity.EPIC, "ECO - F9 Extensible Crafting Controller"
    );

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

    public static final BlockEntry<ECOParallelCoreBlock<NECraftingCluster>> CRAFTING_PARALLEL_CORE_L4 = createParallelCore(
        ECOParallelCoreBlock.Kind.CRAFTING, "l4", ECOTier.L4, Rarity.UNCOMMON, "ECO - FT4 Parallel Core"
    );
    public static final BlockEntry<ECOParallelCoreBlock<NECraftingCluster>> CRAFTING_PARALLEL_CORE_L6 = createParallelCore(
        ECOParallelCoreBlock.Kind.CRAFTING, "l6", ECOTier.L6, Rarity.RARE, "ECO - FT6 Parallel Core"
    );
    public static final BlockEntry<ECOParallelCoreBlock<NECraftingCluster>> CRAFTING_PARALLEL_CORE_L9 = createParallelCore(
        ECOParallelCoreBlock.Kind.CRAFTING, "l9", ECOTier.L9, Rarity.EPIC, "ECO - FT9 Parallel Core"
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
                        .rotationY(BlockStateUtil.yRotation(facing));
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

    public static final BlockEntry<ECOCraftingWorker> FX_MONITOR_CORE = REGISTRATE
        .block("fx_monitor_core", ECOCraftingWorker::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .item()
        .properties(p -> p.rarity(Rarity.EPIC))
        .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/crafting_monitor")))
        .build()
        .lang("ECO - FX Monitor Core")
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_monitor"));
            ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_monitor_formed"));
            ModelFile modelFileWorking = prov.models().getExistingFile(prov.modLoc("block/crafting_monitor_working"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(s -> {
                    Direction facing = s.getValue(ECOCraftingWorker.FACING);
                    boolean formed = s.getValue(ECOCraftingWorker.FORMED);
                    boolean working = s.getValue(ECOCraftingWorker.WORKING);
                    ConfiguredModel.Builder<?> builder = ConfiguredModel.builder()
                        .rotationY(BlockStateUtil.yRotation(facing));
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
                        .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                        .build();
                });
        })
        .item()
        .properties(p -> p.rarity(Rarity.RARE))
        .build()
        .lang("ECO - FD Smart Pattern Bus")
        .register();

    public static final BlockEntry<ECOFluidHatchBlock<NECraftingCluster>> INPUT_HATCH = REGISTRATE
        .block("input_hatch", ECOFluidHatchBlock<NECraftingCluster>::new)
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

    public static final BlockEntry<ECOFluidHatchBlock<NECraftingCluster>> OUTPUT_HATCH = REGISTRATE
        .block("output_hatch", ECOFluidHatchBlock<NECraftingCluster>::new)
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

    public static final BlockEntry<ECOVentBlock<NECraftingCluster>> CRAFTING_VENT = REGISTRATE
        .block("crafting_vent", ECOVentBlock<NECraftingCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_vent"));
            ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/crafting_vent_formed"));
            prov.getVariantBuilder(ctx.get())
                .forAllStates(
                    s -> {
                        boolean formed = s.getValue(ECOVentBlock.FORMED);
                        return ConfiguredModel.builder()
                            .modelFile(formed ? modelFileFormed : modelFile)
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
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

    private static BlockEntry<ECOStorageSystemBlock> createStorageSystem(String level, Rarity rarity, String lang) {
        return REGISTRATE
            .block("storage_system_" + level, ECOStorageSystemBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_off"));
                ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed"));
                ModelFile mirroredFormedModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed_mirrored"));
                ModelFile inputModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed_input"));
                ModelFile mirroredInputModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed_input_mirrored"));
                ModelFile outputModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed_output"));
                ModelFile mirroredOutputModel = prov.models().getExistingFile(prov.modLoc("block/storage_controller/controller_" + level + "_formed_output_mirrored"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOStorageSystemBlock.FORMED)
                                ? (s.getValue(ECOStorageSystemBlock.STORAGE_MODE) == cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.INPUT
                                    ? (s.getValue(ECOStorageSystemBlock.MIRRORED) ? mirroredInputModel : inputModel)
                                    : s.getValue(ECOStorageSystemBlock.STORAGE_MODE) == cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.OUTPUT
                                    ? (s.getValue(ECOStorageSystemBlock.MIRRORED) ? mirroredOutputModel : outputModel)
                                    : (s.getValue(ECOStorageSystemBlock.MIRRORED) ? mirroredFormedModel : formedModel))
                                : modelFile)
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                            .build()
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/storage_controller/controller_" + level + "_off"));
            })
            .build()
            .lang(lang)
            .register();
    }

    private static <C extends NECluster<C>> BlockEntry<ECOParallelCoreBlock<C>> createParallelCore(
        ECOParallelCoreBlock.Kind kind, String level, IECOTier tier, Rarity rarity, String lang
    ) {
        String family = kind.name().toLowerCase(Locale.ROOT);
        return REGISTRATE
            .block(family + "_parallel_core_" + level, p -> new ECOParallelCoreBlock<C>(p, tier, kind))
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/" + family + "_core/parallel_core_" + level));
                ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/" + family + "_core/parallel_core_" + level + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(NEBlock.FORMED) ? modelFileFormed : modelFile)
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                            .build()
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/" + family + "_core/parallel_core_" + level));
            })
            .build()
            .lang(lang)
            .register();
    }

    private static BlockEntry<ECOCraftingSystem> createCraftingSystem(String level, Rarity rarity, String lang) {
        return REGISTRATE
            .block("crafting_system_" + level, ECOCraftingSystem::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_" + level + "_off"));
                ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_" + level + "_formed"));
                ModelFile mirroredFormedModel = prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_" + level + "_formed_mirrored"));
                ModelFile networkFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_l9_network_switch_formed")) : formedModel;
                ModelFile networkMirroredFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_l9_network_switch_formed_mirrored")) : mirroredFormedModel;
                ModelFile powerNetworkFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_l9_power_network_switch_formed")) : formedModel;
                ModelFile powerNetworkMirroredFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/crafting_controller/controller_l9_power_network_switch_formed_mirrored")) : mirroredFormedModel;
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOCraftingSystem.FORMED)
                                ? (s.getValue(ECOCraftingSystem.MIRRORED)
                                    ? (s.getValue(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH) ? powerNetworkMirroredFormedModel
                                        : s.getValue(ECOCraftingSystem.NETWORK_SWITCH) ? networkMirroredFormedModel : mirroredFormedModel)
                                    : (s.getValue(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH) ? powerNetworkFormedModel
                                        : s.getValue(ECOCraftingSystem.NETWORK_SWITCH) ? networkFormedModel : formedModel))
                                : modelFile)
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                            .build()
                    );
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/crafting_controller/controller_" + level + "_off"));
            })
            .build()
            .lang(lang)
            .register();
    }

    private static BlockEntry<ECOComputationSystem> createComputationSystem(String level, Rarity rarity, String lang) {
        return REGISTRATE
            .block("computation_system_" + level, ECOComputationSystem::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_" + level + "_off"));
                ModelFile formedModel = prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_" + level + "_formed"));
                ModelFile mirroredFormedModel = prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_" + level + "_formed_mirrored"));
                ModelFile networkFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_l9_network_switch_formed")) : formedModel;
                ModelFile networkMirroredFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_l9_network_switch_formed_mirrored")) : mirroredFormedModel;
                ModelFile powerNetworkFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_l9_power_network_switch_formed")) : formedModel;
                ModelFile powerNetworkMirroredFormedModel = level.equals("l9") ? prov.models().getExistingFile(prov.modLoc("block/computation_controller/controller_l9_power_network_switch_formed_mirrored")) : mirroredFormedModel;
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOComputationSystem.FORMED)
                                ? (s.getValue(ECOComputationSystem.MIRRORED)
                                    ? (s.getValue(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH) ? powerNetworkMirroredFormedModel
                                        : s.getValue(ECOComputationSystem.NETWORK_SWITCH) ? networkMirroredFormedModel : mirroredFormedModel)
                                    : (s.getValue(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH) ? powerNetworkFormedModel
                                        : s.getValue(ECOComputationSystem.NETWORK_SWITCH) ? networkFormedModel : formedModel))
                                : modelFile)
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                            .build()
                    );
            })
            .item()
            .model((ctx, prov) -> {
                prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_controller/controller_" + level + "_off"));
            })
            .properties(p -> p.rarity(rarity))
            .build()
            .lang(lang)
            .register();
    }

    private static BlockEntry<ECOComputationThreadingCore> createComputationThreadingCore(String level, IECOTier tier, Rarity rarity, String lang) {
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
                            .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
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
            .lang(lang)
            .register();
    }

    private static BlockEntry<ECOComputationCoolingController> createComputationCoolingController(String level, Rarity rarity, String lang) {
        return REGISTRATE
            .block("computation_cooling_controller_" + level, ECOComputationCoolingController::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models().getExistingFile(prov.modLoc("block/computation_cooling_controller/controller_" + level + "_off"));
                ModelFile modelFileFormed = prov.models().getExistingFile(prov.modLoc("block/computation_cooling_controller/controller_" + level + "_formed"));
                ModelFile modelFileFormedMirrored = prov.models().getExistingFile(prov.modLoc("block/computation_cooling_controller/controller_" + level + "_formed_mirrored"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s -> {
                        boolean formed = s.getValue(ECOComputationThreadingCore.FORMED);
                        boolean mirrored = s.getValue(ECOComputationCoolingController.MIRRORED);
                        ConfiguredModel.Builder<?> builder = ConfiguredModel.builder();
                        if (formed) {
                            builder.modelFile(mirrored ? modelFileFormedMirrored : modelFileFormed)
                                .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 270) % 360);
                        } else {
                            builder.modelFile(modelFile)
                                .rotationY(BlockStateUtil.yRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
                        }
                        return builder.build();
                    });
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/computation_cooling_controller/controller_" + level + "_off")))
            .build()
            .lang(lang)
            .register();
    }

    private static BlockEntry<Block> createStorageBlock(
        String name,
        Block initialProperties,
        TagKey<Block> harvestTier,
        TagKey<Item> ingredient,
        String criterion,
        TagKey<Block> blockTag,
        TagKey<Item> itemTag
    ) {
        return REGISTRATE
            .block(name, Block::new)
            .initialProperties(() -> initialProperties)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, harvestTier, blockTag, Tags.Blocks.STORAGE_BLOCKS)
            .recipe((ctx, prov) -> {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                    .pattern("AAA")
                    .pattern("AAA")
                    .pattern("AAA")
                    .define('A', ingredient)
                    .unlockedBy(criterion, RegistrateRecipeProvider.has(ingredient))
                    .save(prov);
            })
            .item()
            .tag(itemTag, Tags.Items.STORAGE_BLOCKS)
            .build()
            .register();
    }

    private static BlockEntry<CasingBlock> createAlloyCasing(String name, TagKey<Item> ingredient, String criterion) {
        return REGISTRATE
            .block(name, CasingBlock::new)
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
                    .define('A', ingredient)
                    .define('B', AEBlocks.QUARTZ_VIBRANT_GLASS)
                    .define('C', NEItems.CRYSTAL_INGOT)
                    .unlockedBy(criterion, RegistrateRecipeProvider.has(ingredient))
                    .unlockedBy("has_quartz_vibrant_glass", RegistrateRecipeProvider.has(AEBlocks.QUARTZ_VIBRANT_GLASS))
                    .unlockedBy("has_crystal_ingot", RegistrateRecipeProvider.has(NEItems.CRYSTAL_INGOT))
                    .save(prov);
            })
            .simpleItem()
            .register();
    }

    // previous is a Supplier because the referenced budding stage is declared later in this class.
    private static BlockEntry<BuddingEnergizedCrystalBlock> createBuddingBlock(String name, Supplier<ItemLike> previous) {
        return REGISTRATE
            .block(name, BuddingEnergizedCrystalBlock::new)
            .initialProperties(() -> Blocks.QUARTZ_BLOCK)
            .properties(p -> p.randomTicks().mapColor(DyeColor.CYAN))
            .loot((prov, block) -> {
                prov.add(block, prov.createSingleItemTableWithSilkTouch(block, previous.get()));
            })
            .recipe((ctx, prov) -> {
                TransformRecipeBuilder.transform(
                    prov,
                    NeoECOAE.id("transform/" + name),
                    ctx.get(),
                    1,
                    TransformCircumstance.fluid(FluidTags.WATER),
                    Ingredient.of(previous.get()),
                    Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL)
                );
            })
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.BUDDING_BLOCKS)
            .item()
            .tag(Tags.Items.BUDDING_BLOCKS)
            .build()
            .register();
    }

    private static BlockEntry<CertusQuartzClusterBlock> createEnergizedCrystalCluster(
        String name,
        int height,
        int inset,
        SoundType sound,
        int lightLevel,
        NonNullBiConsumer<RegistrateBlockLootTables, CertusQuartzClusterBlock> loot
    ) {
        return REGISTRATE
            .block(name, p -> new CertusQuartzClusterBlock(height, inset, p))
            .initialProperties(() -> Blocks.AMETHYST_CLUSTER)
            .properties(p -> p.sound(sound).lightLevel(s -> lightLevel))
            .blockstate((ctx, prov) -> {
                BlockModelBuilder model = prov.models().cross(ctx.getName(), prov.modLoc("block/" + ctx.getName())).renderType("cutout");
                prov.directionalBlock(ctx.get(), model);
            })
            .loot(loot)
            .tag(Tags.Blocks.CLUSTERS, BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tag(Tags.Items.CLUSTERS)
            .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("block/" + ctx.getName())))
            .build()
            .register();
    }

    // previous is an ItemLike: the lower tier is always declared before the tier that references it.
    private static BlockEntry<ECOEnergyCellBlock> createEnergyCell(String tier, String lang, Rarity rarity, ItemLike lowerCell, String criterion) {
        return REGISTRATE
            .block("energy_cell_" + tier, ECOEnergyCellBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, provider) -> {
                provider.getVariantBuilder(ctx.get())
                    .forAllStatesExcept(state -> {
                        int level = state.getValue(ECOEnergyCellBlock.LEVEL);
                        return ConfiguredModel.builder()
                            .modelFile(provider.models().getExistingFile(provider.modLoc(("block/storage_energy_cell/cell_" + tier + "_%d").formatted(level))))
                            .rotationY(BlockStateUtil.yRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))
                            .build();
                    }, ECOEnergyCellBlock.FORMED);
            })
            .recipe((ctx, prov) -> {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                    .pattern("AAA")
                    .pattern("ABA")
                    .pattern("AAA")
                    .define('A', lowerCell)
                    .define('B', NEItems.SUPERCONDUCTING_PROCESSOR)
                    .unlockedBy(criterion, RegistrateRecipeProvider.has(lowerCell))
                    .save(prov);
            })
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, provider) -> {
                provider.withExistingParent(ctx.getName(), provider.modLoc("block/storage_energy_cell/cell_" + tier + "_4"));
            })
            .build()
            .lang(lang)
            .register();
    }

    public static void register() {

    }
}
