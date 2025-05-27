package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.registration.NEBlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEBlockEntities {

    public static final BlockEntityEntry<MachineCasingBlockEntity<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NEComputationCluster>>blockEntityBlockLinked(
            "computation_casing",
            (t, p, b) -> new MachineCasingBlockEntity<>(
                t,
                p,
                b,
                () -> null
            )
        )
        .forBlock(NEBlocks.COMPUTATION_CASING)
        .validBlock(NEBlocks.COMPUTATION_CASING)
        .register();

    public static final BlockEntityEntry<MachineCasingBlockEntity<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NECraftingCluster>>blockEntityBlockLinked(
            "crafting_casing",
            (t, p, b) -> new MachineCasingBlockEntity<>(
                t,
                p,
                b,
                () -> null
            )
        )
        .forBlock(NEBlocks.CRAFTING_CASING)
        .validBlock(NEBlocks.CRAFTING_CASING)
        .register();

    public static final NEBlockEntityEntry<MachineCasingBlockEntity<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .<MachineCasingBlockEntity<NEStorageCluster>>blockEntityBlockLinked(
            "storage_casing",
            (t, p, b) -> new MachineCasingBlockEntity<>(
                t,
                p,
                b,
                () -> null
            )
        )
        .forBlock(NEBlocks.STORAGE_CASING)
        .validBlock(NEBlocks.STORAGE_CASING)
        .register();

    public static void register() {

    }
}
