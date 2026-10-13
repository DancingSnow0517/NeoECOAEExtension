package cn.dancingsnow.neoecoae.all;

import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.ECOFluidHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOVentBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationTransmitterBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECOAdvancedCraftingWorkerRenderer;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.registration.NEBlockEntityBuilder;
import cn.dancingsnow.neoecoae.registration.NEBlockEntityEntry;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

@SuppressWarnings("unused")
public class NEBlockEntities {

    public static final BlockEntityEntry<ECOCraftingNetworkSwitchBlockEntity> CRAFTING_NETWORK_SWITCH = REGISTRATE
        .blockEntity("crafting_network_switch", ECOCraftingNetworkSwitchBlockEntity::new)
        .validBlock(NEBlocks.CRAFTING_NETWORK_SWITCH)
        .validBlock(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH)
        .onRegister(type -> {
            NEBlocks.CRAFTING_NETWORK_SWITCH.get().setBlockEntity(ECOCraftingNetworkSwitchBlockEntity.class, type, null, null);
            NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.get().setBlockEntity(ECOCraftingNetworkSwitchBlockEntity.class, type, null, null);
            AEBaseBlockEntity.registerBlockEntityItem(type, NEBlocks.CRAFTING_NETWORK_SWITCH.asItem());
            AEBaseBlockEntity.registerBlockEntityItem(type, NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.asItem());
        })
        .register();

    public static final BlockEntityEntry<ECOComputationNetworkSwitchBlockEntity> COMPUTATION_NETWORK_SWITCH = REGISTRATE
        .blockEntity("computation_network_switch", ECOComputationNetworkSwitchBlockEntity::new)
        .validBlock(NEBlocks.COMPUTATION_NETWORK_SWITCH)
        .validBlock(NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH)
        .onRegister(type -> {
            NEBlocks.COMPUTATION_NETWORK_SWITCH.get().setBlockEntity(ECOComputationNetworkSwitchBlockEntity.class, type, null, null);
            NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH.get().setBlockEntity(ECOComputationNetworkSwitchBlockEntity.class, type, null, null);
            AEBaseBlockEntity.registerBlockEntityItem(type, NEBlocks.COMPUTATION_NETWORK_SWITCH.asItem());
            AEBaseBlockEntity.registerBlockEntityItem(type, NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH.asItem());
        })
        .register();

    public static final BlockEntityEntry<ECOMachineCasingBlockEntity<NEComputationCluster>> COMPUTATION_CASING = clusterBlockEntity(
        "computation_casing",
        NEComputationClusterCalculator::new,
        ECOMachineCasingBlockEntity::new,
        NEBlocks.COMPUTATION_CASING
    ).register();

    public static final BlockEntityEntry<ECOMachineCasingBlockEntity<NECraftingCluster>> CRAFTING_CASING = clusterBlockEntity(
        "crafting_casing",
        NECraftingClusterCalculator::new,
        ECOMachineCasingBlockEntity::new,
        NEBlocks.CRAFTING_CASING
    ).register();

    public static final NEBlockEntityEntry<ECOMachineCasingBlockEntity<NEStorageCluster>> STORAGE_CASING = clusterBlockEntity(
        "storage_casing",
        NEStorageClusterCalculator::new,
        ECOMachineCasingBlockEntity::new,
        NEBlocks.STORAGE_CASING
    ).register();

    public static final BlockEntityEntry<ECOMachineInterfaceBlockEntity<NEComputationCluster>> COMPUTATION_INTERFACE = clusterBlockEntity(
        "computation_interface",
        NEComputationClusterCalculator::new,
        ECOMachineInterfaceBlockEntity::new,
        NEBlocks.COMPUTATION_INTERFACE
    ).register();

    public static final BlockEntityEntry<ECOMachineInterfaceBlockEntity<NECraftingCluster>> CRAFTING_INTERFACE = clusterBlockEntity(
        "crafting_interface",
        NECraftingClusterCalculator::new,
        ECOMachineInterfaceBlockEntity::new,
        NEBlocks.CRAFTING_INTERFACE
    ).serverTicker(ECOMachineInterfaceBlockEntity::tick).register();

    public static final NEBlockEntityEntry<ECOMachineInterfaceBlockEntity<NEStorageCluster>> STORAGE_INTERFACE = clusterBlockEntity(
        "storage_interface",
        NEStorageClusterCalculator::new,
        ECOMachineInterfaceBlockEntity::new,
        NEBlocks.STORAGE_INTERFACE
    ).register();

    public static final NEBlockEntityEntry<ECODriveBlockEntity> ECO_DRIVE = linkedBlockEntity(
        "eco_drive",
        ECODriveBlockEntity::new,
        NEBlocks.ECO_DRIVE
    ).renderer(() -> ECODriveRenderer::new).register();

    public static final NEBlockEntityEntry<ECOVentBlockEntity<NEStorageCluster>> STORAGE_VENT = clusterBlockEntity(
        "storage_vent",
        NEStorageClusterCalculator::new,
        ECOVentBlockEntity::new,
        NEBlocks.STORAGE_VENT
    ).register();

    public static final NEBlockEntityEntry<ECOStorageSystemBlockEntity> STORAGE_SYSTEM_L4 = linkedBlockEntity(
        "storage_system_l4",
        ECOStorageSystemBlockEntity::createL4,
        NEBlocks.STORAGE_SYSTEM_L4
    ).serverTicker(ECOStorageSystemBlockEntity::tick).register();

    public static final NEBlockEntityEntry<ECOStorageSystemBlockEntity> STORAGE_SYSTEM_L6 = linkedBlockEntity(
        "storage_system_l6",
        ECOStorageSystemBlockEntity::createL6,
        NEBlocks.STORAGE_SYSTEM_L6
    ).serverTicker(ECOStorageSystemBlockEntity::tick).register();

    public static final NEBlockEntityEntry<ECOStorageSystemBlockEntity> STORAGE_SYSTEM_L9 = linkedBlockEntity(
        "storage_system_l9",
        ECOStorageSystemBlockEntity::createL9,
        NEBlocks.STORAGE_SYSTEM_L9
    ).serverTicker(ECOStorageSystemBlockEntity::tick).register();

    public static final NEBlockEntityEntry<ECOEnergyCellBlockEntity> ENERGY_CELL_L4 = tieredBlockEntity(
        "energy_cell_l4",
        ECOTier.L4,
        ECOEnergyCellBlockEntity::new,
        NEBlocks.ENERGY_CELL_L4
    ).register();

    public static final NEBlockEntityEntry<ECOEnergyCellBlockEntity> ENERGY_CELL_L6 = tieredBlockEntity(
        "energy_cell_l6",
        ECOTier.L6,
        ECOEnergyCellBlockEntity::new,
        NEBlocks.ENERGY_CELL_L6
    ).register();

    public static final NEBlockEntityEntry<ECOEnergyCellBlockEntity> ENERGY_CELL_L9 = tieredBlockEntity(
        "energy_cell_l9",
        ECOTier.L9,
        ECOEnergyCellBlockEntity::new,
        NEBlocks.ENERGY_CELL_L9
    ).register();

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

    public static final NEBlockEntityEntry<ECOParallelCoreBlockEntity<NECraftingCluster>> CRAFTING_PARALLEL_CORE_L4 = createCraftingParallelCore(
        ECOTier.L4,
        "l4",
        NEBlocks.CRAFTING_PARALLEL_CORE_L4
    );
    public static final NEBlockEntityEntry<ECOParallelCoreBlockEntity<NECraftingCluster>> CRAFTING_PARALLEL_CORE_L6 = createCraftingParallelCore(
        ECOTier.L6,
        "l6",
        NEBlocks.CRAFTING_PARALLEL_CORE_L6
    );
    public static final NEBlockEntityEntry<ECOParallelCoreBlockEntity<NECraftingCluster>> CRAFTING_PARALLEL_CORE_L9 = createCraftingParallelCore(
        ECOTier.L9,
        "l9",
        NEBlocks.CRAFTING_PARALLEL_CORE_L9
    );

    public static final NEBlockEntityEntry<ECOVentBlockEntity<NECraftingCluster>> CRAFTING_VENT = clusterBlockEntity(
        "crafting_vent",
        NECraftingClusterCalculator::new,
        ECOVentBlockEntity::new,
        NEBlocks.CRAFTING_VENT
    ).register();

    public static final NEBlockEntityEntry<ECOFluidHatchBlockEntity<NECraftingCluster>> INPUT_HATCH = fluidHatchBlockEntity(
        "input_hatch",
        NECraftingClusterCalculator::new,
        ECOFluidHatchBlockEntity.CRAFTING_INPUT,
        NEBlocks.INPUT_HATCH
    ).register();

    public static final NEBlockEntityEntry<ECOFluidHatchBlockEntity<NECraftingCluster>> OUTPUT_HATCH = fluidHatchBlockEntity(
        "output_hatch",
        NECraftingClusterCalculator::new,
        ECOFluidHatchBlockEntity.CRAFTING_OUTPUT,
        NEBlocks.OUTPUT_HATCH
    ).register();

    public static final NEBlockEntityEntry<ECOCraftingWorkerBlockEntity> CRAFTING_WORKER = linkedBlockEntity(
        "crafting_worker",
        ECOCraftingWorkerBlockEntity::new,
        NEBlocks.CRAFTING_WORKER
    ).register();

    public static final NEBlockEntityEntry<ECOCraftingWorkerBlockEntity> FX_MONITOR_CORE = linkedBlockEntity(
        "fx_monitor_core",
        ECOCraftingWorkerBlockEntity::new,
        NEBlocks.FX_MONITOR_CORE
    ).renderer(() -> ECOAdvancedCraftingWorkerRenderer::new).register();

    public static final NEBlockEntityEntry<ECOCraftingPatternBusBlockEntity> CRAFTING_PATTERN_BUS = linkedBlockEntity(
        "crafting_pattern_bus",
        ECOCraftingPatternBusBlockEntity::new,
        NEBlocks.CRAFTING_PATTERN_BUS
    ).register();

    public static final NEBlockEntityEntry<ECOComputationTransmitterBlockEntity> COMPUTATION_TRANSMITTER = linkedBlockEntity(
        "computation_transmitter",
        ECOComputationTransmitterBlockEntity::new,
        NEBlocks.COMPUTATION_TRANSMITTER
    ).register();

    public static final NEBlockEntityEntry<ECOParallelCoreBlockEntity<NEComputationCluster>> COMPUTATION_PARALLEL_CORE_L4 = createComputationParallelCore(
        ECOTier.L4,
        "l4",
        NEBlocks.COMPUTATION_PARALLEL_CORE_L4
    );

    public static final NEBlockEntityEntry<ECOParallelCoreBlockEntity<NEComputationCluster>> COMPUTATION_PARALLEL_CORE_L6 = createComputationParallelCore(
        ECOTier.L6,
        "l6",
        NEBlocks.COMPUTATION_PARALLEL_CORE_L6
    );

    public static final NEBlockEntityEntry<ECOParallelCoreBlockEntity<NEComputationCluster>> COMPUTATION_PARALLEL_CORE_L9 = createComputationParallelCore(
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

    public static final NEBlockEntityEntry<ECOComputationDriveBlockEntity> COMPUTATION_DRIVE = linkedBlockEntity(
        "computation_drive",
        ECOComputationDriveBlockEntity::new,
        NEBlocks.COMPUTATION_DRIVE
    ).register();

    public static final BlockEntityEntry<ECOLargeIntegratedWorkingStationBlockEntity> INTEGRATED_WORKING_STATION_BLOCK = REGISTRATE
        .blockEntity("integrated_working_station", ECOLargeIntegratedWorkingStationBlockEntity::new)
        .validBlock(NEBlocks.INTEGRATED_WORKING_STATION)
        .onRegister(type -> {
            NEBlocks.INTEGRATED_WORKING_STATION.get().setBlockEntity(
                (Class) ECOLargeIntegratedWorkingStationBlockEntity.class,
                (net.minecraft.world.level.block.entity.BlockEntityType) type,
                null,
                null
            );
            AEBaseBlockEntity.registerBlockEntityItem(type, NEBlocks.INTEGRATED_WORKING_STATION.asItem());
        })
        .register();

    public static final BlockEntityEntry<ECOMachineCasingBlockEntity<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_CASING = clusterBlockEntity(
        "large_integrated_working_station_casing",
        NEIntegratedWorkingStationClusterCalculator::new,
        ECOMachineCasingBlockEntity::new,
        NEBlocks.LARGE_INTEGRATED_WORKING_STATION_CASING
    ).register();

    public static final NEBlockEntityEntry<ECOFluidHatchBlockEntity<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH = fluidHatchBlockEntity(
        "large_integrated_working_station_input_hatch",
        NEIntegratedWorkingStationClusterCalculator::new,
        ECOFluidHatchBlockEntity.LWS_INPUT,
        NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH
    ).register();

    public static final NEBlockEntityEntry<ECOFluidHatchBlockEntity<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH = fluidHatchBlockEntity(
        "large_integrated_working_station_output_hatch",
        NEIntegratedWorkingStationClusterCalculator::new,
        ECOFluidHatchBlockEntity.LWS_OUTPUT,
        NEBlocks.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH
    ).register();

    public static final BlockEntityEntry<ECOMachineInterfaceBlockEntity<NEIntegratedWorkingStationCluster>> LARGE_INTEGRATED_WORKING_STATION_INTERFACE = clusterBlockEntity(
        "large_integrated_working_station_interface",
        NEIntegratedWorkingStationClusterCalculator::new,
        ECOLargeIntegratedWorkingStationInterfaceBlockEntity::new,
        NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE
    ).serverTicker(ECOMachineInterfaceBlockEntity::tick).register();

    private static <T extends NEBlockEntity<C, T>, C extends NECluster<C>> NEBlockEntityBuilder<T, NERegistrate> clusterBlockEntity(
        String name,
        NEClusterCalculator.Factory<C> tcFactory,
        NEBlockEntityBuilder.ClusterBlockEntityFactory<T, C> factory,
        BlockEntry<? extends NEBlock<T>> block
    ) {
        return REGISTRATE
            .<T, C>blockEntityClusterElement(name, tcFactory, factory)
            .forBlock(block)
            .validBlock(block);
    }

    private static <T extends NEBlockEntity<C, T>, C extends NECluster<C>> NEBlockEntityBuilder<T, NERegistrate> tieredBlockEntity(
        String name,
        IECOTier tier,
        NEBlockEntityBuilder.TierBlockEntityFactory<T, C> factory,
        BlockEntry<? extends NEBlock<T>> block
    ) {
        return REGISTRATE
            .<T, C>tierBlockEntityBlockLinked(name, tier, factory)
            .forBlock(block)
            .validBlock(block);
    }

    private static <T extends NEBlockEntity<?, T>> NEBlockEntityBuilder<T, NERegistrate> linkedBlockEntity(
        String name,
        BlockEntityBuilder.BlockEntityFactory<T> factory,
        BlockEntry<? extends NEBlock<T>> block
    ) {
        return REGISTRATE
            .blockEntityBlockLinked(name, factory)
            .forBlock(block)
            .validBlock(block);
    }

    private static <C extends NECluster<C>> NEBlockEntityBuilder<ECOFluidHatchBlockEntity<C>, NERegistrate> fluidHatchBlockEntity(
        String name,
        NEClusterCalculator.Factory<C> tcFactory,
        ECOFluidHatchBlockEntity.Spec spec,
        BlockEntry<? extends NEBlock<ECOFluidHatchBlockEntity<C>>> block
    ) {
        return linkedBlockEntity(
            name,
            (type, pos, state) -> new ECOFluidHatchBlockEntity<>(type, pos, state, tcFactory, spec),
            block
        ).serverTicker(ECOFluidHatchBlockEntity::tick);
    }

    private static <C extends NECluster<C>> NEBlockEntityEntry<ECOParallelCoreBlockEntity<C>> createParallelCore(
        String name,
        IECOTier tier,
        NEClusterCalculator.Factory<C> tcFactory,
        double idlePowerUsage,
        BlockEntry<? extends NEBlock<ECOParallelCoreBlockEntity<C>>> block
    ) {
        return tieredBlockEntity(
            name,
            tier,
            (type, pos, state, t) -> new ECOParallelCoreBlockEntity<>(type, pos, state, t, tcFactory, idlePowerUsage),
            block
        ).register();
    }

    private static NEBlockEntityEntry<ECOCraftingSystemBlockEntity> createCraftingSystem(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOCraftingSystemBlockEntity>> block
    ) {
        return tieredBlockEntity("crafting_system_" + tierString, tier, ECOCraftingSystemBlockEntity::new, block)
            .serverTicker(ECOCraftingSystemBlockEntity::tick)
            .register();
    }

    private static NEBlockEntityEntry<ECOParallelCoreBlockEntity<NECraftingCluster>> createCraftingParallelCore(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOParallelCoreBlockEntity<NECraftingCluster>>> block
    ) {
        return createParallelCore("crafting_parallel_core_" + tierString, tier, NECraftingClusterCalculator::new, 64, block);
    }

    private static NEBlockEntityEntry<ECOParallelCoreBlockEntity<NEComputationCluster>> createComputationParallelCore(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOParallelCoreBlockEntity<NEComputationCluster>>> block
    ) {
        return createParallelCore("computation_parallel_core_" + tierString, tier, NEComputationClusterCalculator::new, 16, block);
    }

    private static NEBlockEntityEntry<ECOComputationThreadingCoreBlockEntity> createComputationThreadingCore(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationThreadingCoreBlockEntity>> block
    ) {
        return tieredBlockEntity("computation_threading_core_" + tierString, tier, ECOComputationThreadingCoreBlockEntity::new, block)
            .register();
    }

    private static NEBlockEntityEntry<ECOComputationCoolingControllerBlockEntity> createComputationCoolingController(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationCoolingControllerBlockEntity>> block
    ) {
        return tieredBlockEntity("computation_cooling_controller_" + tierString, tier, ECOComputationCoolingControllerBlockEntity::new, block)
            .register();
    }

    private static NEBlockEntityEntry<ECOComputationSystemBlockEntity> createComputationSystem(
        IECOTier tier,
        String tierString,
        BlockEntry<? extends NEBlock<ECOComputationSystemBlockEntity>> block
    ) {
        return tieredBlockEntity("computation_system_" + tierString, tier, ECOComputationSystemBlockEntity::new, block)
            .serverTicker(ECOComputationSystemBlockEntity::tick)
            .register();
    }

    public static void register() {

    }
}
