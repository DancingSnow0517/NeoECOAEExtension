package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.ECOStorageSystem;
import cn.dancingsnow.neoecoae.blocks.MachineCasing;
import cn.dancingsnow.neoecoae.blocks.ECOStorageVent;
import cn.dancingsnow.neoecoae.blocks.MachineInterface;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import org.apache.commons.io.function.Uncheck;

import java.util.Locale;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEBlocks {
    public static final BlockEntry<MachineCasing<NEComputationCluster>> COMPUTATION_CASING = REGISTRATE
        .block("computation_casing", MachineCasing<NEComputationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .register();

    public static final BlockEntry<MachineCasing<NECraftingCluster>> CRAFTING_CASING = REGISTRATE
        .block("crafting_casing", MachineCasing<NECraftingCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .register();

    public static final BlockEntry<MachineCasing<NEStorageCluster>> STORAGE_CASING = REGISTRATE
        .block("storage_casing", MachineCasing<NEStorageCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .register();

    public static final BlockEntry<MachineInterface<NEComputationCluster>> COMPUTATION_INTERFACE = REGISTRATE
        .block("computation_interface", MachineInterface<NEComputationCluster>::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
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
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
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
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
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

    public static final BlockEntry<ECODriveBlock> ECO_DRIVE = REGISTRATE
        .block("eco_drive", ECODriveBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
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
        .lang("ECO - Crystal Oscillator Drive")
        .register();

    public static final BlockEntry<ECOStorageVent> STORAGE_VENT = REGISTRATE
        .block("storage_vent", ECOStorageVent::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
        .blockstate((ctx, prov) -> {
            ModelFile modelFile = prov.models()
                .cube(
                    ctx.getName(),
                    prov.modLoc("block/storage_casing"),
                    prov.modLoc("block/storage_casing"),
                    prov.modLoc("block/storage_vent_front"),
                    prov.modLoc("block/storage_vent_back"),
                    prov.modLoc("block/storage_vent_we"),
                    prov.modLoc("block/storage_vent_we")
                ).texture("particle", prov.modLoc("block/storage_vent_front"));
            prov.getVariantBuilder(ctx.get())
                .forAllStatesExcept(s ->
                        ConfiguredModel.builder()
                            .modelFile(modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build(),
                    ECOStorageVent.FORMED
                );
        })
        .simpleItem()
        .lang("Storage System Heat Sink")
        .register();

    public static final BlockEntry<ECOStorageSystem> STORAGE_SYSTEM_L4 = createStorageSystem("l4");
    public static final BlockEntry<ECOStorageSystem> STORAGE_SYSTEM_L6 = createStorageSystem("l6");
    public static final BlockEntry<ECOStorageSystem> STORAGE_SYSTEM_L9 = createStorageSystem("l9");

    private static BlockEntry<ECOStorageSystem> createStorageSystem(String level) {
        return REGISTRATE
            .block("storage_system_" + level, ECOStorageSystem::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, prov) -> {
                ModelFile modelFile = prov.models()
                    .cube(
                        ctx.getName(),
                        prov.modLoc("block/storage_casing"),
                        prov.modLoc("block/storage_casing"),
                        prov.modLoc("block/" + ctx.getName()),
                        prov.modLoc("block/storage_vent_front"),
                        prov.modLoc("block/storage_system_side"),
                        prov.modLoc("block/storage_system_side")
                    ).texture("particle", prov.modLoc("block/" + ctx.getName()));
                ModelFile formedModel = new ModelFile.UncheckedModelFile(prov.modLoc("block" + ctx.getName() + "_formed"));
                prov.getVariantBuilder(ctx.get())
                    .forAllStates(s ->
                        ConfiguredModel.builder()
                            .modelFile(s.getValue(ECOStorageSystem.FORMED) ? formedModel : modelFile)
                            .rotationY(((int) s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build()
                    );
            })
            .simpleItem()
            .lang("ECO - %s Extensible Storage Subsystem Controller".formatted(level.toUpperCase(Locale.ROOT)))
            .register();
    }

    public static void register() {

    }
}
