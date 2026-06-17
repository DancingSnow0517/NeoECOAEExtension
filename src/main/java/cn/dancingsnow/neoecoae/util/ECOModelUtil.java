package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationTransmitter;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingPatternBus;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.client.item.ECOStorageCellStateTintSource;
import cn.dancingsnow.neoecoae.client.model.ECOComputationDriveModel;
import cn.dancingsnow.neoecoae.client.model.ECODriveModel;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.providers.generators.RegistrateItemModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator;

import java.util.Optional;

import static net.minecraft.client.data.models.BlockModelGenerators.ROTATIONS_COLUMN_WITH_FACING;
import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ECOModelUtil {
    public static final ModelTemplate CASING = createTemplate("casing_base", TextureSlot.TEXTURE);

    public static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelGenerator> cellModel(String type, String size) {
        return (ctx, prov) -> {
            var model = ModelTemplates.THREE_LAYERED_ITEM.create(
                ctx.get(),
                TextureMapping.layered(
                    prov.modItemTexture("eco_%s_cell_housing".formatted(type)),
                    prov.modItemTexture("eco_cell_light_" + size),
                    prov.modItemTexture("eco_storage_cell_led")
                ),
                prov.modelOutput
            );
            prov.itemModelOutput.accept(
                ctx.get(),
                ItemModelUtils.tintedModel(
                    model,
                    new Constant(-1),
                    new Constant(-1),
                    ECOStorageCellStateTintSource.INSTANCE
                )
            );
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> casing() {
        return (ctx, prov) -> {
            prov.generateWithTemplate(
                ctx.get(),
                CASING,
                TextureMapping.singleSlot(TextureSlot.TEXTURE, new Material(ctx.getId().withPrefix("block/")))
            );
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> simpleExisting() {
        return (ctx, provider) -> provider.create(ctx.get(), provider.modLoc("block/" + ctx.getName()));
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> horizontalExisting() {
        return (ctx, prov) -> {
            var model = ctx.getId().withPrefix("block/");
            prov.generateHorizontalBlock(ctx.get(), BlockModelGenerators.plainVariant(model));
            prov.registerSimpleItemModel(ctx.get(), model);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> storageEnergyCell(String level) {
        return (ctx, prov) -> {
            var propertyDispatch = PropertyDispatch.initial(ECOEnergyCellBlock.LEVEL).generate(l -> BlockModelGenerators.plainVariant(
                prov.modLoc("block/storage_energy_cell/cell_%s_%d".formatted(level, l))));
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(
                BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(
                ctx.get(),
                prov.modLoc("block/storage_energy_cell/cell_%s_0".formatted(level))
            );
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> quartzCluster() {
        return (ctx, prov) -> {
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ctx.get(),
                plainVariant(
                    ModelTemplates.CROSS.extend().build().create(
                        ctx.get(),
                        TextureMapping.cross(ctx.get()),
                        prov.modelOutput
                    )
                )
            ).with(ROTATIONS_COLUMN_WITH_FACING));
            var model = ModelTemplates.FLAT_ITEM.create(
                ctx.get().asItem(),
                TextureMapping.layer0(ctx.get()),
                prov.modelOutput
            );
            prov.itemModelOutput.accept(ctx.get().asItem(), ItemModelUtils.plainModel(model));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> drive() {
        return (ctx, prov) -> {
            var emptyModel = customBlockStateModel(new ECODriveModel.Unbaked(new Variant(NeoECOAE.id("block/eco_drive_empty"))));
            var fullModel = customBlockStateModel(new ECODriveModel.Unbaked(new Variant(NeoECOAE.id("block/eco_drive_full"))));

            var propertyDispatch = PropertyDispatch.initial(ECODriveBlock.HAS_CELL)
                .select(false, emptyModel)
                .select(true, fullModel);

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).withUnbaked(createDriverFacingDispatch()));
            prov.registerSimpleItemModel(ctx.get(), NeoECOAE.id("block/eco_drive_empty"));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationDrive() {
        return (ctx, prov) -> {
            var emptyModel = customBlockStateModel(new ECOComputationDriveModel.Unbaked(new Variant(NeoECOAE.id("block/computation_drive_empty"))));
            var fullModel = customBlockStateModel(new ECOComputationDriveModel.Unbaked(new Variant(NeoECOAE.id("block/computation_drive_full"))));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationDrive.HAS_CELL)
                .select(false, emptyModel)
                .select(true, fullModel);

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).withUnbaked(createDriverFacingDispatch()));
            prov.registerSimpleItemModel(ctx.get(), NeoECOAE.id("block/computation_drive_empty"));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> integratedWorkingStation() {
        return (ctx, prov) -> {
            var model = ctx.getId().withPrefix("block/");
            var workingModel = ctx.getId().withPrefix("block/").withSuffix("_on");

            var propertyDispatch = PropertyDispatch.initial(ECOIntegratedWorkingStation.WORKING)
                .select(false, BlockModelGenerators.plainVariant(model))
                .select(true, BlockModelGenerators.plainVariant(workingModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), model);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationTransmitter() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/computation_transmitter");
            var formedModel = prov.modLoc("block/computation_transmitter_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOComputationTransmitter.FORMED)
                .select(false, BlockModelGenerators.plainVariant(unformedModel))
                .select(true, BlockModelGenerators.plainVariant(formedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingWorker() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_worker");
            var formedModel = prov.modLoc("block/crafting_worker_formed");
            var workingModel = prov.modLoc("block/crafting_worker_working");

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingWorker.FORMED, ECOCraftingWorker.WORKING).generate((formed, working) -> {
                if (formed) {
                    if (working) {
                        return BlockModelGenerators.plainVariant(workingModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(formedModel);
                }
            });

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingPatternBus() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_pattern_bus");
            var formedModel = prov.modLoc("block/crafting_pattern_bus_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingPatternBus.FORMED)
                .select(true, BlockModelGenerators.plainVariant(formedModel))
                .select(false, BlockModelGenerators.plainVariant(unformedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingVent() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_vent");
            var formedModel = prov.modLoc("block/crafting_vent_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOMachineCasing.FORMED)
                .select(true, BlockModelGenerators.plainVariant(formedModel))
                .select(false, BlockModelGenerators.plainVariant(unformedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingCasing() {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_casing");
            var formedModel = prov.modLoc("block/crafting_casing_formed");

            var propertyDispatch = PropertyDispatch.initial(ECOMachineCasing.FORMED)
                .select(true, BlockModelGenerators.plainVariant(formedModel))
                .select(false, BlockModelGenerators.plainVariant(unformedModel));

            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> storageSystem(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/storage_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/storage_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/storage_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOStorageSystemBlock.FORMED, ECOStorageSystemBlock.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingParallelCore(String level) {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/crafting_core/parallel_core_%s".formatted(level));
            var formedModel = prov.modLoc("block/crafting_core/parallel_core_%s_formed".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationParallelCore.FORMED).generate((formed) -> {
                if (formed) {
                    return BlockModelGenerators.plainVariant(formedModel);
                } else {
                    return BlockModelGenerators.plainVariant(unformedModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> craftingSystem(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/crafting_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/crafting_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/crafting_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOCraftingSystem.FORMED, ECOCraftingSystem.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationSystem(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/computation_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/computation_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/computation_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationSystem.FORMED, ECOComputationSystem.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationParallelCore(String level) {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/computation_core/parallel_core_%s".formatted(level));
            var formedModel = prov.modLoc("block/computation_core/parallel_core_%s_formed".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationParallelCore.FORMED).generate((formed) -> {
                if (formed) {
                    return BlockModelGenerators.plainVariant(formedModel);
                } else {
                    return BlockModelGenerators.plainVariant(unformedModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationThreadingCore(String level) {
        return (ctx, prov) -> {
            var unformedModel = prov.modLoc("block/computation_core/threading_core_%s".formatted(level));
            var formedModel = prov.modLoc("block/computation_core/threading_core_%s_formed".formatted(level));
            var workingModel = prov.modLoc("block/computation_core/threading_core_%s_working".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationThreadingCore.FORMED, ECOComputationThreadingCore.WORKING).generate((formed, working) -> {
                if (formed) {
                    if (working) {
                        return BlockModelGenerators.plainVariant(workingModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(unformedModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), unformedModel);
        };
    }

    public static <T extends Block> NonNullBiConsumer<DataGenContext<Block, T>, RegistrateBlockModelGenerator> computationCoolingController(String level) {
        return (ctx, prov) -> {
            var offModel = prov.modLoc("block/computation_cooling_controller/controller_%s_off".formatted(level));
            var formedModel = prov.modLoc("block/computation_cooling_controller/controller_%s_formed".formatted(level));
            var formedMirroredModel = prov.modLoc("block/computation_cooling_controller/controller_%s_formed_mirrored".formatted(level));

            var propertyDispatch = PropertyDispatch.initial(ECOComputationCoolingController.FORMED, ECOComputationCoolingController.MIRRORED).generate((formed, mirrored) -> {
                if (formed) {
                    if (mirrored) {
                        return BlockModelGenerators.plainVariant(formedMirroredModel);
                    } else {
                        return BlockModelGenerators.plainVariant(formedModel);
                    }
                } else {
                    return BlockModelGenerators.plainVariant(offModel);
                }
            });
            prov.blockStateOutput.accept(MultiVariantGenerator.dispatch(ctx.get()).with(propertyDispatch).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            prov.registerSimpleItemModel(ctx.get(), offModel);
        };
    }

    private static ModelTemplate createTemplate(String id, TextureSlot... slots) {
        return new ModelTemplate(Optional.of(NeoECOAE.id(id).withPrefix("block/")), Optional.empty(), slots);
    }

    private static MultiVariant customBlockStateModel(CustomUnbakedBlockStateModel model) {
        return MultiVariant.of(new CustomBlockStateModelBuilder.Simple(model));
    }

    private static PropertyDispatch<UnbakedMutator> createDriverFacingDispatch() {
        return PropertyDispatch.modifyUnbaked(BlockStateProperties.HORIZONTAL_FACING).generate(facing -> {
            VariantMutator mutator = switch (facing) {
                case EAST -> BlockModelGenerators.Y_ROT_90;
                case SOUTH -> BlockModelGenerators.Y_ROT_180;
                case WEST -> BlockModelGenerators.Y_ROT_270;
                default -> BlockModelGenerators.NOP;
            };
            return UnbakedMutator.builder()
                .add(ECODriveModel.Unbaked.class, unbaked -> new ECODriveModel.Unbaked(unbaked.variant().with(mutator)))
                .add(ECOComputationDriveModel.Unbaked.class, unbaked -> new ECOComputationDriveModel.Unbaked(unbaked.variant().with(mutator)))
                .build();
        });
    }
}
