package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.entity.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.MachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.client.renderer.blockentity.ECODriveRenderer;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.registration.NEBlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEBlockEntities {

    public static final BlockEntityEntry<MachineCasingBlockEntity<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NEComputationCluster>, NEComputationCluster>blockEntityClusterElement(
            "computation_casing",
            () -> null,
            MachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_CASING)
        .validBlock(NEBlocks.COMPUTATION_CASING)
        .register();

    public static final BlockEntityEntry<MachineCasingBlockEntity<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NECraftingCluster>, NECraftingCluster>blockEntityClusterElement(
            "crafting_casing",
            () -> null,
            MachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_CASING)
        .validBlock(NEBlocks.CRAFTING_CASING)
        .register();

    public static final NEBlockEntityEntry<MachineCasingBlockEntity<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NEStorageCluster>, NEStorageCluster>blockEntityClusterElement(
            "storage_casing",
            () -> null,
            MachineCasingBlockEntity::new
        )
        .forBlock(NEBlocks.STORAGE_CASING)
        .validBlock(NEBlocks.STORAGE_CASING)
        .register();

    public static final BlockEntityEntry<MachineInterfaceBlockEntity<NEComputationCluster>> COMPUTATION_INTERFACE = REGISTRATE
        .<MachineInterfaceBlockEntity<NEComputationCluster>, NEComputationCluster>blockEntityClusterElement(
            "computation_interface",
            () -> null,
            MachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.COMPUTATION_INTERFACE)
        .validBlock(NEBlocks.COMPUTATION_INTERFACE)
        .register();

    public static final BlockEntityEntry<MachineInterfaceBlockEntity<NECraftingCluster>> CRAFTING_INTERFACE = REGISTRATE
        .<MachineInterfaceBlockEntity<NECraftingCluster>, NECraftingCluster>blockEntityClusterElement(
            "crafting_interface",
            () -> null,
            MachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.CRAFTING_INTERFACE)
        .validBlock(NEBlocks.CRAFTING_INTERFACE)
        .register();

    public static final NEBlockEntityEntry<MachineInterfaceBlockEntity<NEStorageCluster>> STORAGE_INTERFACE = REGISTRATE
        .<MachineInterfaceBlockEntity<NEStorageCluster>, NEStorageCluster>blockEntityClusterElement(
            "storage_interface",
            () -> null,
            MachineInterfaceBlockEntity::new
        )
        .forBlock(NEBlocks.STORAGE_INTERFACE)
        .validBlock(NEBlocks.STORAGE_INTERFACE)
        .register();

    public static final NEBlockEntityEntry<ECODriveBlockEntity> ECO_DRIVE = REGISTRATE
        .blockEntityClusterElement(
            "eco_drive",
            () -> null,
            ECODriveBlockEntity::new
        )
        .forBlock(NEBlocks.ECO_DRIVE)
        .validBlock(NEBlocks.ECO_DRIVE)
        .renderer(() -> ECODriveRenderer::new)
        .register();

    public static void register() {

    }
}
