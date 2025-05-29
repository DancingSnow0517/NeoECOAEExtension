package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOStorageVentBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.MachineEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.MachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.registration.NEBlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.neoforged.neoforge.capabilities.Capabilities;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEBlockEntities {

    public static final BlockEntityEntry<MachineCasingBlockEntity<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NEComputationCluster>, NEComputationCluster>blockEntityClusterElement(
            "computation_casing",
            e -> null,
            MachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_CASING)
        .validBlock(NEBlocks.COMPUTATION_CASING)
        .register();

    public static final BlockEntityEntry<MachineCasingBlockEntity<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NECraftingCluster>, NECraftingCluster>blockEntityClusterElement(
            "crafting_casing",
            e -> null,
            MachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_CASING)
        .validBlock(NEBlocks.CRAFTING_CASING)
        .register();

    public static final NEBlockEntityEntry<MachineCasingBlockEntity<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NEStorageCluster>, NEStorageCluster>blockEntityClusterElement(
            "storage_casing",
            NEStorageClusterCalculator::new,
            MachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.STORAGE_CASING)
        .validBlock(NEBlocks.STORAGE_CASING)
        .register();

    public static final BlockEntityEntry<MachineInterfaceBlockEntity<NEComputationCluster>> COMPUTATION_INTERFACE = REGISTRATE
        .<MachineInterfaceBlockEntity<NEComputationCluster>, NEComputationCluster>blockEntityClusterElement(
            "computation_interface",
            e -> null,
            MachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_INTERFACE)
        .validBlock(NEBlocks.COMPUTATION_INTERFACE)
        .register();

    public static final BlockEntityEntry<MachineInterfaceBlockEntity<NECraftingCluster>> CRAFTING_INTERFACE = REGISTRATE
        .<MachineInterfaceBlockEntity<NECraftingCluster>, NECraftingCluster>blockEntityClusterElement(
            "crafting_interface",
            e -> null,
            MachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_INTERFACE)
        .validBlock(NEBlocks.CRAFTING_INTERFACE)
        .register();

    public static final NEBlockEntityEntry<MachineInterfaceBlockEntity<NEStorageCluster>> STORAGE_INTERFACE = REGISTRATE
        .<MachineInterfaceBlockEntity<NEStorageCluster>, NEStorageCluster>blockEntityClusterElement(
            "storage_interface",
            NEStorageClusterCalculator::new,
            MachineInterfaceBlockEntity::new
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

    public static final NEBlockEntityEntry<MachineEnergyCellBlockEntity> ENERGY_CELL_L4 = REGISTRATE
        .tierBlockEntityBlockLinked(
            "energy_cell_l4",
            ECOTier.L4,
            MachineEnergyCellBlockEntity::new
        )
        .forBlock(NEBlocks.ENERGY_CELL_L4)
        .validBlock(NEBlocks.ENERGY_CELL_L4)
        .register();

    public static final NEBlockEntityEntry<MachineEnergyCellBlockEntity> ENERGY_CELL_L6 = REGISTRATE
        .tierBlockEntityBlockLinked(
            "energy_cell_l6",
            ECOTier.L6,
            MachineEnergyCellBlockEntity::new
        )
        .forBlock(NEBlocks.ENERGY_CELL_L6)
        .validBlock(NEBlocks.ENERGY_CELL_L6)
        .register();

    public static final NEBlockEntityEntry<MachineEnergyCellBlockEntity> ENERGY_CELL_L9 = REGISTRATE
        .tierBlockEntityBlockLinked(
            "energy_cell_l9",
            ECOTier.L9,
            MachineEnergyCellBlockEntity::new
        )
        .forBlock(NEBlocks.ENERGY_CELL_L9)
        .validBlock(NEBlocks.ENERGY_CELL_L9)
        .register();

    public static void register() {

    }
}
