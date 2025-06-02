package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidInputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationTransmitterBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingVentBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageVentBlockEntity;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.registration.NEBlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.neoforged.neoforge.capabilities.Capabilities;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEBlockEntities {

    public static final BlockEntityEntry<ECOMachineCasingBlockEntity<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .<ECOMachineCasingBlockEntity<NEComputationCluster>, NEComputationCluster>blockEntityClusterElement(
            "computation_casing",
            NEComputationClusterCalculator::new,
            ECOMachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_CASING)
        .validBlock(NEBlocks.COMPUTATION_CASING)
        .register();

    public static final BlockEntityEntry<ECOMachineCasingBlockEntity<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .<ECOMachineCasingBlockEntity<NECraftingCluster>, NECraftingCluster>blockEntityClusterElement(
            "crafting_casing",
            NECraftingClusterCalculator::new,
            ECOMachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_CASING)
        .validBlock(NEBlocks.CRAFTING_CASING)
        .register();

    public static final NEBlockEntityEntry<ECOMachineCasingBlockEntity<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .<ECOMachineCasingBlockEntity<NEStorageCluster>, NEStorageCluster>blockEntityClusterElement(
            "storage_casing",
            NEStorageClusterCalculator::new,
            ECOMachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.STORAGE_CASING)
        .validBlock(NEBlocks.STORAGE_CASING)
        .register();

    public static final BlockEntityEntry<ECOMachineInterfaceBlockEntity<NEComputationCluster>> COMPUTATION_INTERFACE = REGISTRATE
        .<ECOMachineInterfaceBlockEntity<NEComputationCluster>, NEComputationCluster>blockEntityClusterElement(
            "computation_interface",
            NEComputationClusterCalculator::new,
            ECOMachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_INTERFACE)
        .validBlock(NEBlocks.COMPUTATION_INTERFACE)
        .register();

    public static final BlockEntityEntry<ECOMachineInterfaceBlockEntity<NECraftingCluster>> CRAFTING_INTERFACE = REGISTRATE
        .<ECOMachineInterfaceBlockEntity<NECraftingCluster>, NECraftingCluster>blockEntityClusterElement(
            "crafting_interface",
            NECraftingClusterCalculator::new,
            ECOMachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_INTERFACE)
        .validBlock(NEBlocks.CRAFTING_INTERFACE)
        .register();

    public static final NEBlockEntityEntry<ECOMachineInterfaceBlockEntity<NEStorageCluster>> STORAGE_INTERFACE = REGISTRATE
        .<ECOMachineInterfaceBlockEntity<NEStorageCluster>, NEStorageCluster>blockEntityClusterElement(
            "storage_interface",
            NEStorageClusterCalculator::new,
            ECOMachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.STORAGE_INTERFACE)
        .validBlock(NEBlocks.STORAGE_INTERFACE)
        .register();

    public static final NEBlockEntityEntry<ECODriveBlockEntity> ECO_DRIVE = REGISTRATE
        .blockEntityBlockLinked(
            "eco_drive",
            ECODriveBlockEntity::new
        )
        .forBlock(NEBlocks.ECO_DRIVE)
        .validBlock(NEBlocks.ECO_DRIVE)
        .renderer(() -> ECODriveRenderer::new)
        .registerCapability(event -> event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            NEBlockEntities.ECO_DRIVE.get(),
            (be, side) -> be.HANDLER
        ))
        .register();

    public static final NEBlockEntityEntry<ECOStorageVentBlockEntity> STORAGE_VENT = REGISTRATE
        .blockEntityBlockLinked(
            "storage_vent",
            ECOStorageVentBlockEntity::new
        )
        .forBlock(NEBlocks.STORAGE_VENT)
        .validBlock(NEBlocks.STORAGE_VENT)
        .register();

    public static final NEBlockEntityEntry<ECOStorageSystemBlockEntity> STORAGE_SYSTEM_L4 = REGISTRATE
        .blockEntityBlockLinked(
            "storage_system_l4",
            ECOStorageSystemBlockEntity::createL4
        )
        .forBlock(NEBlocks.STORAGE_SYSTEM_L4)
        .validBlock(NEBlocks.STORAGE_SYSTEM_L4)
        .register();

    public static final NEBlockEntityEntry<ECOStorageSystemBlockEntity> STORAGE_SYSTEM_L6 = REGISTRATE
        .blockEntityBlockLinked(
            "storage_system_l6",
            ECOStorageSystemBlockEntity::createL6
        )
        .forBlock(NEBlocks.STORAGE_SYSTEM_L6)
        .validBlock(NEBlocks.STORAGE_SYSTEM_L6)
        .register();

    public static final NEBlockEntityEntry<ECOStorageSystemBlockEntity> STORAGE_SYSTEM_L9 = REGISTRATE
        .blockEntityBlockLinked(
            "storage_system_l9",
            ECOStorageSystemBlockEntity::createL9
        )
        .forBlock(NEBlocks.STORAGE_SYSTEM_L9)
        .validBlock(NEBlocks.STORAGE_SYSTEM_L9)
        .register();

    public static final NEBlockEntityEntry<ECOEnergyCellBlockEntity> ENERGY_CELL_L4 = REGISTRATE
        .tierBlockEntityBlockLinked(
            "energy_cell_l4",
            ECOTier.L4,
            ECOEnergyCellBlockEntity::new
        )
        .forBlock(NEBlocks.ENERGY_CELL_L4)
        .validBlock(NEBlocks.ENERGY_CELL_L4)
        .register();

    public static final NEBlockEntityEntry<ECOEnergyCellBlockEntity> ENERGY_CELL_L6 = REGISTRATE
        .tierBlockEntityBlockLinked(
            "energy_cell_l6",
            ECOTier.L6,
            ECOEnergyCellBlockEntity::new
        )
        .forBlock(NEBlocks.ENERGY_CELL_L6)
        .validBlock(NEBlocks.ENERGY_CELL_L6)
        .register();

    public static final NEBlockEntityEntry<ECOEnergyCellBlockEntity> ENERGY_CELL_L9 = REGISTRATE
        .tierBlockEntityBlockLinked(
            "energy_cell_l9",
            ECOTier.L9,
            ECOEnergyCellBlockEntity::new
        )
        .forBlock(NEBlocks.ENERGY_CELL_L9)
        .validBlock(NEBlocks.ENERGY_CELL_L9)
        .register();

    public static final NEBlockEntityEntry<ECOCraftingSystemBlockEntity> CRAFTING_SYSTEM_L4 = createCraftingSystem(
        ECOTier.L4,
        "l4",
        NEBlocks.CRAFTING_SYSTEM_L4
    );
    public static final NEBlockEntityEntry<ECOCraftingSystemBlockEntity> CRAFTING_SYSTEM_L6 = createCraftingSystem(
        ECOTier.L6,
        "l6",
        NEBlocks.CRAFTING_SYSTEM_L6
    );
    public static final NEBlockEntityEntry<ECOCraftingSystemBlockEntity> CRAFTING_SYSTEM_L9 = createCraftingSystem(
        ECOTier.L9,
        "l9",
        NEBlocks.CRAFTING_SYSTEM_L9
    );

    public static final NEBlockEntityEntry<ECOCraftingParallelCoreBlockEntity> CRAFTING_PARALLEL_CORE_L4 = createCraftingParallelCore(
        ECOTier.L4,
        "l4",
        NEBlocks.CRAFTING_PARALLEL_CORE_L4
    );
    public static final NEBlockEntityEntry<ECOCraftingParallelCoreBlockEntity> CRAFTING_PARALLEL_CORE_L6 = createCraftingParallelCore(
        ECOTier.L6,
        "l6",
        NEBlocks.CRAFTING_PARALLEL_CORE_L6
    );
    public static final NEBlockEntityEntry<ECOCraftingParallelCoreBlockEntity> CRAFTING_PARALLEL_CORE_L9 = createCraftingParallelCore(
        ECOTier.L9,
        "l9",
        NEBlocks.CRAFTING_PARALLEL_CORE_L9
    );

    public static final NEBlockEntityEntry<ECOCraftingVentBlockEntity> CRAFTING_VENT = REGISTRATE
        .blockEntityBlockLinked(
            "crafting_vent",
            ECOCraftingVentBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_VENT)
        .validBlock(NEBlocks.CRAFTING_VENT)
        .register();

    public static final NEBlockEntityEntry<ECOFluidInputHatchBlockEntity> INPUT_HATCH = REGISTRATE
        .blockEntityBlockLinked("input_hatch", ECOFluidInputHatchBlockEntity::new)
        .forBlock(NEBlocks.INPUT_HATCH)
        .validBlock(NEBlocks.INPUT_HATCH)
        .serverTicker(ECOFluidInputHatchBlockEntity::tick)
        .registerCapability(event -> {
            event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                NEBlockEntities.INPUT_HATCH.get(),
                (be, side) -> be.getBlockState().getValue(ECOFluidInputHatchBlock.FACING) == side ? be.tank : null
            );
        })
        .register();

    public static final NEBlockEntityEntry<ECOFluidOutputHatchBlockEntity> OUTPUT_HATCH = REGISTRATE
        .blockEntityBlockLinked("output_hatch", ECOFluidOutputHatchBlockEntity::new)
        .forBlock(NEBlocks.OUTPUT_HATCH)
        .validBlock(NEBlocks.OUTPUT_HATCH)
        .serverTicker(ECOFluidOutputHatchBlockEntity::tick)
        .registerCapability(event -> {
            event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                NEBlockEntities.OUTPUT_HATCH.get(),
                (be, side) -> be.getBlockState().getValue(ECOFluidInputHatchBlock.FACING) == side ? be.tank : null
            );
        })
        .register();

    public static final NEBlockEntityEntry<ECOCraftingWorkerBlockEntity> CRAFTING_WORKER = REGISTRATE
        .blockEntityBlockLinked(
            "crafting_worker",
            ECOCraftingWorkerBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_WORKER)
        .validBlock(NEBlocks.CRAFTING_WORKER)
        .register();

    public static final NEBlockEntityEntry<ECOCraftingPatternBusBlockEntity> CRAFTING_PATTERN_BUS = REGISTRATE
        .blockEntityBlockLinked(
            "crafting_pattern_bus",
            ECOCraftingPatternBusBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_PATTERN_BUS)
        .validBlock(NEBlocks.CRAFTING_PATTERN_BUS)
        .registerCapability(e -> e.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            NEBlockEntities.CRAFTING_PATTERN_BUS.get(),
            (be, side) -> be.itemHandler
        ))
        .register();

    public static final NEBlockEntityEntry<ECOComputationTransmitterBlockEntity> COMPUTATION_TRANSMITTER = REGISTRATE
        .blockEntityBlockLinked(
            "computation_transmitter",
            ECOComputationTransmitterBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_TRANSMITTER)
        .validBlock(NEBlocks.COMPUTATION_TRANSMITTER)
        .register();

    public static final NEBlockEntityEntry<ECOComputationParallelCoreBlockEntity> COMPUTATION_PARALLEL_CORE_L4 = createComputationParallelCore(
        ECOTier.L4,
        "l4",
        NEBlocks.COMPUTATION_PARALLEL_CORE_L4
    );

    public static final NEBlockEntityEntry<ECOComputationParallelCoreBlockEntity> COMPUTATION_PARALLEL_CORE_L6 = createComputationParallelCore(
        ECOTier.L6,
        "l6",
        NEBlocks.COMPUTATION_PARALLEL_CORE_L6
    );

    public static final NEBlockEntityEntry<ECOComputationParallelCoreBlockEntity> COMPUTATION_PARALLEL_CORE_L9 = createComputationParallelCore(
        ECOTier.L9,
        "l9",
        NEBlocks.COMPUTATION_PARALLEL_CORE_L9
    );

    public static final NEBlockEntityEntry<ECOComputationThreadingCoreBlockEntity> COMPUTATION_THREADING_CORE_L4 = createComputationThreadingCore(
        ECOTier.L4,
        "l4",
        NEBlocks.COMPUTATION_THREADING_CORE_L4
    );

    public static final NEBlockEntityEntry<ECOComputationThreadingCoreBlockEntity> COMPUTATION_THREADING_CORE_L6 = createComputationThreadingCore(
        ECOTier.L6,
        "l6",
        NEBlocks.COMPUTATION_THREADING_CORE_L6
    );

    public static final NEBlockEntityEntry<ECOComputationThreadingCoreBlockEntity> COMPUTATION_THREADING_CORE_L9 = createComputationThreadingCore(
        ECOTier.L9,
        "l9",
        NEBlocks.COMPUTATION_THREADING_CORE_L9
    );

    public static final NEBlockEntityEntry<ECOComputationCoolingControllerBlockEntity> COMPUTATION_COOLING_CONTROLLER_L4 = createComputationCoolingController(
        ECOTier.L4,
        "l4",
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4
    );

    public static final NEBlockEntityEntry<ECOComputationCoolingControllerBlockEntity> COMPUTATION_COOLING_CONTROLLER_L6 = createComputationCoolingController(
        ECOTier.L6,
        "l6",
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6
    );

    public static final NEBlockEntityEntry<ECOComputationCoolingControllerBlockEntity> COMPUTATION_COOLING_CONTROLLER_L9 = createComputationCoolingController(
        ECOTier.L9,
        "l9",
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9
    );

    public static final NEBlockEntityEntry<ECOComputationSystemBlockEntity> COMPUTATION_SYSTEM_L4 = createComputationSystem(
        ECOTier.L4,
        "l4",
        NEBlocks.COMPUTATION_SYSTEM_L4
    );

    public static final NEBlockEntityEntry<ECOComputationSystemBlockEntity> COMPUTATION_SYSTEM_L6 = createComputationSystem(
        ECOTier.L6,
        "l6",
        NEBlocks.COMPUTATION_SYSTEM_L6
    );

    public static final NEBlockEntityEntry<ECOComputationSystemBlockEntity> COMPUTATION_SYSTEM_L9 = createComputationSystem(
        ECOTier.L9,
        "l9",
        NEBlocks.COMPUTATION_SYSTEM_L9
    );

    public static final NEBlockEntityEntry<ECOComputationDriveBlockEntity> COMPUTATION_DRIVE = REGISTRATE
        .blockEntityBlockLinked(
            "computation_drive",
            ECOComputationDriveBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_DRIVE)
        .validBlock(NEBlocks.COMPUTATION_DRIVE)
        .registerCapability(e -> e.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            NEBlockEntities.COMPUTATION_DRIVE.get(),
            (be, unused) -> be.getItemHandler()
        ))
        .register();

    private static NEBlockEntityEntry<ECOCraftingSystemBlockEntity> createCraftingSystem(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOCraftingSystemBlockEntity>> block
    ) {
        return REGISTRATE
            .tierBlockEntityBlockLinked(
                "crafting_system_" + tierString,
                tier,
                ECOCraftingSystemBlockEntity::new
            )
            .forBlock(block)
            .validBlock(block)
            .register();
    }

    private static NEBlockEntityEntry<ECOCraftingParallelCoreBlockEntity> createCraftingParallelCore(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOCraftingParallelCoreBlockEntity>> block
    ) {
        return REGISTRATE
            .tierBlockEntityBlockLinked(
                "crafting_parallel_core_" + tierString,
                tier,
                ECOCraftingParallelCoreBlockEntity::new
            )
            .forBlock(block)
            .validBlock(block)
            .register();
    }

    private static NEBlockEntityEntry<ECOComputationParallelCoreBlockEntity> createComputationParallelCore(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationParallelCoreBlockEntity>> block
    ) {
        return REGISTRATE
            .tierBlockEntityBlockLinked(
                "computation_parallel_core_" + tierString,
                tier,
                ECOComputationParallelCoreBlockEntity::new
            )
            .forBlock(block)
            .validBlock(block)
            .register();
    }

    private static NEBlockEntityEntry<ECOComputationThreadingCoreBlockEntity> createComputationThreadingCore(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationThreadingCoreBlockEntity>> block
    ) {
        return REGISTRATE
            .tierBlockEntityBlockLinked(
                "computation_threading_core_" + tierString,
                tier,
                ECOComputationThreadingCoreBlockEntity::new
            )
            .forBlock(block)
            .validBlock(block)
            .register();
    }

    private static NEBlockEntityEntry<ECOComputationCoolingControllerBlockEntity> createComputationCoolingController(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationCoolingControllerBlockEntity>> block
    ) {
        return REGISTRATE
            .tierBlockEntityBlockLinked(
                "computation_cooling_controller_" + tierString,
                tier,
                ECOComputationCoolingControllerBlockEntity::new
            )
            .forBlock(block)
            .validBlock(block)
            .register();
    }

    private static NEBlockEntityEntry<ECOComputationSystemBlockEntity> createComputationSystem(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationSystemBlockEntity>> block
    ) {
        return REGISTRATE
            .tierBlockEntityBlockLinked(
                "computation_system_" + tierString,
                tier,
                ECOComputationSystemBlockEntity::new
            )
            .forBlock(block)
            .validBlock(block)
            .register();
    }

    public static void register() {

    }
}
