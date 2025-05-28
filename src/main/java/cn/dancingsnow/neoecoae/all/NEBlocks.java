package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.MachineCasing;
import cn.dancingsnow.neoecoae.blocks.MachineInterface;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEBlocks {

    static {
        REGISTRATE.defaultCreativeTab("neoecoae");
    }

    public static final BlockEntry<MachineCasing<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .block("computation_casing", MachineCasing<NEComputationCluster>::new)
        .simpleItem()
        .register();

    public static final BlockEntry<MachineCasing<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .block("crafting_casing", MachineCasing<NECraftingCluster>::new)
        .simpleItem()
        .register();

    public static final BlockEntry<MachineCasing<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .block("storage_casing", MachineCasing<NEStorageCluster>::new)
        .simpleItem()
        .register();

    public static final BlockEntry<MachineInterface<NEComputationCluster>> COMPUTATION_INTERFACE = REGISTRATE
        .block("computation_interface", MachineInterface<NEComputationCluster>::new)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            prov.simpleBlock(
                ctx.get(),
                prov.models().cubeColumn(
                    ctx.getName(),
                    prov.modLoc("block/" + ctx.getName()),
                    prov.modLoc("block/" + ctx.getName() + "_top")
                )
            );
        })
        .register();

    public static final BlockEntry<MachineInterface<NECraftingCluster>> CRAFTING_INTERFACE = REGISTRATE
        .block("crafting_interface", MachineInterface<NECraftingCluster>::new)
        .simpleItem()
        .blockstate((ctx, prov) -> {
            prov.simpleBlock(
                ctx.get(),
                prov.models().cubeColumn(
                    ctx.getName(),
                    prov.modLoc("block/" + ctx.getName()),
                    prov.modLoc("block/" + ctx.getName() + "_top")
                )
            );
        })
        .register();

    public static final BlockEntry<MachineInterface<NEStorageCluster>> STORAGE_INTERFACE = REGISTRATE
        .block("storage_interface", MachineInterface<NEStorageCluster>::new)
        .simpleItem()
        .defaultBlockstate()
        .register();

    public static final BlockEntry<ECODriveBlock> ECO_DRIVE = REGISTRATE
        .block("eco_drive", ECODriveBlock::new)
        .blockstate((ctx, provider) -> {
            ResourceLocation empty = provider.modLoc("block/eco_drive_empty");
            ResourceLocation full = provider.modLoc("block/eco_drive_full");
            provider.getVariantBuilder(ctx.get())
                .forAllStatesExcept(state -> {
                    boolean isFull = state.getValue(ECODriveBlock.HAS_CELL);
                    return ConfiguredModel.builder()
                        .modelFile(provider.models().getExistingFile(isFull ? full : empty))
                        .rotationY(((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                        .build();
                }, ECODriveBlock.FORMED);
        })
        .item()
        .model((ctx, provider) -> {
            provider.withExistingParent(ctx.getName(), provider.modLoc("block/eco_drive_empty"));
        })
        .build()
        .lang("ECO - Crystal Oscillator Drive")
        .register();

    public static void register() {

    }
}
