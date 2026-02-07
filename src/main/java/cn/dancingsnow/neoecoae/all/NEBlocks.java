package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.ECOMachineInterface;
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
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import java.util.Locale;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@SuppressWarnings("CodeBlock2Expr")
public class NEBlocks {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }


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
        .simpleItem()
        .lang("Storage System Heat Sink")
        .register();

    public static final BlockEntry<ECOMachineCasing<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .block("storage_casing", ECOMachineCasing<NEStorageCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
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
        .simpleItem()
        .lang("ECO Fluid Input Hatch")
        .register();

    public static final BlockEntry<ECOFluidOutputHatchBlock> OUTPUT_HATCH = REGISTRATE
        .block("output_hatch", ECOFluidOutputHatchBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate(BlockStateUtil::simpleExistingBlockState)
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
        .register();

    public static final BlockEntry<ECOMachineCasing<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .block("crafting_casing", ECOMachineCasing<NECraftingCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
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
        .item()
        .properties(p -> p.rarity(Rarity.RARE))
        .model((ctx, prov) -> {
            prov.withExistingParent(ctx.getName(), prov.modLoc("block/integrated_working_station"));
        })
        .build()
        .lang("ECO - Integrated Working Station")
        .register();

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
